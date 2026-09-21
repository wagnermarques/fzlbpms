package fzlbpms.contatemeantes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

public class ListGroupLocationsProcessor implements Processor {

	private static final String SELECT_SQL = "SELECT device_id, user_name, latitude, longitude, accuracy, "
			+ "battery_level, is_charging, last_seen FROM device_status "
			+ "WHERE group_id = ? ORDER BY device_id";

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		String groupId = exchange.getIn().getHeader("groupId", String.class);
		if (groupId == null || groupId.isBlank()) {
			throw new IllegalArgumentException("Missing required path parameter: groupId.");
		}

		Message message = exchange.getMessage();
		message.removeHeaders("*");
		message.setHeader(Exchange.CONTENT_TYPE, "application/json");

		StringBuilder json = new StringBuilder("[");
		try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
			ps.setString(1, groupId);
			try (ResultSet rs = ps.executeQuery()) {
				boolean first = true;
				while (rs.next()) {
					if (!first) {
						json.append(",");
					}
					first = false;
					json.append("{");
					json.append("\"deviceId\":\"").append(Json.escape(rs.getString("device_id"))).append("\",");
					json.append("\"userName\":").append(Json.nullableString(rs.getString("user_name"))).append(",");
					json.append("\"latitude\":").append(rs.getDouble("latitude")).append(",");
					json.append("\"longitude\":").append(rs.getDouble("longitude")).append(",");
					json.append("\"accuracy\":").append(Json.nullableNumber(rs.getObject("accuracy"))).append(",");
					json.append("\"batteryLevel\":").append(Json.nullableNumber(rs.getObject("battery_level")))
							.append(",");
					json.append("\"isCharging\":").append(Json.nullableBoolean(rs.getObject("is_charging")))
							.append(",");
					json.append("\"lastSeen\":\"").append(rs.getTimestamp("last_seen").toInstant()).append("\"");
					json.append("}");
				}
			}
		}
		json.append("]");

		message.setBody(json.toString());
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
