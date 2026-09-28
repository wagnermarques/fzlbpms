package fzlbpms.chamadas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import javax.sql.DataSource;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

public class AuthProcessor implements Processor {

	private DataSource dataSource;

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void process(Exchange exchange) throws Exception {
		String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
		String method = exchange.getIn().getHeader(Exchange.HTTP_METHOD, String.class);
		if (uri == null) uri = "";

		if (uri.endsWith("/keycloak") && "POST".equalsIgnoreCase(method)) {
			handleKeycloakAuth(exchange);
		} else if (uri.endsWith("/me") && "GET".equalsIgnoreCase(method)) {
			handleAuthMe(exchange);
		} else {
			sendJson(exchange, 404, Map.of("error", "not_found", "message", "Endpoint nao encontrado: " + uri));
		}
	}

	@SuppressWarnings("unchecked")
	public void handleKeycloakAuth(Exchange exchange) throws Exception {
		String body = exchange.getIn().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);

		String keycloakToken = (String) req.get("keycloakToken");
		String clientToken = (String) req.get("clientToken");

		if (keycloakToken == null || keycloakToken.isBlank()) {
			sendJson(exchange, 400, Map.of("error", "bad_request", "message", "keycloakToken obrigatorio"));
			return;
		}

		// Verified, not just decoded: the roles below decide STAFF vs STUDENT.
		Map<String, Object> claims = JwtHelper.verifyKeycloakToken(keycloakToken);
		if (claims == null || !claims.containsKey("sub")) {
			sendJson(exchange, 401, Map.of("error", "invalid_token", "message", "Token Keycloak invalido"));
			return;
		}

		String sub = (String) claims.get("sub");
		String preferredUsername = (String) claims.getOrDefault("preferred_username", sub);
		String email = (String) claims.getOrDefault("email", preferredUsername + "@fzlbpms.local");

		String name = (String) claims.get("name");
		if (name == null || name.isBlank()) {
			String given = (String) claims.get("given_name");
			String family = (String) claims.get("family_name");
			if (given != null) {
				name = given + (family != null ? " " + family : "");
			} else {
				name = preferredUsername;
			}
		}

		// Check roles in realm_access or direct
		String role = "STUDENT";
		Object realmAccessObj = claims.get("realm_access");
		List<String> rolesList = new ArrayList<>();
		if (realmAccessObj instanceof Map) {
			Object rObj = ((Map<?, ?>) realmAccessObj).get("roles");
			if (rObj instanceof List) {
				for (Object r : (List<?>) rObj) {
					if (r != null) rolesList.add(r.toString());
				}
			}
		}
		if (claims.get("roles") instanceof List) {
			for (Object r : (List<?>) claims.get("roles")) {
				if (r != null) rolesList.add(r.toString());
			}
		}

		if (rolesList.contains("chamadas-staff") || rolesList.contains("admin")) {
			role = "STAFF";
		}

		String registrationNumber = (String) claims.get("registrationNumber");
		if (registrationNumber == null || registrationNumber.isBlank()) {
			registrationNumber = preferredUsername;
		}

		// Auto-register/update in ChamadasUser
		try (Connection conn = dataSource.getConnection()) {
			String upsertUserSql = "INSERT INTO \"ChamadasUser\" (\"id\", \"role\", \"registrationNumber\", \"email\", \"name\") "
					+ "VALUES (?, ?::\"ChamadasRole\", ?, ?, ?) "
					+ "ON CONFLICT (\"id\") DO UPDATE SET "
					+ "\"name\" = EXCLUDED.\"name\", "
					+ "\"email\" = EXCLUDED.\"email\", "
					+ "\"role\" = EXCLUDED.\"role\", "
					+ "\"registrationNumber\" = COALESCE(\"ChamadasUser\".\"registrationNumber\", EXCLUDED.\"registrationNumber\")";

			try (PreparedStatement ps = conn.prepareStatement(upsertUserSql)) {
				ps.setString(1, sub);
				ps.setString(2, role);
				ps.setString(3, registrationNumber);
				ps.setString(4, email);
				ps.setString(5, name);
				ps.executeUpdate();
			}

			// Upsert ChamadasDevice if clientToken is provided
			if (clientToken != null && !clientToken.isBlank()) {
				String selectDeviceSql = "SELECT \"id\", \"studentId\", \"verified\" FROM \"ChamadasDevice\" WHERE \"clientToken\" = ?";
				boolean deviceFound = false;

				try (PreparedStatement ps = conn.prepareStatement(selectDeviceSql)) {
					ps.setString(1, clientToken);
					try (ResultSet rs = ps.executeQuery()) {
						if (rs.next()) {
							deviceFound = true;
							String currentStudentId = rs.getString("studentId");
							// Update last seen
							String updateSql = "UPDATE \"ChamadasDevice\" SET \"lastSeenAt\" = CURRENT_TIMESTAMP"
									+ (currentStudentId == null && "STUDENT".equals(role) ? ", \"studentId\" = ?" : "")
									+ " WHERE \"clientToken\" = ?";
							try (PreparedStatement ups = conn.prepareStatement(updateSql)) {
								if (currentStudentId == null && "STUDENT".equals(role)) {
									ups.setString(1, sub);
									ups.setString(2, clientToken);
								} else {
									ups.setString(1, clientToken);
								}
								ups.executeUpdate();
							}
						}
					}
				}

				if (!deviceFound) {
					String deviceId = "dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
					String insertDeviceSql = "INSERT INTO \"ChamadasDevice\" (\"id\", \"clientToken\", \"studentId\", \"verified\") "
							+ "VALUES (?, ?, ?, false)";
					try (PreparedStatement ps = conn.prepareStatement(insertDeviceSql)) {
						ps.setString(1, deviceId);
						ps.setString(2, clientToken);
						if ("STUDENT".equals(role)) {
							ps.setString(3, sub);
						} else {
							ps.setNull(3, java.sql.Types.VARCHAR);
						}
						ps.executeUpdate();
					}
				}
			}
		}

		// Issue Session JWT
		String sessionToken = JwtHelper.generateSessionToken(sub, name, role, registrationNumber, 86400L);

		Map<String, Object> userMap = new LinkedHashMap<>();
		userMap.put("id", sub);
		userMap.put("name", name);
		userMap.put("role", role);
		userMap.put("email", email);
		userMap.put("registrationNumber", registrationNumber);

		Map<String, Object> resp = new LinkedHashMap<>();
		resp.put("accessToken", sessionToken);
		resp.put("user", userMap);

		sendJson(exchange, 200, resp);
	}

	public void handleAuthMe(Exchange exchange) throws Exception {
		String authHeader = exchange.getIn().getHeader("Authorization", String.class);
		Map<String, Object> tokenPayload = JwtHelper.validateToken(authHeader);

		if (tokenPayload == null) {
			sendJson(exchange, 401, Map.of("error", "unauthorized", "message", "Token invalido ou expirado"));
			return;
		}

		String sub = (String) tokenPayload.get("sub");
		if (sub == null) {
			sub = (String) tokenPayload.get("preferred_username");
		}

		Map<String, Object> userMap = new LinkedHashMap<>();
		try (Connection conn = dataSource.getConnection();
			 PreparedStatement ps = conn.prepareStatement(
					 "SELECT \"id\", \"role\", \"registrationNumber\", \"email\", \"name\" FROM \"ChamadasUser\" WHERE \"id\" = ?")) {
			ps.setString(1, sub);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					userMap.put("id", rs.getString("id"));
					userMap.put("name", rs.getString("name"));
					userMap.put("role", rs.getString("role"));
					userMap.put("email", rs.getString("email"));
					userMap.put("registrationNumber", rs.getString("registrationNumber"));
				}
			}
		}

		if (userMap.isEmpty()) {
			// Fallback to token claims
			userMap.put("id", sub);
			userMap.put("name", tokenPayload.getOrDefault("name", sub));
			userMap.put("role", tokenPayload.getOrDefault("role", "STUDENT"));
			userMap.put("email", tokenPayload.get("email"));
			userMap.put("registrationNumber", tokenPayload.get("registrationNumber"));
		}

		sendJson(exchange, 200, userMap);
	}

	public static void sendJson(Exchange exchange, int statusCode, Object body) {
		Message msg = exchange.getMessage();
		msg.setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
		msg.setHeader(Exchange.CONTENT_TYPE, "application/json; charset=UTF-8");
		msg.setHeader("Access-Control-Allow-Origin", "*");
		msg.setHeader("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS");
		msg.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept");
		msg.setBody(Json.serialize(body));
	}
}
