package fzlbpms.tasktoday;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.camel.Exchange;

/** /push endpoints. */
public class PushProcessor {

	private Db db;
	private KeycloakJwtVerifier verifier;
	private PushService pushService;

	public void setDb(Db db) {
		this.db = db;
	}

	public void setVerifier(KeycloakJwtVerifier verifier) {
		this.verifier = verifier;
	}

	public void setPushService(PushService pushService) {
		this.pushService = pushService;
	}

	/**
	 * GET /push/vapid-public-key — the applicationServerKey the PWA must pass to
	 * pushManager.subscribe(). Public by nature, so no token required.
	 */
	public void vapidPublicKey(Exchange exchange) throws SQLException, GeneralSecurityException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("publicKey", pushService.publicKeyBase64());
		Http.sendJson(exchange, 200, body);
	}

	/**
	 * POST /push/subscribe — body is PushSubscription.toJSON():
	 * {"endpoint": "...", "expirationTime": null, "keys": {"p256dh": "...", "auth": "..."}}
	 * Idempotent per browser: the row id is the SHA-256 of the endpoint, and a
	 * browser re-subscribing under another login moves to that user.
	 */
	@SuppressWarnings("unchecked")
	public void subscribe(Exchange exchange) throws SQLException, GeneralSecurityException {
		String userId = Http.requireUser(exchange, verifier);
		Map<String, Object> body = Http.jsonBody(exchange);

		String endpoint = Values.text(body, "endpoint", 2048, true);
		Map<String, Object> keys = body.get("keys") instanceof Map ? (Map<String, Object>) body.get("keys") : body;
		String p256dh = Values.text(keys, "p256dh", 256, true);
		String auth = Values.text(keys, "auth", 64, true);

		if (!pushService.isAllowedEndpoint(endpoint)) {
			throw Http.badRequest("endpoint nao pertence a um servico de push conhecido");
		}
		try {
			WebPush.decodePublic(WebPush.unb64(p256dh));
			if (WebPush.unb64(auth).length != 16) throw Http.badRequest("keys.auth deve ter 16 bytes");
		} catch (IllegalArgumentException | GeneralSecurityException e) {
			throw Http.badRequest("keys.p256dh/keys.auth invalidos");
		}

		String id = sha256Hex(endpoint);
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement("INSERT INTO tasktoday_push_subscriptions "
						+ "(id, user_id, endpoint, p256dh, auth) VALUES (?, ?, ?, ?, ?) "
						+ "ON CONFLICT (id) DO UPDATE SET user_id = EXCLUDED.user_id, p256dh = EXCLUDED.p256dh, "
						+ "auth = EXCLUDED.auth")) {
			ps.setString(1, id);
			ps.setString(2, userId);
			ps.setString(3, endpoint);
			ps.setString(4, p256dh);
			ps.setString(5, auth);
			ps.executeUpdate();
		}

		Map<String, Object> resp = new LinkedHashMap<>();
		resp.put("id", id);
		resp.put("endpoint", endpoint);
		Http.sendJson(exchange, 201, resp);
	}

	/**
	 * POST /push/test — optional body {"title": "...", "message": "..."}; sends
	 * to every subscription of the caller. 404 when there is none.
	 */
	public void test(Exchange exchange) throws SQLException, GeneralSecurityException {
		String userId = Http.requireUser(exchange, verifier);
		Map<String, Object> body = Http.jsonBody(exchange);

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("title", Objects.requireNonNullElse(Values.text(body, "title", 120, false), "Task Today App — Teste"));
		payload.put("body", Objects.requireNonNullElse(Values.text(body, "message", 500, false),
				"Notificação push funcionando com sucesso!"));
		payload.put("tag", "tasktoday-test");
		payload.put("data", Map.of("type", "test"));

		PushService.Result result = pushService.sendToUser(userId, payload);
		if (result.sent + result.failed + result.removed == 0) {
			throw Http.notFound("Nenhuma inscricao push registrada para este usuario");
		}
		Http.sendJson(exchange, 200, result.toJson());
	}

	static String sha256Hex(String s) {
		try {
			StringBuilder hex = new StringBuilder(64);
			for (byte b : MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))) {
				hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
			}
			return hex.toString();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}
