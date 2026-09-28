package fzlbpms.chamadas;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.sql.DataSource;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class DeviceProcessor implements Processor {

	private static final SecureRandom RANDOM = new SecureRandom();
	private DataSource dataSource;

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void process(Exchange exchange) throws Exception {
		String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
		String method = exchange.getIn().getHeader(Exchange.HTTP_METHOD, String.class);

		if (uri == null) uri = "";

		if (uri.contains("/students/lookup") && "GET".equalsIgnoreCase(method)) {
			handleStudentLookup(exchange);
		} else if (uri.matches(".*/students/[^/]+/enrollment-code$") && "POST".equalsIgnoreCase(method)) {
			handleGenerateEnrollmentCode(exchange);
		} else if (uri.endsWith("/devices/enroll") && "POST".equalsIgnoreCase(method)) {
			handleDeviceEnroll(exchange);
		} else {
			AuthProcessor.sendJson(exchange, 404, Map.of("error", "not_found", "message", "Endpoint nao encontrado: " + uri));
		}
	}

	public void handleStudentLookup(Exchange exchange) throws Exception {
		String rm = exchange.getIn().getHeader("registrationNumber", String.class);
		if (rm == null || rm.isBlank()) {
			// Try query string
			String query = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
			if (query != null && query.contains("registrationNumber=")) {
				for (String param : query.split("&")) {
					if (param.startsWith("registrationNumber=")) {
						rm = param.substring("registrationNumber=".length());
						break;
					}
				}
			}
		}

		if (rm == null || rm.isBlank()) {
			AuthProcessor.sendJson(exchange, 400, Map.of("error", "bad_request", "message", "registrationNumber obrigatorio"));
			return;
		}

		Map<String, Object> student = null;
		try (Connection conn = dataSource.getConnection()) {
			String userSql = "SELECT \"id\", \"name\", \"email\", \"registrationNumber\", \"role\" "
					+ "FROM \"ChamadasUser\" "
					+ "WHERE (\"registrationNumber\" = ? OR \"email\" = ? OR \"id\" = ?) LIMIT 1";

			try (PreparedStatement ps = conn.prepareStatement(userSql)) {
				ps.setString(1, rm.trim());
				ps.setString(2, rm.trim());
				ps.setString(3, rm.trim());
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						student = new LinkedHashMap<>();
						student.put("id", rs.getString("id"));
						student.put("name", rs.getString("name"));
						student.put("email", rs.getString("email"));
						student.put("registrationNumber", rs.getString("registrationNumber"));
						student.put("role", rs.getString("role"));
					}
				}
			}

			if (student == null) {
				AuthProcessor.sendJson(exchange, 404, Map.of("error", "student_not_found", "message", "Aluno nao encontrado"));
				return;
			}

			String studentId = (String) student.get("id");
			String devSql = "SELECT \"id\", \"clientToken\", \"verifiedAt\", \"lastSeenAt\" "
					+ "FROM \"ChamadasDevice\" WHERE \"studentId\" = ? AND \"verified\" = true LIMIT 1";

			boolean hasVerified = false;
			Map<String, Object> devMap = null;

			try (PreparedStatement ps = conn.prepareStatement(devSql)) {
				ps.setString(1, studentId);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						hasVerified = true;
						devMap = new LinkedHashMap<>();
						devMap.put("id", rs.getString("id"));
						devMap.put("clientToken", rs.getString("clientToken"));
						Timestamp vat = rs.getTimestamp("verifiedAt");
						devMap.put("verifiedAt", vat != null ? vat.toInstant().toString() : null);
						Timestamp lsat = rs.getTimestamp("lastSeenAt");
						devMap.put("lastSeenAt", lsat != null ? lsat.toInstant().toString() : null);
					}
				}
			}

			student.put("hasVerifiedDevice", hasVerified);
			student.put("device", devMap);
		}

		AuthProcessor.sendJson(exchange, 200, student);
	}

	public void handleGenerateEnrollmentCode(Exchange exchange) throws Exception {
		String authHeader = exchange.getIn().getHeader("Authorization", String.class);
		Map<String, Object> staffToken = JwtHelper.validateToken(authHeader);

		String staffId = "staff_sys";
		if (staffToken != null && staffToken.containsKey("sub")) {
			staffId = (String) staffToken.get("sub");
		}

		String studentId = exchange.getIn().getHeader("id", String.class);
		if (studentId == null || studentId.isBlank()) {
			String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
			if (uri != null) {
				String[] parts = uri.split("/");
				for (int i = 0; i < parts.length; i++) {
					if ("students".equals(parts[i]) && i + 1 < parts.length) {
						studentId = parts[i + 1];
						break;
					}
				}
			}
		}

		if (studentId == null || studentId.isBlank()) {
			AuthProcessor.sendJson(exchange, 400, Map.of("error", "bad_request", "message", "studentId obrigatorio"));
			return;
		}

		// 6-digit numeric code, expires in 120 seconds
		String code = String.format("%06d", RANDOM.nextInt(1000000));
		Instant now = Instant.now();
		Instant expiresAt = now.plus(120, ChronoUnit.SECONDS);

		try (Connection conn = dataSource.getConnection()) {
			// Ensure staff user exists in ChamadasUser so foreign key references work
			String ensureStaffSql = "INSERT INTO \"ChamadasUser\" (\"id\", \"role\", \"name\", \"email\") "
					+ "VALUES (?, 'STAFF'::\"ChamadasRole\", ?, ?) "
					+ "ON CONFLICT (\"id\") DO NOTHING";
			try (PreparedStatement ps = conn.prepareStatement(ensureStaffSql)) {
				ps.setString(1, staffId);
				ps.setString(2, staffToken != null ? (String) staffToken.getOrDefault("name", staffId) : "Staff Admin");
				ps.setString(3, staffId + "@fzlbpms.local");
				ps.executeUpdate();
			}

			String insertSql = "INSERT INTO \"ChamadasDeviceEnrollmentCode\" "
					+ "(\"id\", \"studentId\", \"code\", \"issuedByStaffId\", \"issuedAt\", \"expiresAt\") "
					+ "VALUES (?, ?, ?, ?, ?, ?)";
			try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
				ps.setString(1, "enc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
				ps.setString(2, studentId);
				ps.setString(3, code);
				ps.setString(4, staffId);
				ps.setTimestamp(5, Timestamp.from(now));
				ps.setTimestamp(6, Timestamp.from(expiresAt));
				ps.executeUpdate();
			}
		}

		Map<String, Object> resp = new LinkedHashMap<>();
		resp.put("code", code);
		resp.put("expiresAt", expiresAt.toString());
		resp.put("studentId", studentId);

		AuthProcessor.sendJson(exchange, 201, resp);
	}

	public void handleDeviceEnroll(Exchange exchange) throws Exception {
		String body = exchange.getIn().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);

		String code = (String) req.get("code");
		String clientToken = (String) req.get("clientToken");
		String fingerprintHash = (String) req.get("fingerprintHash");

		if (code == null || clientToken == null) {
			AuthProcessor.sendJson(exchange, 400, Map.of("error", "bad_request", "message", "code e clientToken obrigatorios"));
			return;
		}

		String enrollmentId = null;
		String studentId = null;
		String staffId = null;

		try (Connection conn = dataSource.getConnection()) {
			String selectCodeSql = "SELECT \"id\", \"studentId\", \"issuedByStaffId\" "
					+ "FROM \"ChamadasDeviceEnrollmentCode\" "
					+ "WHERE \"code\" = ? AND \"expiresAt\" > CURRENT_TIMESTAMP AND \"usedAt\" IS NULL "
					+ "ORDER BY \"issuedAt\" DESC LIMIT 1";

			try (PreparedStatement ps = conn.prepareStatement(selectCodeSql)) {
				ps.setString(1, code.trim());
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						enrollmentId = rs.getString("id");
						studentId = rs.getString("studentId");
						staffId = rs.getString("issuedByStaffId");
					}
				}
			}

			if (enrollmentId == null) {
				AuthProcessor.sendJson(exchange, 400, Map.of("error", "invalid_code", "message", "Codigo invalido ou expirado"));
				return;
			}

			// Mark enrollment code as used
			String markUsedSql = "UPDATE \"ChamadasDeviceEnrollmentCode\" SET \"usedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = ?";
			try (PreparedStatement ps = conn.prepareStatement(markUsedSql)) {
				ps.setString(1, enrollmentId);
				ps.executeUpdate();
			}

			// Check if device with this clientToken exists
			String selectDevSql = "SELECT \"id\" FROM \"ChamadasDevice\" WHERE \"clientToken\" = ?";
			boolean devExists = false;
			try (PreparedStatement ps = conn.prepareStatement(selectDevSql)) {
				ps.setString(1, clientToken);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						devExists = true;
					}
				}
			}

			if (devExists) {
				String updSql = "UPDATE \"ChamadasDevice\" SET "
						+ "\"studentId\" = ?, \"fingerprintHash\" = ?, \"verified\" = true, "
						+ "\"verifiedAt\" = CURRENT_TIMESTAMP, \"verifiedByStaffId\" = ?, \"lastSeenAt\" = CURRENT_TIMESTAMP "
						+ "WHERE \"clientToken\" = ?";
				try (PreparedStatement ps = conn.prepareStatement(updSql)) {
					ps.setString(1, studentId);
					ps.setString(2, fingerprintHash);
					ps.setString(3, staffId);
					ps.setString(4, clientToken);
					ps.executeUpdate();
				}
			} else {
				String insSql = "INSERT INTO \"ChamadasDevice\" "
						+ "(\"id\", \"clientToken\", \"studentId\", \"fingerprintHash\", \"verified\", \"verifiedAt\", \"verifiedByStaffId\", \"lastSeenAt\") "
						+ "VALUES (?, ?, ?, ?, true, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)";
				try (PreparedStatement ps = conn.prepareStatement(insSql)) {
					ps.setString(1, "dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
					ps.setString(2, clientToken);
					ps.setString(3, studentId);
					ps.setString(4, fingerprintHash);
					ps.setString(5, staffId);
					ps.executeUpdate();
				}
			}
		}

		AuthProcessor.sendJson(exchange, 200, Map.of(
				"status", "ok",
				"message", "Dispositivo vinculado e verificado com sucesso",
				"studentId", studentId
		));
	}
}
