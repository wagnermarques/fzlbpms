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

public class EventsProcessor implements Processor {

	private static final String CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
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

		if (uri.endsWith("/events/active") && "GET".equalsIgnoreCase(method)) {
			handleGetActiveEvent(exchange);
		} else if (uri.endsWith("/events") && "POST".equalsIgnoreCase(method)) {
			handleCreateEvent(exchange);
		} else if (uri.matches(".*/events/[^/]+/periods$") && "POST".equalsIgnoreCase(method)) {
			handleCreatePeriod(exchange);
		} else if (uri.matches(".*/events/[^/]+/periods/[^/]+/code$") && "GET".equalsIgnoreCase(method)) {
			handleGetRotatingCode(exchange);
		} else {
			AuthProcessor.sendJson(exchange, 404, Map.of("error", "not_found", "message", "Endpoint nao encontrado: " + uri));
		}
	}

	public void handleGetActiveEvent(Exchange exchange) throws Exception {
		Map<String, Object> resp = new LinkedHashMap<>();

		String sql = "SELECT e.\"id\" AS \"eventId\", e.\"name\" AS \"eventName\", e.\"date\" AS \"eventDate\", "
				+ "e.\"geofenceLat\", e.\"geofenceLng\", e.\"geofenceRadiusMeters\", e.\"geofencePolygon\", "
				+ "p.\"id\" AS \"periodId\", p.\"label\" AS \"periodLabel\", p.\"startsAt\", p.\"endsAt\" "
				+ "FROM \"ChamadasEventPeriod\" p "
				+ "JOIN \"ChamadasEvent\" e ON p.\"eventId\" = e.\"id\" "
				+ "WHERE p.\"startsAt\" <= CURRENT_TIMESTAMP AND p.\"endsAt\" >= CURRENT_TIMESTAMP "
				+ "ORDER BY p.\"startsAt\" ASC LIMIT 1";

		try (Connection conn = dataSource.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					Map<String, Object> event = new LinkedHashMap<>();
					event.put("id", rs.getString("eventId"));
					event.put("name", rs.getString("eventName"));
					event.put("date", rs.getTimestamp("eventDate").toInstant().toString());
					event.put("geofenceLat", rs.getDouble("geofenceLat"));
					event.put("geofenceLng", rs.getDouble("geofenceLng"));
					event.put("geofenceRadiusMeters", rs.getDouble("geofenceRadiusMeters"));
					String polyJson = rs.getString("geofencePolygon");
					event.put("geofencePolygon", polyJson != null ? Json.parse(polyJson) : null);

					Map<String, Object> period = new LinkedHashMap<>();
					period.put("id", rs.getString("periodId"));
					period.put("eventId", rs.getString("eventId"));
					period.put("label", rs.getString("periodLabel"));
					period.put("startsAt", rs.getTimestamp("startsAt").toInstant().toString());
					period.put("endsAt", rs.getTimestamp("endsAt").toInstant().toString());

					resp.put("active", true);
					resp.put("event", event);
					resp.put("period", period);
					AuthProcessor.sendJson(exchange, 200, resp);
					return;
				}
			}
		}

		// Fallback: Check if there's any event created
		String fallbackSql = "SELECT e.\"id\" AS \"eventId\", e.\"name\" AS \"eventName\", e.\"date\" AS \"eventDate\", "
				+ "e.\"geofenceLat\", e.\"geofenceLng\", e.\"geofenceRadiusMeters\", e.\"geofencePolygon\", "
				+ "p.\"id\" AS \"periodId\", p.\"label\" AS \"periodLabel\", p.\"startsAt\", p.\"endsAt\" "
				+ "FROM \"ChamadasEvent\" e "
				+ "LEFT JOIN \"ChamadasEventPeriod\" p ON p.\"eventId\" = e.\"id\" "
				+ "ORDER BY e.\"createdAt\" DESC LIMIT 1";

		try (Connection conn = dataSource.getConnection();
			 PreparedStatement ps = conn.prepareStatement(fallbackSql);
			 ResultSet rs = ps.executeQuery()) {
			if (rs.next() && rs.getString("periodId") != null) {
				Map<String, Object> event = new LinkedHashMap<>();
				event.put("id", rs.getString("eventId"));
				event.put("name", rs.getString("eventName"));
				event.put("date", rs.getTimestamp("eventDate").toInstant().toString());
				event.put("geofenceLat", rs.getDouble("geofenceLat"));
				event.put("geofenceLng", rs.getDouble("geofenceLng"));
				event.put("geofenceRadiusMeters", rs.getDouble("geofenceRadiusMeters"));
				String polyJson = rs.getString("geofencePolygon");
				event.put("geofencePolygon", polyJson != null ? Json.parse(polyJson) : null);

				Map<String, Object> period = new LinkedHashMap<>();
				period.put("id", rs.getString("periodId"));
				period.put("eventId", rs.getString("eventId"));
				period.put("label", rs.getString("periodLabel"));
				period.put("startsAt", rs.getTimestamp("startsAt").toInstant().toString());
				period.put("endsAt", rs.getTimestamp("endsAt").toInstant().toString());

				resp.put("active", true);
				resp.put("event", event);
				resp.put("period", period);
				AuthProcessor.sendJson(exchange, 200, resp);
				return;
			}
		}

		resp.put("active", false);
		AuthProcessor.sendJson(exchange, 200, resp);
	}

	public void handleCreateEvent(Exchange exchange) throws Exception {
		String body = exchange.getIn().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);

		String name = (String) req.get("name");
		if (name == null || name.isBlank()) {
			AuthProcessor.sendJson(exchange, 400, Map.of("error", "bad_request", "message", "Nome do evento obrigatorio"));
			return;
		}

		String id = "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		String dateStr = (String) req.getOrDefault("date", Instant.now().toString());
		Instant eventDate = Instant.parse(dateStr);

		double geofenceLat = ((Number) req.getOrDefault("geofenceLat", 0.0)).doubleValue();
		double geofenceLng = ((Number) req.getOrDefault("geofenceLng", 0.0)).doubleValue();
		double radiusMeters = ((Number) req.getOrDefault("geofenceRadiusMeters", 150.0)).doubleValue();
		Object polygonObj = req.get("geofencePolygon");
		String polygonJson = polygonObj != null ? Json.serialize(polygonObj) : null;

		String insertSql = "INSERT INTO \"ChamadasEvent\" "
				+ "(\"id\", \"name\", \"date\", \"geofenceLat\", \"geofenceLng\", \"geofenceRadiusMeters\", \"geofencePolygon\") "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)";

		try (Connection conn = dataSource.getConnection();
			 PreparedStatement ps = conn.prepareStatement(insertSql)) {
			ps.setString(1, id);
			ps.setString(2, name);
			ps.setTimestamp(3, Timestamp.from(eventDate));
			ps.setDouble(4, geofenceLat);
			ps.setDouble(5, geofenceLng);
			ps.setDouble(6, radiusMeters);
			ps.setString(7, polygonJson);
			ps.executeUpdate();
		}

		Map<String, Object> result = new LinkedHashMap<>(req);
		result.put("id", id);
		result.put("name", name);
		result.put("date", eventDate.toString());
		result.put("geofenceLat", geofenceLat);
		result.put("geofenceLng", geofenceLng);
		result.put("geofenceRadiusMeters", radiusMeters);
		result.put("geofencePolygon", polygonObj);

		AuthProcessor.sendJson(exchange, 201, result);
	}

	public void handleCreatePeriod(Exchange exchange) throws Exception {
		String eventId = exchange.getIn().getHeader("id", String.class);
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

		String body = exchange.getIn().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);

		String label = (String) req.get("label");
		String startsAtStr = (String) req.get("startsAt");
		String endsAtStr = (String) req.get("endsAt");

		if (label == null || startsAtStr == null || endsAtStr == null) {
			AuthProcessor.sendJson(exchange, 400, Map.of("error", "bad_request", "message", "label, startsAt e endsAt obrigatorios"));
			return;
		}

		String periodId = "prd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		Instant startsAt = Instant.parse(startsAtStr);
		Instant endsAt = Instant.parse(endsAtStr);

		String insertSql = "INSERT INTO \"ChamadasEventPeriod\" (\"id\", \"eventId\", \"label\", \"startsAt\", \"endsAt\") "
				+ "VALUES (?, ?, ?, ?, ?)";

		try (Connection conn = dataSource.getConnection();
			 PreparedStatement ps = conn.prepareStatement(insertSql)) {
			ps.setString(1, periodId);
			ps.setString(2, eventId);
			ps.setString(3, label);
			ps.setTimestamp(4, Timestamp.from(startsAt));
			ps.setTimestamp(5, Timestamp.from(endsAt));
			ps.executeUpdate();
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", periodId);
		result.put("eventId", eventId);
		result.put("label", label);
		result.put("startsAt", startsAt.toString());
		result.put("endsAt", endsAt.toString());

		AuthProcessor.sendJson(exchange, 201, result);
	}

	public void handleGetRotatingCode(Exchange exchange) throws Exception {
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

		Instant now = Instant.now();
		String code = null;
		Instant expiresAt = null;

		String querySql = "SELECT \"code\", \"expiresAt\" FROM \"ChamadasCheckinCode\" "
				+ "WHERE \"eventPeriodId\" = ? AND \"expiresAt\" > CURRENT_TIMESTAMP "
				+ "ORDER BY \"issuedAt\" DESC LIMIT 1";

		try (Connection conn = dataSource.getConnection();
			 PreparedStatement ps = conn.prepareStatement(querySql)) {
			ps.setString(1, periodId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					code = rs.getString("code");
					expiresAt = rs.getTimestamp("expiresAt").toInstant();
				}
			}
		}

		if (code == null || expiresAt == null || expiresAt.isBefore(now)) {
			// Generate new 5-character rotating code (45 seconds validity)
			code = generateCode(5);
			expiresAt = now.plus(45, ChronoUnit.SECONDS);

			String insertSql = "INSERT INTO \"ChamadasCheckinCode\" (\"id\", \"eventPeriodId\", \"code\", \"issuedAt\", \"expiresAt\") "
					+ "VALUES (?, ?, ?, ?, ?)";
			try (Connection conn = dataSource.getConnection();
				 PreparedStatement ps = conn.prepareStatement(insertSql)) {
				ps.setString(1, "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
				ps.setString(2, periodId);
				ps.setString(3, code);
				ps.setTimestamp(4, Timestamp.from(now));
				ps.setTimestamp(5, Timestamp.from(expiresAt));
				ps.executeUpdate();
			}
		}

		long secondsRemaining = Math.max(0, ChronoUnit.SECONDS.between(now, expiresAt));

		Map<String, Object> resp = new LinkedHashMap<>();
		resp.put("code", code);
		resp.put("expiresAt", expiresAt.toString());
		resp.put("secondsRemaining", secondsRemaining);

		AuthProcessor.sendJson(exchange, 200, resp);
	}

	private static String generateCode(int length) {
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
		}
		return sb.toString();
	}
}
