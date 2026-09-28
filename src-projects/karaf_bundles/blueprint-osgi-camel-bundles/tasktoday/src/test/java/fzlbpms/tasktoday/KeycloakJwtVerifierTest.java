package fzlbpms.tasktoday;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.sun.net.httpserver.HttpServer;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Serves a JWKS like Keycloak's /realms/fzlbpms/protocol/openid-connect/certs. */
public class KeycloakJwtVerifierTest {

	private static final String ISS = "https://fzlbpms.local/auth/realms/fzlbpms";

	private HttpServer server;
	private KeyPair realmKey;
	private KeycloakJwtVerifier verifier;

	@Before
	public void startJwks() throws Exception {
		KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
		gen.initialize(2048);
		realmKey = gen.generateKeyPair();
		RSAPublicKey pub = (RSAPublicKey) realmKey.getPublic();

		Map<String, Object> sigKey = new LinkedHashMap<>();
		sigKey.put("kid", "k1");
		sigKey.put("kty", "RSA");
		sigKey.put("alg", "RS256");
		sigKey.put("use", "sig");
		sigKey.put("n", WebPush.b64(unsigned(pub.getModulus())));
		sigKey.put("e", WebPush.b64(unsigned(pub.getPublicExponent())));
		byte[] jwks = Json.serialize(Map.of("keys", List.of(sigKey))).getBytes(StandardCharsets.UTF_8);

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/auth/realms/fzlbpms/protocol/openid-connect/certs", ex -> {
			ex.sendResponseHeaders(200, jwks.length);
			ex.getResponseBody().write(jwks);
			ex.close();
		});
		server.start();
		verifier = new KeycloakJwtVerifier("http://127.0.0.1:" + server.getAddress().getPort() + "/auth", "fzlbpms");
	}

	@After
	public void stop() {
		server.stop(0);
	}

	@Test
	public void acceptsValidRealmToken() throws Exception {
		Map<String, Object> claims = verifier.verify("Bearer " + token("k1", ISS, 300, realmKey));
		assertNotNull(claims);
		assertEquals("user-123", claims.get("sub"));
	}

	@Test
	public void rejectsForgedSignature() throws Exception {
		KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
		gen.initialize(2048);
		assertNull(verifier.verify("Bearer " + token("k1", ISS, 300, gen.generateKeyPair())));
	}

	@Test
	public void rejectsTamperedPayload() throws Exception {
		String[] parts = token("k1", ISS, 300, realmKey).split("\\.");
		String evil = WebPush.b64(("{\"sub\":\"someone-else\",\"iss\":\"" + ISS + "\",\"exp\":"
				+ (System.currentTimeMillis() / 1000 + 300) + "}").getBytes(StandardCharsets.UTF_8));
		assertNull(verifier.verify("Bearer " + parts[0] + "." + evil + "." + parts[2]));
	}

	@Test
	public void rejectsExpiredToken() throws Exception {
		assertNull(verifier.verify("Bearer " + token("k1", ISS, -120, realmKey)));
	}

	@Test
	public void rejectsOtherRealm() throws Exception {
		assertNull(verifier.verify("Bearer " + token("k1", "https://fzlbpms.local/auth/realms/master", 300, realmKey)));
	}

	@Test
	public void rejectsUnsignedAndMissing() throws Exception {
		String[] parts = token("k1", ISS, 300, realmKey).split("\\.");
		String none = WebPush.b64("{\"alg\":\"none\",\"kid\":\"k1\"}".getBytes(StandardCharsets.UTF_8));
		assertNull(verifier.verify("Bearer " + none + "." + parts[1] + "."));
		assertNull(verifier.verify(null));
		assertNull(verifier.verify("Basic dXNlcjpwYXNz"));
	}

	private static String token(String kid, String iss, long ttlSeconds, KeyPair signer) throws Exception {
		long now = System.currentTimeMillis() / 1000;
		Map<String, Object> header = new LinkedHashMap<>();
		header.put("alg", "RS256");
		header.put("typ", "JWT");
		header.put("kid", kid);
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("sub", "user-123");
		claims.put("iss", iss);
		claims.put("iat", now);
		claims.put("exp", now + ttlSeconds);
		String input = WebPush.b64(Json.serialize(header).getBytes(StandardCharsets.UTF_8)) + "."
				+ WebPush.b64(Json.serialize(claims).getBytes(StandardCharsets.UTF_8));
		Signature sig = Signature.getInstance("SHA256withRSA");
		sig.initSign(signer.getPrivate());
		sig.update(input.getBytes(StandardCharsets.US_ASCII));
		return input + "." + WebPush.b64(sig.sign());
	}

	private static byte[] unsigned(BigInteger n) {
		byte[] b = n.toByteArray();
		return b[0] == 0 ? java.util.Arrays.copyOfRange(b, 1, b.length) : b;
	}
}
