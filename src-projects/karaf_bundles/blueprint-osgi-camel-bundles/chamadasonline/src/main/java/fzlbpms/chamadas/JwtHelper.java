package fzlbpms.chamadas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Two kinds of bearer token reach this bundle, told apart by the header "alg":
 *
 *   HS256 — our own session token (generateSessionToken), signed with
 *           CHAMADAS_JWT_SECRET. There is deliberately no default secret: a
 *           key committed to a public repository is a key anyone can sign with.
 *   RS256 — a Keycloak access token of realm fzlbpms, verified against the
 *           realm JWKS by KeycloakJwtVerifier (signature, exp, issuer).
 *
 * Anything else (alg "none", unsigned or tampered tokens) is rejected.
 */
public final class JwtHelper {

	private static final String HMAC_ALGO = "HmacSHA256";
	private static final int MIN_SECRET_LENGTH = 32;
	private static final KeycloakJwtVerifier KEYCLOAK = new KeycloakJwtVerifier();

	private JwtHelper() {
	}

	public static String getSecret() {
		String sec = System.getenv("CHAMADAS_JWT_SECRET");
		if (sec == null || sec.length() < MIN_SECRET_LENGTH) {
			throw new IllegalStateException("CHAMADAS_JWT_SECRET must be set (at least "
					+ MIN_SECRET_LENGTH + " characters) — see .env.template");
		}
		return sec;
	}

	/** Claims of a Keycloak access token (raw or "Bearer ..."), or null if not genuine. */
	public static Map<String, Object> verifyKeycloakToken(String token) {
		String[] parts = parts(token);
		if (parts == null || !"RS256".equals(decodePart(parts[0]).get("alg"))) return null;
		return KEYCLOAK.verify("Bearer " + String.join(".", parts));
	}

	public static String generateSessionToken(String userId, String name, String role, String regNum, long ttlSeconds) {
		Map<String, Object> header = new LinkedHashMap<>();
		header.put("alg", "HS256");
		header.put("typ", "JWT");

		long now = System.currentTimeMillis() / 1000;
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sub", userId);
		payload.put("name", name);
		payload.put("role", role);
		if (regNum != null) payload.put("registrationNumber", regNum);
		payload.put("iat", now);
		payload.put("exp", now + ttlSeconds);

		String h64 = Base64.getUrlEncoder().withoutPadding().encodeToString(Json.serialize(header).getBytes(StandardCharsets.UTF_8));
		String p64 = Base64.getUrlEncoder().withoutPadding().encodeToString(Json.serialize(payload).getBytes(StandardCharsets.UTF_8));

		String signature = sign(h64 + "." + p64, getSecret());
		return h64 + "." + p64 + "." + signature;
	}

	/**
	 * @param token raw token or "Bearer ..." header value
	 * @return claims of a genuine, unexpired session token or Keycloak access token; else null
	 */
	public static Map<String, Object> validateToken(String token) {
		String[] parts = parts(token);
		if (parts == null) return null;

		Object alg = decodePart(parts[0]).get("alg");
		if ("RS256".equals(alg)) {
			return KEYCLOAK.verify("Bearer " + String.join(".", parts));
		}
		if (!"HS256".equals(alg)) return null;

		String computedSig = sign(parts[0] + "." + parts[1], getSecret());
		if (!MessageDigest.isEqual(computedSig.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
			return null;
		}
		Map<String, Object> payload = decodePart(parts[1]);
		Object exp = payload.get("exp");
		if (!(exp instanceof Number) || System.currentTimeMillis() / 1000 > ((Number) exp).longValue()) {
			return null; // expired, or no expiry at all
		}
		return payload;
	}

	/** header, payload, signature of a compact JWS; null if malformed. */
	private static String[] parts(String token) {
		if (token == null || token.isBlank()) return null;
		if (token.startsWith("Bearer ")) {
			token = token.substring(7).trim();
		}
		String[] parts = token.split("\\.", -1);
		return parts.length == 3 && !parts[2].isEmpty() ? parts : null;
	}

	private static Map<String, Object> decodePart(String part) {
		try {
			return Json.parseObject(new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8));
		} catch (Exception e) {
			return Collections.emptyMap();
		}
	}

	private static String sign(String data, String secret) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGO);
			SecretKeySpec spec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
			mac.init(spec);
			byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		} catch (Exception e) {
			throw new RuntimeException("Failed to calculate HMAC signature", e);
		}
	}
}
