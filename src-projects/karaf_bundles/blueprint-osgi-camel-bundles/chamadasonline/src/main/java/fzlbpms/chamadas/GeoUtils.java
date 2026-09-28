package fzlbpms.chamadas;

import java.util.List;
import java.util.Map;

public final class GeoUtils {

	private static final double EARTH_RADIUS_METERS = 6371000.0;

	private GeoUtils() {
	}

	public static double haversineDistanceMeters(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);

		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(dLng / 2) * Math.sin(dLng / 2);

		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return EARTH_RADIUS_METERS * c;
	}

	@SuppressWarnings("unchecked")
	public static boolean isPointInPolygon(double lat, double lng, Object polygonObj) {
		if (polygonObj == null) return false;
		if (!(polygonObj instanceof List)) return false;

		List<?> list = (List<?>) polygonObj;
		if (list.isEmpty()) return false;

		double[] lats = new double[list.size()];
		double[] lngs = new double[list.size()];

		for (int i = 0; i < list.size(); i++) {
			Object item = list.get(i);
			if (item instanceof Map) {
				Map<String, Object> pt = (Map<String, Object>) item;
				Number pLat = (Number) pt.get("lat");
				Number pLng = (Number) pt.get("lng");
				if (pLat == null || pLng == null) return false;
				lats[i] = pLat.doubleValue();
				lngs[i] = pLng.doubleValue();
			} else if (item instanceof List) {
				List<?> coords = (List<?>) item;
				if (coords.size() < 2) return false;
				lats[i] = ((Number) coords.get(0)).doubleValue();
				lngs[i] = ((Number) coords.get(1)).doubleValue();
			} else {
				return false;
			}
		}

		boolean inside = false;
		int n = list.size();
		for (int i = 0, j = n - 1; i < n; j = i++) {
			double xi = lats[i], yi = lngs[i];
			double xj = lats[j], yj = lngs[j];

			boolean intersect = ((yi > lng) != (yj > lng))
					&& (lat < (xj - xi) * (lng - yi) / (yj - yi) + xi);
			if (intersect) {
				inside = !inside;
			}
		}
		return inside;
	}
}
