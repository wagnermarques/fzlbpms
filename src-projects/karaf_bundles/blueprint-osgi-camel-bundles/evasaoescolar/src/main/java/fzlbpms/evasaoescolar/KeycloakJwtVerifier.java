package fzlbpms.evasaoescolar;

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
		this(Env.get("EVASAO_KEYCLOAK_URL",
				"http://" + Env.get("KC_HOST", "fzl-keycloak") + ":8080" + Env.get("KC_HTTP_RELATIVE_PATH", "/auth")),
				Env.get("FZL_REALM", "fzlbpms"));
	}

	KeycloakJwtVerifier(String keycloakBaseUrl, String realm) {
		this.jwksUrl = keycloakBaseUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
		this.issuerSuffix = "/realms/" + realm;
	}

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
			if (exp != null && now > exp.longValue() + CLOCK_SKEW_SECONDS) return null;

			Number nbf = (Number) claims.get("nbf");
			if (nbf != null && now < nbf.longValue() - CLOCK_SKEW_SECONDS) return null;

			String iss = (String) claims.get("iss");
			if (iss == null || !iss.endsWith(issuerSuffix)) return null;

			return claims;
		} catch (Exception e) {
			LOG.debug("Falha na validação do token JWT: {}", e.getMessage());
			return null;
		}
	}

	private PublicKey keyFor(String kid) {
		PublicKey cached = keysByKid.get(kid);
		if (cached != null) return cached;
		refreshKeys();
		return keysByKid.get(kid);
	}

	private synchronized void refreshKeys() {
		long now = System.currentTimeMillis();
		if (now - lastRefresh < MIN_REFRESH_INTERVAL_MS) return;
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(jwksUrl))
					.timeout(Duration.ofSeconds(5))
					.header("Accept", "application/json")
					.GET()
					.build();
			HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (res.statusCode() != 200) {
				LOG.warn("JWKS retornou HTTP {}: {}", res.statusCode(), jwksUrl);
				return;
			}
			Map<String, Object> jwks = Json.parseObject(res.body());
			List<?> keys = (List<?>) jwks.get("keys");
			if (keys == null) return;

			KeyFactory factory = KeyFactory.getInstance("RSA");
			for (Object entry : keys) {
				if (!(entry instanceof Map)) continue;
				Map<?, ?> k = (Map<?, ?>) entry;
				if (!"RSA".equals(k.get("kty")) || !"sig".equals(k.get("use"))) continue;
				String kid = (String) k.get("kid");
				String n = (String) k.get("n");
				String e = (String) k.get("e");
				if (kid == null || n == null || e == null) continue;

				BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
				BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
				keysByKid.put(kid, factory.generatePublic(new RSAPublicKeySpec(modulus, exponent)));
			}
			lastRefresh = now;
			LOG.info("Carregadas {} chaves públicas do JWKS Keycloak: {}", keysByKid.size(), jwksUrl);
		} catch (Exception e) {
			LOG.warn("Não foi possível carregar JWKS de {}: {}", jwksUrl, e.getMessage());
		}
	}

	private static String b64String(String part) {
		return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
	}
}
