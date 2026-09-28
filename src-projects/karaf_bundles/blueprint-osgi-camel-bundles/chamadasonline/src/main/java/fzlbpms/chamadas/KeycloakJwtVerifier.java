package fzlbpms.chamadas;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates Keycloak access tokens (realm fzlbpms) by RS256 signature against
 * the realm's JWKS, plus exp/nbf and issuer.
 *
 * The JWKS is fetched over the internal network (http://fzl-keycloak:8080/auth/...),
 * while the token's "iss" carries the PUBLIC hostname the browser used
 * (https://fzlbpms.local/auth/realms/fzlbpms, or whatever switch-domain.sh set).
 * The issuer is therefore checked by its realm suffix only, which keeps the
 * bundle domain-agnostic; the signature is what actually proves the origin.
 */
public class KeycloakJwtVerifier {

	private static final Logger LOG = LoggerFactory.getLogger(KeycloakJwtVerifier.class);
	private static final long CLOCK_SKEW_SECONDS = 30;
	private static final long MIN_REFRESH_INTERVAL_MS = 30_000;

	private final String jwksUrl;
	private final String issuerSuffix;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final Map<String, PublicKey> keysByKid = new ConcurrentHashMap<>();
	private volatile long lastRefresh;

	public KeycloakJwtVerifier() {
		this(env("CHAMADAS_KEYCLOAK_URL",
				"http://" + env("KC_HOST", "fzl-keycloak") + ":8080" + env("KC_HTTP_RELATIVE_PATH", "/auth")),
				env("FZL_REALM", "fzlbpms"));
	}

	/** @param keycloakBaseUrl e.g. http://fzl-keycloak:8080/auth */
	KeycloakJwtVerifier(String keycloakBaseUrl, String realm) {
		this.jwksUrl = keycloakBaseUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
		this.issuerSuffix = "/realms/" + realm;
	}

	/**
	 * @param authorization raw Authorization header value
	 * @return the token claims, or null when the token is missing or invalid
	 */
	public Map<String, Object> verify(String authorization) {
		if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
		String token = authorization.substring(7).trim();
		String[] parts = token.split("\\.");
		if (parts.length != 3) return null;

		try {
			Map<String, Object> header = Json.parseObject(b64String(parts[0]));
			if (!"RS256".equals(header.get("alg"))) return null;
			String kid = String.valueOf(header.get("kid"));

			PublicKey key = keyFor(kid);
			if (key == null) return null;

			Signature sig = Signature.getInstance("SHA256withRSA");
			sig.initVerify(key);
			sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
			if (!sig.verify(Base64.getUrlDecoder().decode(parts[2]))) return null;

			Map<String, Object> claims = Json.parseObject(b64String(parts[1]));
			long now = System.currentTimeMillis() / 1000;
			Number exp = (Number) claims.get("exp");
			if (exp == null || now > exp.longValue() + CLOCK_SKEW_SECONDS) return null;
			Number nbf = (Number) claims.get("nbf");
			if (nbf != null && now + CLOCK_SKEW_SECONDS < nbf.longValue()) return null;
			Object iss = claims.get("iss");
			if (!(iss instanceof String) || !((String) iss).endsWith(issuerSuffix)) return null;
			Object sub = claims.get("sub");
			if (!(sub instanceof String) || ((String) sub).isBlank()) return null;
			return claims;
		} catch (Exception e) {
			LOG.debug("JWT rejected: {}", e.toString());
			return null;
		}
	}

	private PublicKey keyFor(String kid) {
		PublicKey key = keysByKid.get(kid);
		if (key != null) return key;
		// Unknown kid: Keycloak may have rotated keys. Refresh, but not on every
		// request carrying a garbage kid.
		long now = System.currentTimeMillis();
		if (now - lastRefresh < MIN_REFRESH_INTERVAL_MS) return null;
		synchronized (this) {
			if (now - lastRefresh >= MIN_REFRESH_INTERVAL_MS) {
				lastRefresh = now;
				refreshKeys();
			}
		}
		return keysByKid.get(kid);
	}

	@SuppressWarnings("unchecked")
	private void refreshKeys() {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(jwksUrl)).timeout(Duration.ofSeconds(10)).GET().build();
			HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 200) {
				LOG.warn("JWKS fetch {} answered HTTP {}", jwksUrl, resp.statusCode());
				return;
			}
			Object keys = Json.parseObject(resp.body()).get("keys");
			if (!(keys instanceof List)) return;
			KeyFactory kf = KeyFactory.getInstance("RSA");
			for (Object o : (List<Object>) keys) {
				Map<String, Object> jwk = (Map<String, Object>) o;
				if (!"RSA".equals(jwk.get("kty")) || "enc".equals(jwk.get("use"))) continue;
				BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode((String) jwk.get("n")));
				BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode((String) jwk.get("e")));
				keysByKid.put(String.valueOf(jwk.get("kid")), kf.generatePublic(new RSAPublicKeySpec(n, e)));
			}
			LOG.info("Loaded {} signing key(s) from {}", keysByKid.size(), jwksUrl);
		} catch (Exception e) {
			LOG.warn("JWKS fetch {} failed: {}", jwksUrl, e.toString());
		}
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return (value == null || value.isBlank()) ? fallback : value;
	}

	private static String b64String(String part) {
		return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
	}
}
