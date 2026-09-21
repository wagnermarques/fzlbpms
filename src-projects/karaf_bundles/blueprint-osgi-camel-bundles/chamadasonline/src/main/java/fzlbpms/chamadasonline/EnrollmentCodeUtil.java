package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Ports apps/api/src/lib/enrollmentCode.ts. */
final class EnrollmentCodeUtil {

	static final int CODE_LENGTH = 6;
	static final long TTL_SECONDS = 120;

	private EnrollmentCodeUtil() {
	}

	static final class Code {
		final String code;
		final Instant expiresAt;

		Code(String code, Instant expiresAt) {
			this.code = code;
			this.expiresAt = expiresAt;
		}
	}

	/** Staff generates a short-lived code for a specific student, shown on the staff screen. */
	static Code createEnrollmentCode(Connection conn, UUID studentId, UUID staffId) throws SQLException {
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(TTL_SECONDS);
		String code = CodeGenerator.generate(CODE_LENGTH);
		String sql = "INSERT INTO device_enrollment_codes "
				+ "(student_id, code, issued_by_staff_id, issued_at, expires_at) VALUES (?, ?, ?, ?, ?)";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setObject(1, studentId);
			ps.setString(2, code);
			ps.setObject(3, staffId);
			ps.setTimestamp(4, Timestamp.from(now));
			ps.setTimestamp(5, Timestamp.from(expiresAt));
			ps.executeUpdate();
		}
		return new Code(code, expiresAt);
	}

	/**
	 * Validates and consumes a code entered by the student on their own device.
	 * The code must belong to this exact student, be unexpired, and unused.
	 * Returns the staff id that issued it, or null if invalid.
	 */
	static UUID consumeEnrollmentCode(Connection conn, UUID studentId, String submittedCode) throws SQLException {
		String normalized = submittedCode.trim().toUpperCase();
		String selectSql = "SELECT id, issued_by_staff_id FROM device_enrollment_codes "
				+ "WHERE student_id = ? AND code = ? AND used_at IS NULL AND expires_at > now() "
				+ "ORDER BY issued_at DESC LIMIT 1";
		UUID matchId;
		UUID issuedByStaffId;
		try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
			ps.setObject(1, studentId);
			ps.setString(2, normalized);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) return null;
				matchId = (UUID) rs.getObject("id");
				issuedByStaffId = (UUID) rs.getObject("issued_by_staff_id");
			}
		}

		String updateSql = "UPDATE device_enrollment_codes SET used_at = now() WHERE id = ?";
		try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
			ps.setObject(1, matchId);
			ps.executeUpdate();
		}

		return issuedByStaffId;
	}
}
