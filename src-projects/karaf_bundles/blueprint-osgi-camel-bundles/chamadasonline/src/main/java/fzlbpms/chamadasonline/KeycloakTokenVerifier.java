package fzlbpms.chamadasonline;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

/**
 * Validates a Keycloak-issued access token: RS256 signature against the
 * realm's JWKS (fetched from Keycloak's own /certs endpoint and cached),
 * plus expiry. This is a security fix relative to the Node source
 * (routes/auth.ts's POST /auth/keycloak), which only base64-decodes the
 * token payload and never checks the signature at all.
 */
public final class KeycloakTokenVerifier {

	private static final Configuration LENIENT = Configuration.defaultConfiguration()
			.addOptions(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL);
	private static final long JWKS_CACHE_TTL_MILLIS = 10 * 60 * 1000;

	private final String jwksUrl;
	private final String clientId;
	private final HttpClient httpClient = HttpClient.newHttpClient();

	private volatile Map<String, PublicKey> keyCache = Map.of();
	private volatile long keyCacheFetchedAt = 0;

	public KeycloakTokenVerifier(String jwksUrl, String clientId) {
		this.jwksUrl = jwksUrl;
		this.clientId = clientId;
	}

	/** Blueprint factory-method target. Reuses the same KC_HOST/KC_HTTP_RELATIVE_PATH/
	 *  FZL_REALM env vars keycloak-admin-camel-context.xml already runs with in this
	 *  container, rather than inventing chamadasonline-specific duplicates. */
	public static KeycloakTokenVerifier create() {
		String kcHost = env("KC_HOST", "fzl-keycloak");
		String kcPath = env("KC_HTTP_RELATIVE_PATH", "/auth");
		String realm = env("FZL_REALM", "fzlbpms");
		String clientId = env("FZL_CHAMADASONLINE_CLIENT_ID", "fzl-chamadasonline");
		String jwksUrl = "http://" + kcHost + ":8080" + kcPath + "/realms/" + realm
				+ "/protocol/openid-connect/certs";
		return new KeycloakTokenVerifier(jwksUrl, clientId);
	}

	/** Throws UnauthorizedException("invalid_keycloak_token") on any failure — bad
	 *  shape, unknown kid, bad signature, or expiry — matching the Node source's
	 *  single catch-all error code for this endpoint. */
	public Claims verify(String token) {
		String[] parts = token.split("\\.");
		if (parts.length != 3) {
			throw new UnauthorizedException("invalid_keycloak_token");
		}

		DocumentContext header = parseJsonOrThrow(decode(parts[0]));
		DocumentContext payload = parseJsonOrThrow(decode(parts[1]));

		String kid = header.read("$.kid");
		if (kid == null) {
			throw new UnauthorizedException("invalid_keycloak_token");
		}

		PublicKey publicKey = resolveKey(kid);
		if (publicKey == null || !verifySignature(parts[0] + "." + parts[1], parts[2], publicKey)) {
			throw new UnauthorizedException("invalid_keycloak_token");
		}

		Number exp = payload.read("$.exp");
		if (exp == null || Instant.now().getEpochSecond() > exp.longValue()) {
			throw new UnauthorizedException("invalid_keycloak_token");
		}

		return Claims.from(payload, clientId);
	}

	private PublicKey resolveKey(String kid) {
		Map<String, PublicKey> cache = keyCache;
		long now = System.currentTimeMillis();
		if (!cache.containsKey(kid) || now - keyCacheFetchedAt > JWKS_CACHE_TTL_MILLIS) {
			cache = fetchJwks();
			keyCache = cache;
			keyCacheFetchedAt = now;
		}
		return cache.get(kid);
	}

	private Map<String, PublicKey> fetchJwks() {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(jwksUrl)).GET().build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				throw new UnauthorizedException("invalid_keycloak_token");
			}
			DocumentContext jwks = parseJsonOrThrow(response.body());
			List<Map<String, Object>> keys = jwks.read("$.keys");
			Map<String, PublicKey> result = new HashMap<>();
			if (keys != null) {
				for (Map<String, Object> key : keys) {
					if (!"RSA".equals(key.get("kty"))) continue;
					String kid = (String) key.get("kid");
					String n = (String) key.get("n");
					String e = (String) key.get("e");
					if (kid == null || n == null || e == null) continue;
					result.put(kid, buildRsaPublicKey(n, e));
				}
			}
			return result;
		} catch (IOException ex) {
			throw new UnauthorizedException("invalid_keycloak_token");
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new UnauthorizedException("invalid_keycloak_token");
		}
	}

	private static PublicKey buildRsaPublicKey(String n, String e) {
		try {
			BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
			BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
			return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
		} catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static boolean verifySignature(String signingInput, String signaturePart, PublicKey publicKey) {
		try {
			byte[] signatureBytes = Base64.getUrlDecoder().decode(signaturePart);
			Signature sig = Signature.getInstance("SHA256withRSA");
			sig.initVerify(publicKey);
			sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
			return sig.verify(signatureBytes);
		} catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | IllegalArgumentException ex) {
			return false;
		}
	}

	private static String decode(String base64UrlPart) {
		try {
			return new String(Base64.getUrlDecoder().decode(base64UrlPart), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException ex) {
			throw new UnauthorizedException("invalid_keycloak_token");
		}
	}

	private static DocumentContext parseJsonOrThrow(String json) {
		try {
			return JsonPath.using(LENIENT).parse(json);
		} catch (RuntimeException ex) {
			throw new UnauthorizedException("invalid_keycloak_token");
		}
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return (value == null || value.isBlank()) ? fallback : value;
	}

	public static final class Claims {
		public final String sub;
		public final String preferredUsername;
		public final String name;
		public final String email;
		public final List<String> allRoles;

		private Claims(String sub, String preferredUsername, String name, String email, List<String> allRoles) {
			this.sub = sub;
			this.preferredUsername = preferredUsername;
			this.name = name;
			this.email = email;
			this.allRoles = allRoles;
		}

		private static Claims from(DocumentContext payload, String clientId) {
			String sub = payload.read("$.sub");
			String preferredUsername = payload.read("$.preferred_username");
			String name = payload.read("$.name");
			String email = payload.read("$.email");

			Set<String> combined = new LinkedHashSet<>();
			combined.addAll(readRoles(payload, "$.realm_access.roles"));
			combined.addAll(readRoles(payload, "$.resource_access['" + clientId + "'].roles"));

			return new Claims(sub, preferredUsername, name, email, new ArrayList<>(combined));
		}

		private static List<String> readRoles(DocumentContext ctx, String path) {
			List<String> roles = ctx.read(path);
			return roles == null ? List.of() : roles;
		}
	}
}
