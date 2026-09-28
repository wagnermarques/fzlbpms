package fzlbpms.tasktoday;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VAPID identity and delivery of encrypted Web Push messages to the
 * subscriptions stored in tasktoday_push_subscriptions.
 *
 * Key source, first match wins:
 *   1. TASKTODAY_VAPID_PUBLIC_KEY + TASKTODAY_VAPID_PRIVATE_KEY (base64url)
 *   2. tasktoday_vapid_keys row (generated on first use and kept, so browser
 *      subscriptions — bound to the key they were made with — survive restarts)
 */
public class PushService {

	private static final Logger LOG = LoggerFactory.getLogger(PushService.class);

	/**
	 * The server POSTs to whatever endpoint a client registers, so endpoints are
	 * limited to the browser vendors' push services (host suffixes). Extend with
	 * TASKTODAY_PUSH_ALLOWED_HOSTS (comma-separated) if a new one appears.
	 */
	private static final List<String> DEFAULT_PUSH_HOSTS = List.of(
			"fcm.googleapis.com",          // Chrome, Edge (Chromium), Opera, Samsung
			"push.services.mozilla.com",   // Firefox
			"push.apple.com",              // Safari (web.push.apple.com)
			"notify.windows.com");         // legacy Edge / WNS

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final List<String> allowedHosts;
	private final String subject;

	private Db db;
	private volatile ECPrivateKey privateKey;
	private volatile byte[] publicKey;

	public PushService() {
		List<String> hosts = new ArrayList<>(DEFAULT_PUSH_HOSTS);
		for (String h : Env.get("TASKTODAY_PUSH_ALLOWED_HOSTS", "").split(",")) {
			if (!h.isBlank()) hosts.add(h.trim().toLowerCase(Locale.ROOT));
		}
		this.allowedHosts = hosts;
		this.subject = Env.get("TASKTODAY_VAPID_SUBJECT", "mailto:admin@fzlbpms.local");
	}

	public void setDb(Db db) {
		this.db = db;
	}

	/** Counts of one delivery round. */
	public static final class Result {
		public int sent;
		public int failed;
		public int removed;

		Map<String, Object> toJson() {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("sent", sent);
			m.put("failed", failed);
			m.put("removed", removed);
			return m;
		}
	}

	public String publicKeyBase64() throws SQLException, GeneralSecurityException {
		ensureKeys();
		return WebPush.b64(publicKey);
	}

	boolean isAllowedEndpoint(String endpoint) {
		try {
			URI uri = URI.create(endpoint);
			if (!"https".equals(uri.getScheme()) || uri.getHost() == null) return false;
			String host = uri.getHost().toLowerCase(Locale.ROOT);
			for (String allowed : allowedHosts) {
				if (host.equals(allowed) || host.endsWith("." + allowed)) return true;
			}
			return false;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/** Sends one JSON payload to every subscription of the user. */
	public Result sendToUser(String userId, Map<String, Object> payload) throws SQLException, GeneralSecurityException {
		ensureKeys();
		byte[] body = Json.serialize(payload).getBytes(StandardCharsets.UTF_8);
		Result result = new Result();

		List<String[]> subs = new ArrayList<>();
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement(
						"SELECT id, endpoint, p256dh, auth FROM tasktoday_push_subscriptions WHERE user_id = ?")) {
			ps.setString(1, userId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					subs.add(new String[] { rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4) });
				}
			}
		}

		for (String[] sub : subs) {
			int status = send(sub[1], sub[2], sub[3], body);
			if (status >= 200 && status < 300) {
				result.sent++;
			} else if (status == 404 || status == 410) {
				// Subscription expired or the user revoked permission: forget it.
				deleteSubscription(sub[0]);
				result.removed++;
			} else {
				result.failed++;
			}
		}
		return result;
	}

	/** @return the push service's HTTP status, or -1 when it could not be reached */
	private int send(String endpoint, String p256dh, String auth, byte[] payload) {
		if (!isAllowedEndpoint(endpoint)) {
			LOG.warn("Skipping push endpoint outside the allowed push services: {}", endpoint);
			return -1;
		}
		try {
			byte[] encrypted = WebPush.encrypt(payload, WebPush.unb64(p256dh), WebPush.unb64(auth));
			HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
					.timeout(Duration.ofSeconds(10))
					.header("Content-Type", "application/octet-stream")
					.header("Content-Encoding", "aes128gcm")
					.header("TTL", "86400")
					.header("Urgency", "high")
					.header("Authorization", WebPush.vapidAuthorization(endpoint, privateKey, publicKey, subject))
					.POST(HttpRequest.BodyPublishers.ofByteArray(encrypted))
					.build();
			HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() >= 300 && resp.statusCode() != 404 && resp.statusCode() != 410) {
				LOG.warn("Push service {} answered HTTP {}: {}", URI.create(endpoint).getHost(), resp.statusCode(),
						resp.body());
			}
			return resp.statusCode();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return -1;
		} catch (Exception e) {
			LOG.warn("Push delivery to {} failed: {}", URI.create(endpoint).getHost(), e.toString());
			return -1;
		}
	}

	private void deleteSubscription(String id) throws SQLException {
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement("DELETE FROM tasktoday_push_subscriptions WHERE id = ?")) {
			ps.setString(1, id);
			ps.executeUpdate();
		}
	}

	private void ensureKeys() throws SQLException, GeneralSecurityException {
		if (privateKey != null) return;
		synchronized (this) {
			if (privateKey != null) return;

			String envPublic = Env.get("TASKTODAY_VAPID_PUBLIC_KEY", null);
			String envPrivate = Env.get("TASKTODAY_VAPID_PRIVATE_KEY", null);
			if (envPublic != null && envPrivate != null) {
				useKeys(WebPush.unb64(envPublic), WebPush.unb64(envPrivate));
				LOG.info("VAPID keys loaded from TASKTODAY_VAPID_* environment");
				return;
			}

			try (Connection conn = db.getConnection()) {
				if (!loadStoredKeys(conn)) {
					KeyPair kp = WebPush.generateKeyPair();
					try (PreparedStatement ps = conn.prepareStatement("INSERT INTO tasktoday_vapid_keys "
							+ "(id, public_key, private_key) VALUES (1, ?, ?) ON CONFLICT (id) DO NOTHING")) {
						ps.setString(1, WebPush.b64(WebPush.encodePublic((ECPublicKey) kp.getPublic())));
						ps.setString(2, WebPush.b64(WebPush.encodePrivate((ECPrivateKey) kp.getPrivate())));
						ps.executeUpdate();
					}
					// Re-read: if another node won the insert race, its pair is the one.
					if (!loadStoredKeys(conn)) throw new SQLException("tasktoday_vapid_keys still empty after insert");
					LOG.info("Generated a new VAPID key pair and stored it in tasktoday_vapid_keys");
				}
			}
		}
	}

	private boolean loadStoredKeys(Connection conn) throws SQLException, GeneralSecurityException {
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT public_key, private_key FROM tasktoday_vapid_keys WHERE id = 1");
				ResultSet rs = ps.executeQuery()) {
			if (!rs.next()) return false;
			useKeys(WebPush.unb64(rs.getString(1)), WebPush.unb64(rs.getString(2)));
			return true;
		}
	}

	private void useKeys(byte[] pub, byte[] priv) throws GeneralSecurityException {
		WebPush.decodePublic(pub); // validates the point
		ECPrivateKey key = WebPush.decodePrivate(priv);
		this.publicKey = Arrays.copyOf(pub, pub.length);
		this.privateKey = key; // written last: ensureKeys() tests it without the lock
	}
}
