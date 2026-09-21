package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

/** Ports POST /students/{id}/enrollment-code (routes/devices.ts), staff-only — a
 *  short-lived code shown on the staff screen while the student is present. */
public class IssueEnrollmentCodeProcessor implements Processor {

	private static final String FIND_STUDENT_SQL = "SELECT id FROM users WHERE id = ? AND role = 'STUDENT'";

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		UUID studentId = RequestUtil.requireUuidPathParam(exchange, "id");
		UUID staffId = UUID.fromString(AuthUtil.requireUserId(exchange));

		try (Connection conn = dataSource.getConnection()) {
			try (PreparedStatement ps = conn.prepareStatement(FIND_STUDENT_SQL)) {
				ps.setObject(1, studentId);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						throw new NotFoundException("student_not_found");
					}
				}
			}

			EnrollmentCodeUtil.Code code = EnrollmentCodeUtil.createEnrollmentCode(conn, studentId, staffId);

			Message message = exchange.getMessage();
			message.removeHeaders("*");
			message.setHeader(Exchange.CONTENT_TYPE, "application/json");
			message.setBody("{\"code\":\"" + code.code + "\",\"expiresAt\":" + JsonRender.instant(code.expiresAt)
					+ "}");
		}
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
