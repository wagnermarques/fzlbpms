package fzlbpms.contatemeantes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

public class GetDeviceStatusProcessor implements Processor {

	private static final String SELECT_SQL = "SELECT device_id, user_name, latitude, longitude, accuracy, "
			+ "battery_level, is_charging, last_seen FROM device_status WHERE device_id = ?";

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		String deviceId = exchange.getIn().getHeader("deviceId", String.class);
		if (deviceId == null || deviceId.isBlank()) {
			throw new IllegalArgumentException("Missing required path parameter: deviceId.");
		}

		Message message = exchange.getMessage();
		// The Jetty REST binding reuses the inbound request's Message for the
		// response, so at this point it still carries every original request
		// header (Accept, User-Agent, cookies, the deviceId path param itself,
		// ...) — strip them before setting the ones we actually want, or they
		// ride back out on the response (harmless with curl, big enough with a
		// real browser to blow nginx's proxy header buffer).
		message.removeHeaders("*");
		message.setHeader(Exchange.CONTENT_TYPE, "application/json");

		try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
			ps.setString(1, deviceId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					message.setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
					message.setBody("{\"error\":\"device not found\"}");
					return;
				}

				StringBuilder json = new StringBuilder("{");
				json.append("\"deviceId\":\"").append(Json.escape(rs.getString("device_id"))).append("\",");
				json.append("\"userName\":").append(Json.nullableString(rs.getString("user_name"))).append(",");
				json.append("\"latitude\":").append(rs.getDouble("latitude")).append(",");
				json.append("\"longitude\":").append(rs.getDouble("longitude")).append(",");
				json.append("\"accuracy\":").append(Json.nullableNumber(rs.getObject("accuracy"))).append(",");
				json.append("\"batteryLevel\":").append(Json.nullableNumber(rs.getObject("battery_level")))
						.append(",");
				json.append("\"isCharging\":").append(Json.nullableBoolean(rs.getObject("is_charging"))).append(",");
				json.append("\"lastSeen\":\"").append(rs.getTimestamp("last_seen").toInstant()).append("\"");
				json.append("}");
				message.setBody(json.toString());
			}
		}
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
