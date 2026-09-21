package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

/** Ports GET /events/{eventId}/periods/{periodId}/checkins (routes/events.ts), staff-only. */
public class ListCheckinsProcessor implements Processor {

	private static final String SELECT_SQL = "SELECT c.id, c.student_id, c.event_period_id, c.device_id, "
			+ "c.lat, c.lng, c.distance_meters, c.flagged, c.flag_reasons, c.reviewed, c.review_decision, "
			+ "c.created_at, u.id AS student_user_id, u.name AS student_name, "
			+ "u.registration_number AS student_registration_number "
			+ "FROM checkins c JOIN users u ON u.id = c.student_id "
			+ "WHERE c.event_period_id = ? ORDER BY c.created_at DESC";

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		UUID periodId = RequestUtil.requireUuidPathParam(exchange, "periodId");

		StringBuilder items = new StringBuilder("[");
		int count = 0;
		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
			ps.setObject(1, periodId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					if (count > 0) items.append(",");
					items.append("{").append(CheckinJson.fields(rs)).append(",")
							.append("\"student\":{")
							.append("\"id\":\"").append(rs.getObject("student_user_id")).append("\",")
							.append("\"name\":").append(Json.nullableString(rs.getString("student_name"))).append(",")
							.append("\"registrationNumber\":")
							.append(Json.nullableString(rs.getString("student_registration_number")))
							.append("}}");
					count++;
				}
			}
		}
		items.append("]");

		Message message = exchange.getMessage();
		message.removeHeaders("*");
		message.setHeader(Exchange.CONTENT_TYPE, "application/json");
		message.setBody("{\"count\":" + count + ",\"checkins\":" + items + "}");
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
