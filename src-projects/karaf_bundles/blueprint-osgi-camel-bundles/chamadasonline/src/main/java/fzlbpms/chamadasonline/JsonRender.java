package fzlbpms.chamadasonline;

import java.time.Instant;
import java.util.List;

/** Renders the shared Event/EventPeriod JSON shape used by the events endpoints. */
final class JsonRender {

	private JsonRender() {
	}

	static String event(EventUtil.PeriodEvent pe) {
		return event(pe.eventId, pe.eventName, pe.eventDate, pe.geofenceLat, pe.geofenceLng,
				pe.geofenceRadiusMeters, pe.geofencePolygon, pe.eventCreatedAt);
	}

	static String event(java.util.UUID id, String name, Instant date, double geofenceLat, double geofenceLng,
			double geofenceRadiusMeters, List<double[]> geofencePolygon, Instant createdAt) {
		return "{"
				+ "\"id\":\"" + id + "\","
				+ "\"name\":" + Json.nullableString(name) + ","
				+ "\"date\":" + instant(date) + ","
				+ "\"geofenceLat\":" + geofenceLat + ","
				+ "\"geofenceLng\":" + geofenceLng + ","
				+ "\"geofenceRadiusMeters\":" + geofenceRadiusMeters + ","
				+ "\"geofencePolygon\":" + polygon(geofencePolygon) + ","
				+ "\"createdAt\":" + instant(createdAt)
				+ "}";
	}

	static String period(EventUtil.PeriodEvent pe) {
		return period(pe.periodId, pe.eventId, pe.label, pe.startsAt, pe.endsAt);
	}

	static String period(java.util.UUID id, java.util.UUID eventId, String label, Instant startsAt, Instant endsAt) {
		return "{"
				+ "\"id\":\"" + id + "\","
				+ "\"eventId\":\"" + eventId + "\","
				+ "\"label\":" + Json.nullableString(label) + ","
				+ "\"startsAt\":" + instant(startsAt) + ","
				+ "\"endsAt\":" + instant(endsAt)
				+ "}";
	}

	static String instant(Instant value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private static String polygon(List<double[]> points) {
		if (points == null) return "null";
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < points.size(); i++) {
			if (i > 0) sb.append(",");
			sb.append("{\"lat\":").append(points.get(i)[0]).append(",\"lng\":").append(points.get(i)[1]).append("}");
		}
		return sb.append("]").toString();
	}
}
