package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import com.jayway.jsonpath.DocumentContext;

/** Ports POST /events/{id}/periods (routes/events.ts), staff-only. */
public class CreatePeriodProcessor implements Processor {

	private static final String INSERT_SQL = "INSERT INTO event_periods (event_id, label, starts_at, ends_at) "
			+ "VALUES (?, ?, ?, ?) RETURNING id";

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		UUID eventId = RequestUtil.requireUuidPathParam(exchange, "id");

		DocumentContext body = RequestUtil.parseBody(exchange);
		String label = RequestUtil.requireString(body, "$.label");
		Instant startsAt = requireInstant(body, "$.startsAt");
		Instant endsAt = requireInstant(body, "$.endsAt");

		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
			ps.setObject(1, eventId);
			ps.setString(2, label);
			ps.setTimestamp(3, Timestamp.from(startsAt));
			ps.setTimestamp(4, Timestamp.from(endsAt));
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				UUID id = (UUID) rs.getObject("id");

				Message message = exchange.getMessage();
				message.removeHeaders("*");
				message.setHeader(Exchange.CONTENT_TYPE, "application/json");
				message.setHeader(Exchange.HTTP_RESPONSE_CODE, 201);
				message.setBody(JsonRender.period(id, eventId, label, startsAt, endsAt));
			}
		}
	}

	private Instant requireInstant(DocumentContext body, String path) {
		String value = RequestUtil.requireString(body, path);
		try {
			return Instant.parse(value);
		} catch (DateTimeParseException e) {
			throw new BadRequestException("invalid_payload");
		}
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
