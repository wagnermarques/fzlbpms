package fzlbpms.tasktoday;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;

public class WebPushTest {

	/** RFC 8291 Appendix A — the worked example, byte for byte. */
	@Test
	public void matchesRfc8291Example() throws Exception {
		byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.US_ASCII);
		ECPrivateKey asPriv = WebPush.decodePrivate(WebPush.unb64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"));
		ECPublicKey asPub = WebPush.decodePublic(WebPush.unb64(
				"BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"));
		byte[] uaPub = WebPush.unb64(
				"BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
		byte[] auth = WebPush.unb64("BTBZMqHH6r4Tts7J_aSIgg");
		byte[] salt = WebPush.unb64("DGv6ra1nlYgDCS1FRnbzlw");

		byte[] body = WebPush.encrypt(plaintext, uaPub, auth, new KeyPair(asPub, asPriv), salt);

		assertEquals("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8"
				+ "wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
				WebPush.b64(body));
	}

	/** Random keys: a receiver implementing RFC 8291 recovers the plaintext. */
	@Test
	public void browserSideDecryptRecoversPayload() throws Exception {
		KeyPair ua = WebPush.generateKeyPair();
		byte[] uaPub = WebPush.encodePublic((ECPublicKey) ua.getPublic());
		byte[] auth = new byte[16];
		new java.security.SecureRandom().nextBytes(auth);
		byte[] plaintext = "{\"title\":\"⏰ Prova\",\"body\":\"Prazo: 28/09 18:00\"}".getBytes(StandardCharsets.UTF_8);

		byte[] body = WebPush.encrypt(plaintext, uaPub, auth);

		assertArrayEquals(plaintext, decrypt(body, ua.getPrivate(), uaPub, auth));
	}

	@Test
	public void keysRoundTripThroughRawEncoding() throws Exception {
		KeyPair kp = WebPush.generateKeyPair();
		byte[] pub = WebPush.encodePublic((ECPublicKey) kp.getPublic());
		byte[] priv = WebPush.encodePrivate((ECPrivateKey) kp.getPrivate());
		assertEquals(65, pub.length);
		assertEquals(32, priv.length);
		assertEquals(kp.getPublic(), WebPush.decodePublic(pub));
		assertEquals(((ECPrivateKey) kp.getPrivate()).getS(), WebPush.decodePrivate(priv).getS());
	}

	@Test
	public void vapidHeaderCarriesVerifiableEs256Jwt() throws Exception {
		KeyPair kp = WebPush.generateKeyPair();
		byte[] pub = WebPush.encodePublic((ECPublicKey) kp.getPublic());

		String header = WebPush.vapidAuthorization("https://fcm.googleapis.com/fcm/send/abc:def",
				(ECPrivateKey) kp.getPrivate(), pub, "mailto:admin@fzlbpms.local");

		assertTrue(header.startsWith("vapid t="));
		String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
		assertEquals(WebPush.b64(pub), header.substring(header.indexOf(", k=") + 4));

		String[] parts = jwt.split("\\.");
		Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
		sig.initVerify(kp.getPublic());
		sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
		assertTrue(sig.verify(Base64.getUrlDecoder().decode(parts[2])));

		Map<String, Object> claims = Json.parseObject(
				new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
		assertEquals("https://fcm.googleapis.com", claims.get("aud"));
		assertEquals("mailto:admin@fzlbpms.local", claims.get("sub"));
	}

	/** Receiver side of RFC 8291, written independently of WebPush.encrypt. */
	private static byte[] decrypt(byte[] body, PrivateKey uaPriv, byte[] uaPub, byte[] auth) throws Exception {
		ByteBuffer buf = ByteBuffer.wrap(body);
		byte[] salt = new byte[16];
		buf.get(salt);
		assertEquals(WebPush.RECORD_SIZE, buf.getInt());
		byte[] asPub = new byte[buf.get() & 0xff];
		buf.get(asPub);
		byte[] record = new byte[buf.remaining()];
		buf.get(record);

		KeyAgreement ka = KeyAgreement.getInstance("ECDH");
		ka.init(uaPriv);
		ka.doPhase(WebPush.decodePublic(asPub), true);
		byte[] ecdh = ka.generateSecret();

		byte[] prkKey = WebPush.hmac(auth, ecdh);
		byte[] ikm = WebPush.hmac(prkKey, WebPush.concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII),
				uaPub, asPub, new byte[] { 1 }));
		byte[] prk = WebPush.hmac(salt, ikm);
		byte[] cek = Arrays.copyOf(WebPush.hmac(prk,
				WebPush.concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] { 1 })), 16);
		byte[] nonce = Arrays.copyOf(WebPush.hmac(prk,
				WebPush.concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] { 1 })), 12);

		Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
		c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
		byte[] padded = c.doFinal(record);
		assertEquals(2, padded[padded.length - 1]);
		return Arrays.copyOf(padded, padded.length - 1);
	}
}
