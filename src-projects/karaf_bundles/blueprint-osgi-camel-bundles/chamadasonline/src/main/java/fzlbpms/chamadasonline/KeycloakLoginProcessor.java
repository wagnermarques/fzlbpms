package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import com.jayway.jsonpath.DocumentContext;

/** Ports POST /auth/keycloak (routes/auth.ts) — Keycloak-token login with auto-provisioning. */
public class KeycloakLoginProcessor implements Processor {

	private static final Set<String> ACCESS_ROLES = Set.of(
			"chamadas-user", "chamadas-student", "chamadas-staff", "admin", "staff");
	private static final Set<String> STAFF_ROLES = Set.of(
			"chamadas-staff", "staff", "admin", "teacher");

	private static final String FIND_USER_SQL = "SELECT id, role, name FROM users "
			+ "WHERE registration_number = ? OR (email IS NOT NULL AND email = ?)";
	private static final String INSERT_USER_SQL = "INSERT INTO users (role, registration_number, name, email, pin_hash) "
			+ "VALUES (?, ?, ?, ?, '') RETURNING id, role, name";
	private static final String UPDATE_USER_SQL = "UPDATE users SET name = ?, email = COALESCE(?, email), "
			+ "role = CASE WHEN ? THEN 'STAFF' ELSE role END WHERE id = ? RETURNING role, name";

	private DataSource dataSource;
	private JwtUtil jwtUtil;
	private KeycloakTokenVerifier keycloakTokenVerifier;

	@Override
	public void process(Exchange exchange) throws Exception {
		DocumentContext body = RequestUtil.parseBody(exchange);
		String keycloakToken = RequestUtil.requireString(body, "$.keycloakToken");
		UUID clientToken = RequestUtil.requireUuid(body, "$.clientToken");

		KeycloakTokenVerifier.Claims claims = keycloakTokenVerifier.verify(keycloakToken);

		boolean hasAccess = claims.allRoles.stream()
				.anyMatch(r -> ACCESS_ROLES.contains(r.toLowerCase(Locale.ROOT)));
		if (!hasAccess && !claims.allRoles.isEmpty()) {
			throw new ForbiddenException("missing_chamadas_role");
		}
		boolean isStaff = claims.allRoles.stream()
				.anyMatch(r -> STAFF_ROLES.contains(r.toLowerCase(Locale.ROOT)));

		String username = claims.preferredUsername != null ? claims.preferredUsername : claims.sub;
		String name = claims.name != null ? claims.name
				: (claims.preferredUsername != null ? claims.preferredUsername : "Aluno");

		try (Connection conn = dataSource.getConnection()) {
			UUID userId;
			String role;
			String userName;

			try (PreparedStatement ps = conn.prepareStatement(FIND_USER_SQL)) {
				ps.setString(1, username);
				ps.setString(2, claims.email);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						userId = (UUID) rs.getObject("id");
						UserSnapshot updated = updateUser(conn, userId, name, claims.email, isStaff);
						role = updated.role;
						userName = updated.name;
					} else {
						try (PreparedStatement insert = conn.prepareStatement(INSERT_USER_SQL)) {
							insert.setString(1, isStaff ? "STAFF" : "STUDENT");
							insert.setString(2, username);
							insert.setString(3, name);
							if (claims.email == null) {
								insert.setNull(4, java.sql.Types.VARCHAR);
							} else {
								insert.setString(4, claims.email);
							}
							try (ResultSet rs2 = insert.executeQuery()) {
								rs2.next();
								userId = (UUID) rs2.getObject("id");
								role = rs2.getString("role");
								userName = rs2.getString("name");
							}
						}
					}
				}
			}

			DeviceUtil.Device device = DeviceUtil.upsertDevice(conn, clientToken.toString(), null);
			DeviceUtil.bindDeviceIfUnbound(conn, device.id, userId);

			String accessToken = jwtUtil.sign(userId.toString(), role);

			Message message = exchange.getMessage();
			message.removeHeaders("*");
			message.setHeader(Exchange.CONTENT_TYPE, "application/json");
			message.setBody("{\"accessToken\":\"" + Json.escape(accessToken) + "\",\"user\":{"
					+ "\"id\":\"" + userId + "\","
					+ "\"name\":" + Json.nullableString(userName) + ","
					+ "\"role\":\"" + role + "\"}}");
		}
	}

	private UserSnapshot updateUser(Connection conn, UUID userId, String name, String email, boolean isStaff)
			throws java.sql.SQLException {
		try (PreparedStatement ps = conn.prepareStatement(UPDATE_USER_SQL)) {
			ps.setString(1, name);
			if (email == null) {
				ps.setNull(2, java.sql.Types.VARCHAR);
			} else {
				ps.setString(2, email);
			}
			ps.setBoolean(3, isStaff);
			ps.setObject(4, userId);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return new UserSnapshot(rs.getString("role"), rs.getString("name"));
			}
		}
	}

	private static final class UserSnapshot {
		final String role;
		final String name;

		UserSnapshot(String role, String name) {
			this.role = role;
			this.name = name;
		}
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public void setJwtUtil(JwtUtil jwtUtil) {
		this.jwtUtil = jwtUtil;
	}

	public void setKeycloakTokenVerifier(KeycloakTokenVerifier keycloakTokenVerifier) {
		this.keycloakTokenVerifier = keycloakTokenVerifier;
	}
}
