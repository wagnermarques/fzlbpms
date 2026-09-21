package fzlbpms.chamadasonline;

import java.util.List;

/** Direct port of apps/api/src/lib/geofence.ts. */
public final class GeofenceUtil {

	private static final double EARTH_RADIUS_METERS = 6_371_000;

	private GeofenceUtil() {
	}

	/** Great-circle distance between two lat/lng points, in meters. */
	public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
						* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return EARTH_RADIUS_METERS * c;
	}

	public static Result isWithinGeofence(double lat, double lng, double centerLat, double centerLng,
			double radiusMeters) {
		double distance = haversineMeters(lat, lng, centerLat, centerLng);
		return new Result(distance <= radiusMeters, distance);
	}

	/**
	 * Ray-casting point-in-polygon test. Treats lat/lng as flat x/y, which is
	 * accurate enough at the scale of a single school campus (matches the
	 * TypeScript source's own comment).
	 *
	 * @param polygon list of [lat, lng] pairs, at least 3 points.
	 */
	public static boolean isPointInPolygon(double lat, double lng, List<double[]> polygon) {
		boolean inside = false;
		int n = polygon.size();
		for (int i = 0, j = n - 1; i < n; j = i++) {
			double yi = polygon.get(i)[0];
			double xi = polygon.get(i)[1];
			double yj = polygon.get(j)[0];
			double xj = polygon.get(j)[1];
			boolean intersects = ((yi > lat) != (yj > lat))
					&& (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi);
			if (intersects) {
				inside = !inside;
			}
		}
		return inside;
	}

	private static double[] centroid(List<double[]> polygon) {
		double sumLat = 0;
		double sumLng = 0;
		for (double[] point : polygon) {
			sumLat += point[0];
			sumLng += point[1];
		}
		return new double[] { sumLat / polygon.size(), sumLng / polygon.size() };
	}

	/**
	 * Prioritizes the polygon geofence when present (>= 3 points), falling back
	 * to the circular lat/lng/radius geofence otherwise — matches
	 * isWithinEventGeofence in the TypeScript source. When a polygon is used,
	 * the reported distance is to the polygon's centroid (for staff-review
	 * display), not to the containment boundary.
	 */
	public static Result isWithinEventGeofence(double lat, double lng, double eventLat, double eventLng,
			double radiusMeters, List<double[]> polygon) {
		if (polygon != null && polygon.size() >= 3) {
			boolean within = isPointInPolygon(lat, lng, polygon);
			double[] center = centroid(polygon);
			double distance = haversineMeters(lat, lng, center[0], center[1]);
			return new Result(within, distance);
		}
		return isWithinGeofence(lat, lng, eventLat, eventLng, radiusMeters);
	}

	public static final class Result {
		public final boolean withinRadius;
		public final double distanceMeters;

		Result(boolean withinRadius, double distanceMeters) {
			this.withinRadius = withinRadius;
			this.distanceMeters = distanceMeters;
		}
	}
}
