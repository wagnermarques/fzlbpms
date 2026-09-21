package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import com.jayway.jsonpath.DocumentContext;

/** Ports POST /events (routes/events.ts), staff-only. */
public class CreateEventProcessor implements Processor {

	private static final String INSERT_SQL = "INSERT INTO events "
			+ "(name, date, geofence_lat, geofence_lng, geofence_radius_meters, geofence_polygon) "
			+ "VALUES (?, ?, ?, ?, ?, ?) RETURNING id, created_at";

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		DocumentContext body = RequestUtil.parseBody(exchange);
		String name = RequestUtil.requireString(body, "$.name");
		Instant date = requireInstant(body, "$.date");
		double geofenceLat = RequestUtil.requireDouble(body, "$.geofenceLat");
		double geofenceLng = RequestUtil.requireDouble(body, "$.geofenceLng");
		if (geofenceLat < -90 || geofenceLat > 90 || geofenceLng < -180 || geofenceLng > 180) {
			throw new BadRequestException("invalid_payload");
		}
		Object radiusRaw = body.read("$.geofenceRadiusMeters");
		double geofenceRadiusMeters = radiusRaw instanceof Number ? ((Number) radiusRaw).doubleValue() : 150;
		if (geofenceRadiusMeters <= 0) {
			throw new BadRequestException("invalid_payload");
		}

		Object polygonRaw = body.read("$.geofencePolygon");
		String polygonJson = polygonRaw == null ? null : polygonRaw.toString();
		List<double[]> polygon = EventUtil.parsePolygon(polygonJson);
		if (polygonRaw != null && (polygon == null || polygon.size() < 3)) {
			throw new BadRequestException("invalid_payload");
		}

		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
			ps.setString(1, name);
			ps.setTimestamp(2, Timestamp.from(date));
			ps.setDouble(3, geofenceLat);
			ps.setDouble(4, geofenceLng);
			ps.setDouble(5, geofenceRadiusMeters);
			if (polygonJson == null) {
				ps.setNull(6, Types.OTHER);
			} else {
				ps.setObject(6, polygonJson, Types.OTHER);
			}
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				UUID id = (UUID) rs.getObject("id");
				Instant createdAt = rs.getTimestamp("created_at").toInstant();

				Message message = exchange.getMessage();
				message.removeHeaders("*");
				message.setHeader(Exchange.CONTENT_TYPE, "application/json");
				message.setHeader(Exchange.HTTP_RESPONSE_CODE, 201);
				message.setBody(JsonRender.event(id, name, date, geofenceLat, geofenceLng,
						geofenceRadiusMeters, polygon, createdAt));
			}
		}
	}

	private Instant requireInstant(DocumentContext body, String path) {
		String value = RequestUtil.requireString(body, path);
		try {
			return Instant.parse(value);
		} catch (java.time.format.DateTimeParseException e) {
			throw new BadRequestException("invalid_payload");
		}
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
