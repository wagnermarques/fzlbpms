package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

/** Ports GET /students/lookup (routes/devices.ts), staff-only — secretaria
 *  desk lookup of a student by RM (matrícula). */
public class StudentLookupProcessor implements Processor {

	private static final String SELECT_SQL = "SELECT id, name, registration_number FROM users "
			+ "WHERE role = 'STUDENT' AND registration_number = ?";

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		String registrationNumber = exchange.getIn().getHeader("registrationNumber", String.class);
		if (registrationNumber == null || registrationNumber.isBlank()) {
			throw new BadRequestException("registration_number_required");
		}

		try (Connection conn = dataSource.getConnection()) {
			UUID studentId;
			String name;
			try (PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
				ps.setString(1, registrationNumber);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						throw new NotFoundException("student_not_found");
					}
					studentId = (UUID) rs.getObject("id");
					name = rs.getString("name");
				}
			}

			DeviceUtil.Device verifiedDevice = DeviceUtil.getVerifiedDeviceForStudent(conn, studentId);

			Message message = exchange.getMessage();
			message.removeHeaders("*");
			message.setHeader(Exchange.CONTENT_TYPE, "application/json");
			message.setBody("{\"student\":{"
					+ "\"id\":\"" + studentId + "\","
					+ "\"name\":" + Json.nullableString(name) + ","
					+ "\"registrationNumber\":" + Json.nullableString(registrationNumber) + "},"
					+ "\"verifiedDevice\":" + verifiedDeviceJson(verifiedDevice) + "}");
		}
	}

	private String verifiedDeviceJson(DeviceUtil.Device device) {
		if (device == null) return "null";
		return "{\"verifiedAt\":" + JsonRender.instant(device.verifiedAt) + "}";
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
