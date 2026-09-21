package fzlbpms.chamadasonline;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Renders the scalar Checkin fields shared by ListCheckinsProcessor,
 *  ListFlaggedProcessor, and ReviewCheckinProcessor. Callers wrap the
 *  returned "key":value list with "{" ... [",relation":{...}]* "}". */
final class CheckinJson {

	private CheckinJson() {
	}

	static String fields(ResultSet rs) throws SQLException {
		return "\"id\":\"" + rs.getObject("id") + "\","
				+ "\"studentId\":\"" + rs.getObject("student_id") + "\","
				+ "\"eventPeriodId\":\"" + rs.getObject("event_period_id") + "\","
				+ "\"deviceId\":\"" + rs.getObject("device_id") + "\","
				+ "\"lat\":" + rs.getDouble("lat") + ","
				+ "\"lng\":" + rs.getDouble("lng") + ","
				+ "\"distanceMeters\":" + rs.getDouble("distance_meters") + ","
				+ "\"flagged\":" + rs.getBoolean("flagged") + ","
				+ "\"flagReasons\":" + flagReasonsArray(rs) + ","
				+ "\"reviewed\":" + rs.getBoolean("reviewed") + ","
				+ "\"reviewDecision\":" + Json.nullableString(rs.getString("review_decision")) + ","
				+ "\"createdAt\":" + JsonRender.instant(rs.getTimestamp("created_at").toInstant());
	}

	private static String flagReasonsArray(ResultSet rs) throws SQLException {
		Array array = rs.getArray("flag_reasons");
		if (array == null) return "[]";
		Object[] values = (Object[]) array.getArray();
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < values.length; i++) {
			if (i > 0) sb.append(",");
			sb.append(Json.nullableString((String) values[i]));
		}
		return sb.append("]").toString();
	}
}
