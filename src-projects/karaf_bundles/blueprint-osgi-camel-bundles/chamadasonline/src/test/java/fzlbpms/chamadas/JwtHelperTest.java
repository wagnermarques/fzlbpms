package fzlbpms.chamadas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.Test;

public class JwtHelperTest {

	private static String b64(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

	private static long now() {
		return System.currentTimeMillis() / 1000;
	}

	@Test
	public void sessionTokenRoundTrips() {
		String token = JwtHelper.generateSessionToken("u1", "Ana", "STAFF", "123", 60);
		Map<String, Object> claims = JwtHelper.validateToken("Bearer " + token);
		assertNotNull(claims);
		assertEquals("u1", claims.get("sub"));
		assertEquals("STAFF", claims.get("role"));
	}

	@Test
	public void tamperedSessionTokenIsRejected() {
		String[] p = JwtHelper.generateSessionToken("u1", "Ana", "STUDENT", null, 60).split("\\.");
		String elevated = b64("{\"sub\":\"u1\",\"role\":\"STAFF\",\"exp\":" + (now() + 60) + "}");
		assertNull(JwtHelper.validateToken(p[0] + "." + elevated + "." + p[2]));
	}

	@Test
	public void expiredSessionTokenIsRejected() {
		assertNull(JwtHelper.validateToken(JwtHelper.generateSessionToken("u1", "Ana", "STAFF", null, -5)));
	}

	/** The old bypass: any unsigned token that merely looked like Keycloak's was accepted. */
	@Test
	public void unsignedKeycloakLookalikeIsRejected() {
		String forged = b64("{\"alg\":\"none\"}") + "."
				+ b64("{\"sub\":\"x\",\"iss\":\"https://fzlbpms.local/auth/realms/fzlbpms\",\"exp\":" + (now() + 60)
						+ ",\"realm_access\":{\"roles\":[\"admin\"]}}") + ".";
		assertNull(JwtHelper.validateToken(forged));
		assertNull(JwtHelper.verifyKeycloakToken(forged));
	}

	@Test
	public void rs256WithBogusSignatureIsRejected() {
		String forged = b64("{\"alg\":\"RS256\",\"kid\":\"k1\"}") + "."
				+ b64("{\"sub\":\"x\",\"iss\":\"https://fzlbpms.local/auth/realms/fzlbpms\",\"exp\":" + (now() + 60)
						+ ",\"realm_access\":{\"roles\":[\"admin\"]}}") + ".c2lnbmF0dXJl";
		assertNull(JwtHelper.validateToken(forged));
		assertNull(JwtHelper.verifyKeycloakToken(forged));
	}

	@Test
	public void sessionTokenIsNotAcceptedAsKeycloakToken() {
		assertNull(JwtHelper.verifyKeycloakToken(JwtHelper.generateSessionToken("u1", "Ana", "STAFF", null, 60)));
	}
}
