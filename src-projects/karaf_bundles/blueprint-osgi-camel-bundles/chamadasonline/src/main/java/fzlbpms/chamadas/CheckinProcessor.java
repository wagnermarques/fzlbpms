package fzlbpms.chamadas;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import javax.sql.DataSource;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class CheckinProcessor implements Processor {

	private DataSource dataSource;

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void process(Exchange exchange) throws Exception {
		String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
		String method = exchange.getIn().getHeader(Exchange.HTTP_METHOD, String.class);

		if (uri == null) uri = "";

		if (uri.endsWith("/checkins") && "POST".equalsIgnoreCase(method)) {
			handleCheckinSubmit(exchange);
		} else if (uri.matches(".*/events/[^/]+/periods/[^/]+/checkins$") && "GET".equalsIgnoreCase(method)) {
			handleListPeriodCheckins(exchange);
		} else if (uri.matches(".*/events/[^/]+/flagged$") && "GET".equalsIgnoreCase(method)) {
			handleListFlaggedCheckins(exchange);
		} else if (uri.matches(".*/checkins/[^/]+/review$") && "PATCH".equalsIgnoreCase(method)) {
			handleReviewCheckin(exchange);
		} else {
			AuthProcessor.sendJson(exchange, 404, Map.of("error", "not_found", "message", "Endpoint nao encontrado: " + uri));
		}
	}

	public void handleCheckinSubmit(Exchange exchange) throws Exception {
		String authHeader = exchange.getIn().getHeader("Authorization", String.class);
		Map<String, Object> tokenPayload = JwtHelper.validateToken(authHeader);

		if (tokenPayload == null) {
			AuthProcessor.sendJson(exchange, 401, Map.of("error", "unauthorized", "message", "Token invalido ou ausente"));
			return;
		}

		String studentId = (String) tokenPayload.get("sub");
		if (studentId == null) {
			studentId = (String) tokenPayload.get("preferred_username");
		}

		String body = exchange.getIn().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);

		String eventPeriodId = (String) req.get("eventPeriodId");
		String code = (String) req.get("code");
		String clientToken = (String) req.get("clientToken");
		String fingerprintHash = (String) req.get("fingerprintHash");

		double lat = ((Number) req.getOrDefault("lat", 0.0)).doubleValue();
		double lng = ((Number) req.getOrDefault("lng", 0.0)).doubleValue();

		if (eventPeriodId == null || code == null || clientToken == null) {
			AuthProcessor.sendJson(exchange, 400, Map.of("error", "bad_request", "message", "eventPeriodId, code e clientToken sao obrigatorios"));
			return;
		}

		List<String> flagReasons = new ArrayList<>();

		try (Connection conn = dataSource.getConnection()) {
			// 1. Check if period exists and get event geofence
			double eventLat = 0.0, eventLng = 0.0, radiusMeters = 150.0;
			Object polygonObj = null;
			boolean periodFound = false;

			String periodSql = "SELECT p.\"id\", e.\"geofenceLat\", e.\"geofenceLng\", e.\"geofenceRadiusMeters\", e.\"geofencePolygon\" "
					+ "FROM \"ChamadasEventPeriod\" p "
					+ "JOIN \"ChamadasEvent\" e ON p.\"eventId\" = e.\"id\" "
					+ "WHERE p.\"id\" = ?";

			try (PreparedStatement ps = conn.prepareStatement(periodSql)) {
				ps.setString(1, eventPeriodId);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						periodFound = true;
						eventLat = rs.getDouble("geofenceLat");
						eventLng = rs.getDouble("geofenceLng");
						radiusMeters = rs.getDouble("geofenceRadiusMeters");
						String polyJson = rs.getString("geofencePolygon");
						if (polyJson != null) {
							polygonObj = Json.parse(polyJson);
						}
					}
				}
			}

			if (!periodFound) {
				AuthProcessor.sendJson(exchange, 404, Map.of("error", "period_not_found", "message", "Periodo nao encontrado"));
				return;
			}

			// 2. Validate Code (current or immediately previous within last 90 seconds)
			String codeCheckSql = "SELECT \"id\" FROM \"ChamadasCheckinCode\" "
					+ "WHERE \"eventPeriodId\" = ? AND UPPER(\"code\") = UPPER(?) "
					+ "AND \"issuedAt\" >= (CURRENT_TIMESTAMP - INTERVAL '95 seconds') "
					+ "LIMIT 1";

			boolean codeValid = false;
			try (PreparedStatement ps = conn.prepareStatement(codeCheckSql)) {
				ps.setString(1, eventPeriodId);
				ps.setString(2, code.trim());
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						codeValid = true;
					}
				}
			}

			if (!codeValid) {
				flagReasons.add("invalid_or_expired_code");
			}

			// 3. Verify Device Binding
			// Check if student has a verified device
			String verifiedDeviceSql = "SELECT \"id\", \"clientToken\" FROM \"ChamadasDevice\" "
					+ "WHERE \"studentId\" = ? AND \"verified\" = true LIMIT 1";

			String studentVerifiedClientToken = null;
			try (PreparedStatement ps = conn.prepareStatement(verifiedDeviceSql)) {
				ps.setString(1, studentId);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						studentVerifiedClientToken = rs.getString("clientToken");
					}
				}
			}

			if (studentVerifiedClientToken != null && !studentVerifiedClientToken.equals(clientToken)) {
				// Student has an enrolled phone, but is using an unauthorized device
				AuthProcessor.sendJson(exchange, 403, Map.of(
						"error", "device_not_enrolled",
						"message", "Dispositivo nao autorizado para este aluno. Utilize o celular cadastrado na secretaria."
				));
				return;
			}

			// Upsert or retrieve deviceId for this clientToken
			String deviceId = null;
			String deviceStudentId = null;

			String findDevSql = "SELECT \"id\", \"studentId\" FROM \"ChamadasDevice\" WHERE \"clientToken\" = ?";
			try (PreparedStatement ps = conn.prepareStatement(findDevSql)) {
				ps.setString(1, clientToken);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						deviceId = rs.getString("id");
						deviceStudentId = rs.getString("studentId");
					}
				}
			}

			if (deviceId == null) {
				deviceId = "dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
				String insDevSql = "INSERT INTO \"ChamadasDevice\" (\"id\", \"clientToken\", \"studentId\", \"fingerprintHash\", \"verified\") "
						+ "VALUES (?, ?, ?, ?, false)";
				try (PreparedStatement ps = conn.prepareStatement(insDevSql)) {
					ps.setString(1, deviceId);
					ps.setString(2, clientToken);
					ps.setString(3, studentId);
					ps.setString(4, fingerprintHash);
					ps.executeUpdate();
				}
			} else {
				// Update device last seen
				String updDevSql = "UPDATE \"ChamadasDevice\" SET \"lastSeenAt\" = CURRENT_TIMESTAMP WHERE \"id\" = ?";
				try (PreparedStatement ps = conn.prepareStatement(updDevSql)) {
					ps.setString(1, deviceId);
					ps.executeUpdate();
				}
			}

			// Check device bound to other student
			if (deviceStudentId != null && !deviceStudentId.equals(studentId)) {
				flagReasons.add("device_bound_to_other_student");
			}

			// Also check if this device was used by another student in checkins today
			String otherCheckinSql = "SELECT \"studentId\" FROM \"ChamadasCheckin\" "
					+ "WHERE \"deviceId\" = ? AND \"studentId\" != ? "
					+ "AND \"createdAt\"::date = CURRENT_DATE LIMIT 1";
			try (PreparedStatement ps = conn.prepareStatement(otherCheckinSql)) {
				ps.setString(1, deviceId);
				ps.setString(2, studentId);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						if (!flagReasons.contains("device_bound_to_other_student")) {
							flagReasons.add("device_bound_to_other_student");
						}
					}
				}
			}

			// 4. Geofence Calculation
			double distanceMeters = GeoUtils.haversineDistanceMeters(lat, lng, eventLat, eventLng);
			boolean outside = distanceMeters > radiusMeters;
			if (polygonObj != null) {
				boolean insidePoly = GeoUtils.isPointInPolygon(lat, lng, polygonObj);
				if (!insidePoly) outside = true;
			}

			if (outside) {
				flagReasons.add("outside_geofence");
			}

			boolean flagged = !flagReasons.isEmpty();

			// 5. Insert Checkin (idempotent ON CONFLICT)
			String checkinId = "chk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
			Array reasonsSqlArray = conn.createArrayOf("text", flagReasons.toArray(new String[0]));

			String insertCheckinSql = "INSERT INTO \"ChamadasCheckin\" "
					+ "(\"id\", \"studentId\", \"eventPeriodId\", \"deviceId\", \"lat\", \"lng\", \"distanceMeters\", \"flagged\", \"flagReasons\", \"reviewed\", \"createdAt\") "
					+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, false, CURRENT_TIMESTAMP) "
					+ "ON CONFLICT (\"studentId\", \"eventPeriodId\") DO UPDATE SET "
					+ "\"deviceId\" = EXCLUDED.\"deviceId\", "
					+ "\"lat\" = EXCLUDED.\"lat\", "
					+ "\"lng\" = EXCLUDED.\"lng\", "
					+ "\"distanceMeters\" = EXCLUDED.\"distanceMeters\", "
					+ "\"flagged\" = EXCLUDED.\"flagged\", "
					+ "\"flagReasons\" = EXCLUDED.\"flagReasons\"";

			try (PreparedStatement ps = conn.prepareStatement(insertCheckinSql)) {
				ps.setString(1, checkinId);
				ps.setString(2, studentId);
				ps.setString(3, eventPeriodId);
				ps.setString(4, deviceId);
				ps.setDouble(5, lat);
				ps.setDouble(6, lng);
				ps.setDouble(7, distanceMeters);
				ps.setBoolean(8, flagged);
				ps.setArray(9, reasonsSqlArray);
				ps.executeUpdate();
			}
		}

		// Always return 200 OK for student to avoid alerting fraudsters
		AuthProcessor.sendJson(exchange, 200, Map.of("status", "ok"));
	}

	public void handleListPeriodCheckins(Exchange exchange) throws Exception {
		String periodId = exchange.getIn().getHeader("periodId", String.class);
		if (periodId == null || periodId.isBlank()) {
			String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
			if (uri != null) {
				String[] parts = uri.split("/");
				for (int i = 0; i < parts.length; i++) {
					if ("periods".equals(parts[i]) && i + 1 < parts.length) {
						periodId = parts[i + 1];
						break;
					}
				}
			}
		}

		List<Map<String, Object>> checkins = new ArrayList<>();
		String sql = "SELECT c.\"id\", c.\"studentId\", u.\"name\" AS \"studentName\", u.\"registrationNumber\", "
				+ "c.\"deviceId\", d.\"clientToken\", c.\"lat\", c.\"lng\", c.\"distanceMeters\", "
				+ "c.\"flagged\", c.\"flagReasons\", c.\"reviewed\", c.\"reviewDecision\", c.\"createdAt\" "
				+ "FROM \"ChamadasCheckin\" c "
				+ "JOIN \"ChamadasUser\" u ON c.\"studentId\" = u.\"id\" "
				+ "JOIN \"ChamadasDevice\" d ON c.\"deviceId\" = d.\"id\" "
				+ "WHERE c.\"eventPeriodId\" = ? "
				+ "ORDER BY c.\"createdAt\" DESC";

		try (Connection conn = dataSource.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, periodId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("id", rs.getString("id"));
					item.put("studentId", rs.getString("studentId"));
					item.put("studentName", rs.getString("studentName"));
					item.put("registrationNumber", rs.getString("registrationNumber"));
					item.put("deviceId", rs.getString("deviceId"));
					item.put("clientToken", rs.getString("clientToken"));
					item.put("lat", rs.getDouble("lat"));
					item.put("lng", rs.getDouble("lng"));
					item.put("distanceMeters", rs.getDouble("distanceMeters"));
					item.put("flagged", rs.getBoolean("flagged"));

					Array arr = rs.getArray("flagReasons");
					item.put("flagReasons", arr != null ? Arrays.asList((String[]) arr.getArray()) : Collections.emptyList());
					item.put("reviewed", rs.getBoolean("reviewed"));
					item.put("reviewDecision", rs.getString("reviewDecision"));
					item.put("createdAt", rs.getTimestamp("createdAt").toInstant().toString());
					checkins.add(item);
				}
			}
		}

		Map<String, Object> resp = new LinkedHashMap<>();
		resp.put("count", checkins.size());
		resp.put("checkins", checkins);

		AuthProcessor.sendJson(exchange, 200, resp);
	}

	public void handleListFlaggedCheckins(Exchange exchange) throws Exception {
		String eventId = exchange.getIn().getHeader("eventId", String.class);
		if (eventId == null || eventId.isBlank()) {
			String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
			if (uri != null) {
				String[] parts = uri.split("/");
				for (int i = 0; i < parts.length; i++) {
					if ("events".equals(parts[i]) && i + 1 < parts.length) {
						eventId = parts[i + 1];
						break;
					}
				}
			}
		}

		List<Map<String, Object>> flaggedList = new ArrayList<>();
		String sql = "SELECT c.\"id\", c.\"studentId\", u.\"name\" AS \"studentName\", u.\"registrationNumber\", "
				+ "p.\"label\" AS \"periodLabel\", c.\"lat\", c.\"lng\", c.\"distanceMeters\", "
				+ "c.\"flagged\", c.\"flagReasons\", c.\"reviewed\", c.\"reviewDecision\", c.\"createdAt\" "
				+ "FROM \"ChamadasCheckin\" c "
				+ "JOIN \"ChamadasEventPeriod\" p ON c.\"eventPeriodId\" = p.\"id\" "
				+ "JOIN \"ChamadasUser\" u ON c.\"studentId\" = u.\"id\" "
				+ "WHERE p.\"eventId\" = ? AND c.\"flagged\" = true AND c.\"reviewed\" = false "
				+ "ORDER BY c.\"createdAt\" DESC";

		try (Connection conn = dataSource.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, eventId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("id", rs.getString("id"));
					item.put("studentId", rs.getString("studentId"));
					item.put("studentName", rs.getString("studentName"));
					item.put("registrationNumber", rs.getString("registrationNumber"));
					item.put("periodLabel", rs.getString("periodLabel"));
					item.put("lat", rs.getDouble("lat"));
					item.put("lng", rs.getDouble("lng"));
					item.put("distanceMeters", rs.getDouble("distanceMeters"));

					Array arr = rs.getArray("flagReasons");
					item.put("flagReasons", arr != null ? Arrays.asList((String[]) arr.getArray()) : Collections.emptyList());
					item.put("reviewed", rs.getBoolean("reviewed"));
					item.put("reviewDecision", rs.getString("reviewDecision"));
					item.put("createdAt", rs.getTimestamp("createdAt").toInstant().toString());
					flaggedList.add(item);
				}
			}
		}

		Map<String, Object> resp = new LinkedHashMap<>();
		resp.put("count", flaggedList.size());
		resp.put("flaggedCheckins", flaggedList);

		AuthProcessor.sendJson(exchange, 200, resp);
	}

	public void handleReviewCheckin(Exchange exchange) throws Exception {
		String checkinId = exchange.getIn().getHeader("id", String.class);
		if (checkinId == null || checkinId.isBlank()) {
			String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
			if (uri != null) {
				String[] parts = uri.split("/");
				for (int i = 0; i < parts.length; i++) {
					if ("checkins".equals(parts[i]) && i + 1 < parts.length) {
						checkinId = parts[i + 1];
						break;
					}
				}
			}
		}

		String body = exchange.getIn().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);
		String decision = (String) req.get("decision"); // "approved" or "rejected"

		if (decision == null || (!decision.equals("approved") && !decision.equals("rejected"))) {
			AuthProcessor.sendJson(exchange, 400, Map.of("error", "bad_request", "message", "decision deve ser 'approved' ou 'rejected'"));
			return;
		}

		String updateSql = "UPDATE \"ChamadasCheckin\" SET \"reviewed\" = true, \"reviewDecision\" = ? WHERE \"id\" = ?";
		try (Connection conn = dataSource.getConnection();
			 PreparedStatement ps = conn.prepareStatement(updateSql)) {
			ps.setString(1, decision);
			ps.setString(2, checkinId);
			int rows = ps.executeUpdate();
			if (rows == 0) {
				AuthProcessor.sendJson(exchange, 404, Map.of("error", "not_found", "message", "Checkin nao encontrado"));
				return;
			}
		}

		AuthProcessor.sendJson(exchange, 200, Map.of("status", "ok", "checkinId", checkinId, "decision", decision));
	}
}
