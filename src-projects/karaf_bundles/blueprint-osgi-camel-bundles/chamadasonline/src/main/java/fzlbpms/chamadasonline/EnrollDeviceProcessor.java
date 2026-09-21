package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import com.jayway.jsonpath.DocumentContext;

/** Ports POST /devices/enroll (routes/devices.ts) — student, on their own phone,
 *  enters the code shown on the staff screen to make this device the verified
 *  one for their account. */
public class EnrollDeviceProcessor implements Processor {

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		String role = AuthUtil.requireUserRole(exchange);
		if (!"STUDENT".equals(role)) {
			throw new ForbiddenException("forbidden");
		}
		UUID studentId = UUID.fromString(AuthUtil.requireUserId(exchange));

		DocumentContext body = RequestUtil.parseBody(exchange);
		String code = RequestUtil.requireString(body, "$.code");
		if (code.length() < 4 || code.length() > 10) {
			throw new BadRequestException("invalid_payload");
		}
		UUID clientToken = RequestUtil.requireUuid(body, "$.clientToken");
		String fingerprintHash = RequestUtil.optionalString(body, "$.fingerprintHash");

		try (Connection conn = dataSource.getConnection()) {
			UUID issuedByStaffId = EnrollmentCodeUtil.consumeEnrollmentCode(conn, studentId, code);
			if (issuedByStaffId == null) {
				throw new BadRequestException("invalid_or_expired_code");
			}

			DeviceUtil.enrollVerifiedDevice(conn, studentId, issuedByStaffId, clientToken.toString(),
					fingerprintHash);

			Message message = exchange.getMessage();
			message.removeHeaders("*");
			message.setHeader(Exchange.CONTENT_TYPE, "application/json");
			message.setBody("{\"status\":\"ok\"}");
		}
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
