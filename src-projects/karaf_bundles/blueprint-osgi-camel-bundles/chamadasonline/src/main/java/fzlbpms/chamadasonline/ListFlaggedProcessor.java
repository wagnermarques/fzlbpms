package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

/** Ports GET /events/{eventId}/flagged (routes/events.ts), staff-only. */
public class ListFlaggedProcessor implements Processor {

	private static final String SELECT_SQL = "SELECT c.id, c.student_id, c.event_period_id, c.device_id, "
			+ "c.lat, c.lng, c.distance_meters, c.flagged, c.flag_reasons, c.reviewed, c.review_decision, "
			+ "c.created_at, "
			+ "u.id AS student_user_id, u.name AS student_name, u.registration_number AS student_registration_number, "
			+ "ep.id AS period_id, ep.label AS period_label, "
			+ "d.id AS device_row_id, d.client_token AS device_client_token, d.student_id AS device_student_id "
			+ "FROM checkins c "
			+ "JOIN users u ON u.id = c.student_id "
			+ "JOIN event_periods ep ON ep.id = c.event_period_id "
			+ "JOIN devices d ON d.id = c.device_id "
			+ "WHERE c.flagged = true AND c.reviewed = false AND ep.event_id = ? "
			+ "ORDER BY c.created_at DESC";

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		UUID eventId = RequestUtil.requireUuidPathParam(exchange, "eventId");

		StringBuilder items = new StringBuilder("[");
		int count = 0;
		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
			ps.setObject(1, eventId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					if (count > 0) items.append(",");
					Object deviceStudentId = rs.getObject("device_student_id");
					items.append("{").append(CheckinJson.fields(rs)).append(",")
							.append("\"student\":{")
							.append("\"id\":\"").append(rs.getObject("student_user_id")).append("\",")
							.append("\"name\":").append(Json.nullableString(rs.getString("student_name"))).append(",")
							.append("\"registrationNumber\":")
							.append(Json.nullableString(rs.getString("student_registration_number")))
							.append("},")
							.append("\"eventPeriod\":{")
							.append("\"id\":\"").append(rs.getObject("period_id")).append("\",")
							.append("\"label\":").append(Json.nullableString(rs.getString("period_label")))
							.append("},")
							.append("\"device\":{")
							.append("\"id\":\"").append(rs.getObject("device_row_id")).append("\",")
							.append("\"clientToken\":\"").append(rs.getObject("device_client_token")).append("\",")
							.append("\"studentId\":")
							.append(deviceStudentId == null ? "null" : "\"" + deviceStudentId + "\"")
							.append("}}");
					count++;
				}
			}
		}
		items.append("]");

		Message message = exchange.getMessage();
		message.removeHeaders("*");
		message.setHeader(Exchange.CONTENT_TYPE, "application/json");
		message.setBody("{\"flagged\":" + items + "}");
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
