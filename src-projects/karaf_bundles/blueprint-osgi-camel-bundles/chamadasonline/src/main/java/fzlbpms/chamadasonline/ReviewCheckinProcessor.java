package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import com.jayway.jsonpath.DocumentContext;

/** Ports PATCH /checkins/{id}/review (routes/events.ts), staff-only. */
public class ReviewCheckinProcessor implements Processor {

	private static final Set<String> VALID_DECISIONS = Set.of("approved", "rejected");

	private static final String UPDATE_SQL = "UPDATE checkins SET reviewed = true, review_decision = ? "
			+ "WHERE id = ? RETURNING id, student_id, event_period_id, device_id, lat, lng, distance_meters, "
			+ "flagged, flag_reasons, reviewed, review_decision, created_at";

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		UUID checkinId = RequestUtil.requireUuidPathParam(exchange, "id");

		DocumentContext body = RequestUtil.parseBody(exchange);
		String decision = RequestUtil.requireString(body, "$.decision");
		if (!VALID_DECISIONS.contains(decision)) {
			throw new BadRequestException("invalid_decision");
		}

		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
			ps.setString(1, decision);
			ps.setObject(2, checkinId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new NotFoundException("not_found");
				}

				Message message = exchange.getMessage();
				message.removeHeaders("*");
				message.setHeader(Exchange.CONTENT_TYPE, "application/json");
				message.setBody("{" + CheckinJson.fields(rs) + "}");
			}
		}
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
