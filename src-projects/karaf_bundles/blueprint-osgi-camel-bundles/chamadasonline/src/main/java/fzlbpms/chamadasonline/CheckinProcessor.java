package fzlbpms.chamadasonline;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import com.jayway.jsonpath.DocumentContext;

/**
 * Ports POST /checkins (routes/checkins.ts) — the core fraud-check flow:
 * period-active window, code validity with grace window, idempotent replay,
 * verified-device hard rejection, device upsert, geofence check,
 * device-flag computation, insert.
 */
public class CheckinProcessor implements Processor {

	private DataSource dataSource;

	@Override
	public void process(Exchange exchange) throws Exception {
		String role = AuthUtil.requireUserRole(exchange);
		if (!"STUDENT".equals(role)) {
			throw new ForbiddenException("forbidden");
		}
		UUID studentId = UUID.fromString(AuthUtil.requireUserId(exchange));

		DocumentContext body = RequestUtil.parseBody(exchange);
		UUID eventPeriodId = RequestUtil.requireUuid(body, "$.eventPeriodId");
		String code = RequestUtil.requireString(body, "$.code");
		if (code.length() < 4 || code.length() > 12) {
			throw new BadRequestException("invalid_payload");
		}
		double lat = RequestUtil.requireDouble(body, "$.lat");
		double lng = RequestUtil.requireDouble(body, "$.lng");
		if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
			throw new BadRequestException("invalid_payload");
		}
		UUID clientToken = RequestUtil.requireUuid(body, "$.clientToken");
		String fingerprintHash = RequestUtil.optionalString(body, "$.fingerprintHash");

		try (Connection conn = dataSource.getConnection()) {
			EventUtil.PeriodEvent periodEvent = EventUtil.loadPeriodEvent(conn, eventPeriodId);
			if (periodEvent == null) {
				throw new NotFoundException("period_not_found");
			}

			Instant now = Instant.now();
			if (now.isBefore(periodEvent.startsAt) || now.isAfter(periodEvent.endsAt)) {
				throw new BadRequestException("period_not_active");
			}

			if (!CheckinCodeUtil.isCodeValidForPeriod(conn, eventPeriodId, code)) {
				throw new BadRequestException("invalid_or_expired_code");
			}

			if (checkinExists(conn, studentId, eventPeriodId)) {
				// Idempotent from the student's point of view: they already checked in.
				writeOk(exchange);
				return;
			}

			// Once a student has a staff-verified device on file (secretaria
			// enrollment), only that device can check in for them — hard
			// rejection, not a flag, since identity was already confirmed
			// in person.
			DeviceUtil.Device verifiedDevice = DeviceUtil.getVerifiedDeviceForStudent(conn, studentId);
			if (verifiedDevice != null && !verifiedDevice.clientToken.equals(clientToken.toString())) {
				throw new ForbiddenException("device_not_enrolled");
			}

			DeviceUtil.Device device = DeviceUtil.upsertDevice(conn, clientToken.toString(), fingerprintHash);

			GeofenceUtil.Result geofenceResult = GeofenceUtil.isWithinEventGeofence(
					lat, lng, periodEvent.geofenceLat, periodEvent.geofenceLng,
					periodEvent.geofenceRadiusMeters, periodEvent.geofencePolygon);

			List<String> flagReasons = computeDeviceFlags(conn, device, studentId, fingerprintHash);
			if (!geofenceResult.withinRadius) {
				flagReasons.add("outside_geofence");
			}

			insertCheckin(conn, studentId, eventPeriodId, device.id, lat, lng,
					geofenceResult.distanceMeters, flagReasons);

			// Always report success to the student, whether flagged or not — the
			// flag is only for staff review, and telling the student would tip
			// off anyone trying to game the system.
			writeOk(exchange);
		}
	}

	/** Ports computeDeviceFlags (lib/device.ts) — soft-fraud checks around device
	 *  reuse. Never blocks the check-in, only flags it for staff review. */
	private List<String> computeDeviceFlags(Connection conn, DeviceUtil.Device device, UUID requestingStudentId,
			String fingerprintHash) throws SQLException {
		List<String> reasons = new ArrayList<>();

		if (device.studentId != null && !device.studentId.equals(requestingStudentId)) {
			reasons.add("device_bound_to_other_student");
		}

		if (fingerprintHash != null) {
			String sql = "SELECT 1 FROM devices WHERE fingerprint_hash = ? AND id <> ? "
					+ "AND student_id IS NOT NULL AND student_id <> ? LIMIT 1";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, fingerprintHash);
				ps.setObject(2, device.id);
				ps.setObject(3, requestingStudentId);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						reasons.add("similar_fingerprint_multiple_students");
					}
				}
			}
		}

		return reasons;
	}

	private boolean checkinExists(Connection conn, UUID studentId, UUID eventPeriodId) throws SQLException {
		String sql = "SELECT 1 FROM checkins WHERE student_id = ? AND event_period_id = ?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setObject(1, studentId);
			ps.setObject(2, eventPeriodId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private void insertCheckin(Connection conn, UUID studentId, UUID eventPeriodId, UUID deviceId,
			double lat, double lng, double distanceMeters, List<String> flagReasons) throws SQLException {
		String sql = "INSERT INTO checkins "
				+ "(student_id, event_period_id, device_id, lat, lng, distance_meters, flagged, flag_reasons) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setObject(1, studentId);
			ps.setObject(2, eventPeriodId);
			ps.setObject(3, deviceId);
			ps.setDouble(4, lat);
			ps.setDouble(5, lng);
			ps.setDouble(6, distanceMeters);
			ps.setBoolean(7, !flagReasons.isEmpty());
			Array flagReasonsArray = conn.createArrayOf("text", flagReasons.toArray());
			ps.setArray(8, flagReasonsArray);
			ps.executeUpdate();
		}
	}

	private void writeOk(Exchange exchange) {
		Message message = exchange.getMessage();
		message.removeHeaders("*");
		message.setHeader(Exchange.CONTENT_TYPE, "application/json");
		message.setBody("{\"status\":\"ok\"}");
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
}
