package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import com.jayway.jsonpath.DocumentContext;

/** Ports POST /auth/login (routes/auth.ts) — registrationNumber-or-email + PIN login. */
public class LoginProcessor implements Processor {

	private static final String FIND_USER_SQL = "SELECT id, role, name, pin_hash FROM users "
			+ "WHERE registration_number = ? OR email = ?";

	private DataSource dataSource;
	private JwtUtil jwtUtil;

	@Override
	public void process(Exchange exchange) throws Exception {
		DocumentContext body = RequestUtil.parseBody(exchange);
		String identifier = RequestUtil.requireString(body, "$.identifier");
		String pin = RequestUtil.requireString(body, "$.pin");
		if (pin.length() < 4 || pin.length() > 64) {
			throw new BadRequestException("invalid_payload");
		}
		UUID clientToken = RequestUtil.requireUuid(body, "$.clientToken");

		try (Connection conn = dataSource.getConnection()) {
			UUID userId;
			String role;
			String name;

			try (PreparedStatement ps = conn.prepareStatement(FIND_USER_SQL)) {
				ps.setString(1, identifier);
				ps.setString(2, identifier);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next() || !PasswordUtil.matches(pin, rs.getString("pin_hash"))) {
						throw new UnauthorizedException("invalid_credentials");
					}
					userId = (UUID) rs.getObject("id");
					role = rs.getString("role");
					name = rs.getString("name");
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
					+ "\"name\":" + Json.nullableString(name) + ","
					+ "\"role\":\"" + role + "\"}}");
		}
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public void setJwtUtil(JwtUtil jwtUtil) {
		this.jwtUtil = jwtUtil;
	}
}
