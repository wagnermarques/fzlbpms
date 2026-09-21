package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

/** Ports GET /events/{eventId}/periods/{periodId}/code (routes/events.ts), staff-only —
 *  the projector view polls this for the current rotating code. */
public class RotateCodeProcessor implements Processor {

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		UUID periodId = RequestUtil.requireUuidPathParam(exchange, "periodId");

		try (Connection conn = dataSource.getConnection()) {
			CheckinCodeUtil.Code current = CheckinCodeUtil.getOrRotateCurrentCode(conn, periodId);
			long secondsRemaining = Math.max(0,
					Math.round((current.expiresAt.toEpochMilli() - Instant.now().toEpochMilli()) / 1000.0));

			Message message = exchange.getMessage();
			message.removeHeaders("*");
			message.setHeader(Exchange.CONTENT_TYPE, "application/json");
			message.setBody("{\"code\":\"" + current.code + "\","
					+ "\"expiresAt\":" + JsonRender.instant(current.expiresAt) + ","
					+ "\"secondsRemaining\":" + secondsRemaining + "}");
		}
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
