package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

/** Ports apps/api/src/lib/device.ts. */
final class DeviceUtil {

	private DeviceUtil() {
	}

	static final class Device {
		final UUID id;
		final UUID studentId;
		final boolean verified;
		final String clientToken;
		final Instant verifiedAt;

		Device(UUID id, UUID studentId, boolean verified, String clientToken, Instant verifiedAt) {
			this.id = id;
			this.studentId = studentId;
			this.verified = verified;
			this.clientToken = clientToken;
			this.verifiedAt = verifiedAt;
		}
	}

	/**
	 * Ports upsertDevice: finds-or-creates the Device row for this browser's
	 * persistent clientToken. Every call bumps last_seen_at (mirrors Prisma's
	 * @updatedAt firing on any write, even the fingerprintHash-less {} update
	 * branch in the Node source) and refreshes fingerprint_hash only when a
	 * new one is actually provided.
	 */
	static Device upsertDevice(Connection conn, String clientToken, String fingerprintHash) throws SQLException {
		String sql = "INSERT INTO devices (client_token, fingerprint_hash) VALUES (?, ?) "
				+ "ON CONFLICT (client_token) DO UPDATE SET "
				+ "last_seen_at = now(), "
				+ "fingerprint_hash = COALESCE(EXCLUDED.fingerprint_hash, devices.fingerprint_hash) "
				+ "RETURNING id, student_id, verified, client_token, verified_at";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setObject(1, UUID.fromString(clientToken));
			if (fingerprintHash == null) {
				ps.setNull(2, Types.VARCHAR);
			} else {
				ps.setString(2, fingerprintHash);
			}
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return toDevice(rs);
			}
		}
	}

	/** Ports bindDeviceIfUnbound. */
	static void bindDeviceIfUnbound(Connection conn, UUID deviceId, UUID studentId) throws SQLException {
		String sql = "UPDATE devices SET student_id = ? WHERE id = ? AND student_id IS NULL";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setObject(1, studentId);
			ps.setObject(2, deviceId);
			ps.executeUpdate();
		}
	}

	/** Ports getVerifiedDeviceForStudent. */
	static Device getVerifiedDeviceForStudent(Connection conn, UUID studentId) throws SQLException {
		String sql = "SELECT id, student_id, verified, client_token, verified_at FROM devices "
				+ "WHERE student_id = ? AND verified = true LIMIT 1";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setObject(1, studentId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? toDevice(rs) : null;
			}
		}
	}

	/**
	 * Completes an in-person enrollment: the device making this request becomes
	 * the sole verified device for the student, replacing any previous one
	 * (e.g. a lost or replaced phone) rather than leaving both marked verified.
	 * Ports enrollVerifiedDevice.
	 */
	static void enrollVerifiedDevice(Connection conn, UUID studentId, UUID staffId, String clientToken,
			String fingerprintHash) throws SQLException {
		String unverifyPreviousSql = "UPDATE devices SET verified = false, verified_at = NULL, "
				+ "verified_by_staff_id = NULL, student_id = NULL WHERE student_id = ? AND verified = true";
		try (PreparedStatement ps = conn.prepareStatement(unverifyPreviousSql)) {
			ps.setObject(1, studentId);
			ps.executeUpdate();
		}

		String upsertSql = "INSERT INTO devices "
				+ "(client_token, student_id, fingerprint_hash, verified, verified_at, verified_by_staff_id) "
				+ "VALUES (?, ?, ?, true, now(), ?) "
				+ "ON CONFLICT (client_token) DO UPDATE SET "
				+ "student_id = EXCLUDED.student_id, "
				+ "fingerprint_hash = COALESCE(EXCLUDED.fingerprint_hash, devices.fingerprint_hash), "
				+ "verified = true, verified_at = now(), verified_by_staff_id = EXCLUDED.verified_by_staff_id, "
				+ "last_seen_at = now()";
		try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
			ps.setObject(1, UUID.fromString(clientToken));
			ps.setObject(2, studentId);
			if (fingerprintHash == null) {
				ps.setNull(3, Types.VARCHAR);
			} else {
				ps.setString(3, fingerprintHash);
			}
			ps.setObject(4, staffId);
			ps.executeUpdate();
		}
	}

	private static Device toDevice(ResultSet rs) throws SQLException {
		Timestamp verifiedAt = rs.getTimestamp("verified_at");
		return new Device(
				(UUID) rs.getObject("id"),
				(UUID) rs.getObject("student_id"),
				rs.getBoolean("verified"),
				rs.getString("client_token"),
				verifiedAt == null ? null : verifiedAt.toInstant());
	}
}
