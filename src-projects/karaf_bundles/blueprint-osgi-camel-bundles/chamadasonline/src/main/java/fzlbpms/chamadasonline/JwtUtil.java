package fzlbpms.chamadasonline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs/verifies the app's own Bearer tokens (sub + role claims, 12h
 * expiry) — a direct port of apps/api/src/plugins/auth.ts's @fastify/jwt
 * usage. Hand-rolled HMAC-SHA256 rather than pulling in a JWT library: the
 * claim shape is fixed and tiny, and this keeps the bundle free of a
 * dependency that isn't a clean OSGi bundle.
 */
public final class JwtUtil {

	private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
	private static final String HEADER_B64 = base64Url(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
	private static final long EXPIRY_SECONDS = 12 * 60 * 60; // 12h, matching sign: { expiresIn: "12h" }

	private static final Pattern SUB_PATTERN = Pattern.compile("\"sub\":\"([^\"]*)\"");
	private static final Pattern ROLE_PATTERN = Pattern.compile("\"role\":\"([^\"]*)\"");
	private static final Pattern EXP_PATTERN = Pattern.compile("\"exp\":(\\d+)");

	private final byte[] secretBytes;

	public JwtUtil(String secret) {
		this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
	}

	/** Blueprint factory-method target — reads CHAMADASONLINE_JWT_SECRET, same
	 *  env-var-driven wiring as ChamadasOnlineDataSourceFactory. */
	public static JwtUtil create() {
		String secret = System.getenv("CHAMADASONLINE_JWT_SECRET");
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("Missing required env var: CHAMADASONLINE_JWT_SECRET");
		}
		return new JwtUtil(secret);
	}

	public String sign(String sub, String role) {
		long exp = Instant.now().getEpochSecond() + EXPIRY_SECONDS;
		String payloadJson = "{\"sub\":\"" + Json.escape(sub) + "\",\"role\":\"" + Json.escape(role) + "\",\"exp\":"
				+ exp + "}";
		String payloadB64 = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
		String signingInput = HEADER_B64 + "." + payloadB64;
		String signature = base64Url(hmacSha256(signingInput));
		return signingInput + "." + signature;
	}

	/** Returns the verified payload, or null if the token is malformed, mis-signed, or expired. */
	public Payload verify(String token) {
		if (token == null) return null;
		String[] parts = token.split("\\.");
		if (parts.length != 3) return null;

		String signingInput = parts[0] + "." + parts[1];
		byte[] expectedSignature = hmacSha256(signingInput);
		byte[] providedSignature;
		try {
			providedSignature = Base64.getUrlDecoder().decode(parts[2]);
		} catch (IllegalArgumentException e) {
			return null;
		}
		if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
			return null;
		}

		String payloadJson;
		try {
			payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return null;
		}

		Matcher subMatcher = SUB_PATTERN.matcher(payloadJson);
		Matcher roleMatcher = ROLE_PATTERN.matcher(payloadJson);
		Matcher expMatcher = EXP_PATTERN.matcher(payloadJson);
		if (!subMatcher.find() || !roleMatcher.find() || !expMatcher.find()) {
			return null;
		}

		long exp = Long.parseLong(expMatcher.group(1));
		if (Instant.now().getEpochSecond() > exp) {
			return null;
		}

		return new Payload(subMatcher.group(1), roleMatcher.group(1));
	}

	private byte[] hmacSha256(String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
			return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String base64Url(byte[] data) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
	}

	public static final class Payload {
		public final String sub;
		public final String role;

		Payload(String sub, String role) {
			this.sub = sub;
			this.role = role;
		}
	}
}
