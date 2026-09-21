package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

/** Ports GET /auth/me (routes/auth.ts). Runs after AuthenticateProcessor in the route. */
public class MeProcessor implements Processor {

	private static final String SELECT_SQL = "SELECT id, name, role FROM users WHERE id = ?";

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		UUID userId = UUID.fromString(AuthUtil.requireUserId(exchange));

		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
			ps.setObject(1, userId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new NotFoundException("not_found");
				}

				Message message = exchange.getMessage();
				message.removeHeaders("*");
				message.setHeader(Exchange.CONTENT_TYPE, "application/json");
				message.setBody("{\"id\":\"" + rs.getObject("id") + "\","
						+ "\"name\":" + Json.nullableString(rs.getString("name")) + ","
						+ "\"role\":\"" + rs.getString("role") + "\"}");
			}
		}
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
