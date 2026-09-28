package fzlbpms.tasktoday;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Web Push message encryption (RFC 8291, "aes128gcm" content coding, RFC 8188)
 * and VAPID authorization (RFC 8292), on the JDK's own crypto providers only.
 *
 * Deliberately not nl.martijndwars:web-push: it drags BouncyCastle, jose4j and
 * Apache async HTTP into Karaf, each needing its own OSGi wiring, for what is
 * P-256 ECDH + HKDF-SHA256 + AES-128-GCM + ES256 — all in the JDK since 11.
 */
public final class WebPush {

	/** RFC 8188 record size; a single record carries the whole payload. */
	static final int RECORD_SIZE = 4096;
	/**
	 * Push services must accept 4096-byte bodies (RFC 8291 §4): minus the
	 * 86-byte header (salt 16, rs 4, idlen 1, key 65), the 16-byte GCM tag and
	 * the 1-byte padding delimiter.
	 */
	static final int MAX_PAYLOAD = 3993;

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final ECParameterSpec P256;

	static {
		try {
			AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
			params.init(new ECGenParameterSpec("secp256r1"));
			P256 = params.getParameterSpec(ECParameterSpec.class);
		} catch (GeneralSecurityException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private WebPush() {
	}

	// ---------------------------------------------------------------- keys

	static KeyPair generateKeyPair() throws GeneralSecurityException {
		KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
		gen.initialize(P256, RANDOM);
		return gen.generateKeyPair();
	}

	/** Uncompressed point 0x04 || X || Y (65 bytes), the form browsers use. */
	static byte[] encodePublic(ECPublicKey key) {
		byte[] out = new byte[65];
		out[0] = 0x04;
		System.arraycopy(fixed32(key.getW().getAffineX()), 0, out, 1, 32);
		System.arraycopy(fixed32(key.getW().getAffineY()), 0, out, 33, 32);
		return out;
	}

	static ECPublicKey decodePublic(byte[] raw) throws GeneralSecurityException {
		if (raw.length != 65 || raw[0] != 0x04) {
			throw new GeneralSecurityException("P-256 public key must be a 65-byte uncompressed point");
		}
		BigInteger x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 33));
		BigInteger y = new BigInteger(1, Arrays.copyOfRange(raw, 33, 65));
		return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
	}

	static byte[] encodePrivate(ECPrivateKey key) {
		return fixed32(key.getS());
	}

	static ECPrivateKey decodePrivate(byte[] raw) throws GeneralSecurityException {
		if (raw.length != 32) throw new GeneralSecurityException("P-256 private key must be 32 bytes");
		return (ECPrivateKey) KeyFactory.getInstance("EC")
				.generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), P256));
	}

	// ---------------------------------------------------------- encryption

	/**
	 * @param plaintext  message (at most MAX_PAYLOAD bytes)
	 * @param uaPublic   subscription keys.p256dh, decoded (65 bytes)
	 * @param authSecret subscription keys.auth, decoded (16 bytes)
	 * @return the complete aes128gcm request body (header + one record)
	 */
	static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
		if (plaintext.length > MAX_PAYLOAD) throw new GeneralSecurityException("push payload too large");
		KeyPair as = generateKeyPair();
		byte[] salt = new byte[16];
		RANDOM.nextBytes(salt);
		return encrypt(plaintext, uaPublic, authSecret, as, salt);
	}

	/** Deterministic core, separated so tests can pin the ephemeral key and salt. */
	static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair as, byte[] salt)
			throws GeneralSecurityException {
		byte[] asPublic = encodePublic((ECPublicKey) as.getPublic());

		KeyAgreement ka = KeyAgreement.getInstance("ECDH");
		ka.init(as.getPrivate());
		ka.doPhase(decodePublic(uaPublic), true);
		byte[] ecdhSecret = ka.generateSecret();

		// RFC 8291 §3.3-3.4: IKM from the auth secret and both public keys.
		byte[] prkKey = hmac(authSecret, ecdhSecret);
		byte[] ikm = hmac(prkKey, concat(ascii("WebPush: info\0"), uaPublic, asPublic, new byte[] { 1 }));
		// RFC 8188 §2.2-2.3: content encryption key and nonce from the salt.
		byte[] prk = hmac(salt, ikm);
		byte[] cek = Arrays.copyOf(hmac(prk, concat(ascii("Content-Encoding: aes128gcm\0"), new byte[] { 1 })), 16);
		byte[] nonce = Arrays.copyOf(hmac(prk, concat(ascii("Content-Encoding: nonce\0"), new byte[] { 1 })), 12);

		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
		// 0x02 = padding delimiter of the last (here: only) record.
		byte[] record = cipher.doFinal(concat(plaintext, new byte[] { 2 }));

		ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
		header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
		return concat(header.array(), record);
	}

	// --------------------------------------------------------------- VAPID

	/**
	 * RFC 8292 Authorization header value: "vapid t=<ES256 JWT>, k=<public key>".
	 * aud is the push service origin, exp at most 24h ahead (12h here).
	 */
	static String vapidAuthorization(String endpoint, ECPrivateKey privateKey, byte[] publicKey, String subject)
			throws GeneralSecurityException {
		URI uri = URI.create(endpoint);
		String aud = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());

		Map<String, Object> header = new LinkedHashMap<>();
		header.put("typ", "JWT");
		header.put("alg", "ES256");
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("aud", aud);
		claims.put("exp", System.currentTimeMillis() / 1000 + 12 * 3600);
		claims.put("sub", subject);

		String signingInput = b64(Json.serialize(header).getBytes(StandardCharsets.UTF_8)) + "."
				+ b64(Json.serialize(claims).getBytes(StandardCharsets.UTF_8));
		// P1363 = raw R||S (64 bytes), the JWS encoding; plain SHA256withECDSA is DER.
		Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
		sig.initSign(privateKey);
		sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
		return "vapid t=" + signingInput + "." + b64(sig.sign()) + ", k=" + b64(publicKey);
	}

	// ------------------------------------------------------------- helpers

	static String b64(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** Accepts base64url or standard base64, with or without padding. */
	static byte[] unb64(String s) {
		String normalized = s.trim().replace('+', '-').replace('/', '_').replace("=", "");
		return Base64.getUrlDecoder().decode(normalized);
	}

	static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		return mac.doFinal(data);
	}

	private static byte[] fixed32(BigInteger n) {
		byte[] raw = n.toByteArray();
		if (raw.length == 32) return raw;
		byte[] out = new byte[32];
		if (raw.length > 32) {
			System.arraycopy(raw, raw.length - 32, out, 0, 32); // drop sign byte
		} else {
			System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
		}
		return out;
	}

	private static byte[] ascii(String s) {
		return s.getBytes(StandardCharsets.US_ASCII);
	}

	static byte[] concat(byte[]... parts) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] p : parts) out.writeBytes(p);
		return out.toByteArray();
	}
}
