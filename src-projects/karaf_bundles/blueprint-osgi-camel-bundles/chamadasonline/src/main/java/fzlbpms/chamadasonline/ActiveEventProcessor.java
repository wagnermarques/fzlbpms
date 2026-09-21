package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

/** Ports GET /events/active (routes/events.ts). Runs after AuthenticateProcessor. */
public class ActiveEventProcessor implements Processor {

	private static final String SELECT_ACTIVE_PERIOD_SQL = "SELECT id FROM event_periods "
			+ "WHERE starts_at <= now() AND ends_at >= now() ORDER BY starts_at DESC LIMIT 1";

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		try (Connection conn = dataSource.getConnection()) {
			UUID periodId = findActivePeriodId(conn);

			Message message = exchange.getMessage();
			message.removeHeaders("*");
			message.setHeader(Exchange.CONTENT_TYPE, "application/json");

			if (periodId == null) {
				message.setBody("{\"active\":false}");
				return;
			}

			EventUtil.PeriodEvent periodEvent = EventUtil.loadPeriodEvent(conn, periodId);
			message.setBody("{\"active\":true,"
					+ "\"event\":" + JsonRender.event(periodEvent) + ","
					+ "\"period\":" + JsonRender.period(periodEvent) + "}");
		}
	}

	private UUID findActivePeriodId(Connection conn) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(SELECT_ACTIVE_PERIOD_SQL);
				ResultSet rs = ps.executeQuery()) {
			return rs.next() ? (UUID) rs.getObject("id") : null;
		}
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
