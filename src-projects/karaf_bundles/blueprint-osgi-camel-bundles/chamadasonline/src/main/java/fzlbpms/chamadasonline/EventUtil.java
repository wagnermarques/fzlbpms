package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

/** Shared event/period loading, used by checkins (fraud-check flow) and the
 *  staff events endpoints. Ports the Event/EventPeriod parts of schema.prisma. */
final class EventUtil {

	private static final Configuration LENIENT = Configuration.defaultConfiguration()
			.addOptions(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL);

	private static final String SELECT_PERIOD_WITH_EVENT_SQL = "SELECT "
			+ "ep.id AS period_id, ep.event_id, ep.label, ep.starts_at, ep.ends_at, "
			+ "e.name AS event_name, e.date AS event_date, e.geofence_lat, e.geofence_lng, "
			+ "e.geofence_radius_meters, e.geofence_polygon, e.created_at AS event_created_at "
			+ "FROM event_periods ep JOIN events e ON e.id = ep.event_id WHERE ep.id = ?";

	private EventUtil() {
	}

	static PeriodEvent loadPeriodEvent(Connection conn, UUID eventPeriodId) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(SELECT_PERIOD_WITH_EVENT_SQL)) {
			ps.setObject(1, eventPeriodId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) return null;
				return new PeriodEvent(
						(UUID) rs.getObject("period_id"),
						(UUID) rs.getObject("event_id"),
						rs.getString("label"),
						toInstant(rs.getTimestamp("starts_at")),
						toInstant(rs.getTimestamp("ends_at")),
						rs.getString("event_name"),
						toInstant(rs.getTimestamp("event_date")),
						rs.getDouble("geofence_lat"),
						rs.getDouble("geofence_lng"),
						rs.getDouble("geofence_radius_meters"),
						parsePolygon(rs.getString("geofence_polygon")),
						toInstant(rs.getTimestamp("event_created_at")));
			}
		}
	}

	private static Instant toInstant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}

	/** geofence_polygon is stored as JSONB: a JSON array of {"lat":..,"lng":..}. */
	static List<double[]> parsePolygon(String polygonJson) {
		if (polygonJson == null || polygonJson.isBlank() || "null".equals(polygonJson)) {
			return null;
		}
		List<Map<String, Object>> points = JsonPath.using(LENIENT).parse(polygonJson).read("$");
		if (points == null || points.isEmpty()) return null;
		List<double[]> result = new ArrayList<>(points.size());
		for (Map<String, Object> point : points) {
			Number lat = (Number) point.get("lat");
			Number lng = (Number) point.get("lng");
			if (lat == null || lng == null) continue;
			result.add(new double[] { lat.doubleValue(), lng.doubleValue() });
		}
		return result;
	}

	static final class PeriodEvent {
		final UUID periodId;
		final UUID eventId;
		final String label;
		final Instant startsAt;
		final Instant endsAt;
		final String eventName;
		final Instant eventDate;
		final double geofenceLat;
		final double geofenceLng;
		final double geofenceRadiusMeters;
		final List<double[]> geofencePolygon;
		final Instant eventCreatedAt;

		PeriodEvent(UUID periodId, UUID eventId, String label, Instant startsAt, Instant endsAt,
				String eventName, Instant eventDate, double geofenceLat, double geofenceLng,
				double geofenceRadiusMeters, List<double[]> geofencePolygon, Instant eventCreatedAt) {
			this.periodId = periodId;
			this.eventId = eventId;
			this.label = label;
			this.startsAt = startsAt;
			this.endsAt = endsAt;
			this.eventName = eventName;
			this.eventDate = eventDate;
			this.geofenceLat = geofenceLat;
			this.geofenceLng = geofenceLng;
			this.geofenceRadiusMeters = geofenceRadiusMeters;
			this.geofencePolygon = geofencePolygon;
			this.eventCreatedAt = eventCreatedAt;
		}
	}
}
