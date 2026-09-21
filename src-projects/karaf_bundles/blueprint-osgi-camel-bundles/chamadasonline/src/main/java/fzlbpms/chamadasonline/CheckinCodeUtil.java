package fzlbpms.chamadasonline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Ports apps/api/src/lib/checkinCode.ts. */
final class CheckinCodeUtil {

	static final int CODE_LENGTH = 5;
	static final long CODE_ROTATION_SECONDS = 45;

	private CheckinCodeUtil() {
	}

	static final class Code {
		final String code;
		final Instant expiresAt;

		Code(String code, Instant expiresAt) {
			this.code = code;
			this.expiresAt = expiresAt;
		}
	}

	/**
	 * A submitted code is valid if it matches the current code, or the
	 * immediately preceding one (grace window for projector/typing lag).
	 */
	static boolean isCodeValidForPeriod(Connection conn, UUID eventPeriodId, String submittedCode)
			throws SQLException {
		String normalized = submittedCode.trim().toUpperCase();
		String sql = "SELECT code, expires_at FROM checkin_codes WHERE event_period_id = ? "
				+ "ORDER BY issued_at DESC LIMIT 2";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setObject(1, eventPeriodId);
			try (ResultSet rs = ps.executeQuery()) {
				Instant now = Instant.now();
				while (rs.next()) {
					if (normalized.equals(rs.getString("code"))) {
						Instant expiresAt = rs.getTimestamp("expires_at").toInstant();
						if (expiresAt.plusSeconds(CODE_ROTATION_SECONDS).isAfter(now)) {
							return true;
						}
					}
				}
				return false;
			}
		}
	}

	/**
	 * Returns the currently active code for a period, generating a new one if the
	 * previous one has expired (rotation happens lazily, on read, rather than via
	 * a background timer) — ports getOrRotateCurrentCode.
	 */
	static Code getOrRotateCurrentCode(Connection conn, UUID eventPeriodId) throws SQLException {
		String selectSql = "SELECT code, expires_at FROM checkin_codes WHERE event_period_id = ? "
				+ "ORDER BY issued_at DESC LIMIT 1";
		try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
			ps.setObject(1, eventPeriodId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					Instant expiresAt = rs.getTimestamp("expires_at").toInstant();
					if (expiresAt.isAfter(Instant.now())) {
						return new Code(rs.getString("code"), expiresAt);
					}
				}
			}
		}

		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(CODE_ROTATION_SECONDS);
		String code = CodeGenerator.generate(CODE_LENGTH);
		String insertSql = "INSERT INTO checkin_codes (event_period_id, code, issued_at, expires_at) "
				+ "VALUES (?, ?, ?, ?)";
		try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
			ps.setObject(1, eventPeriodId);
			ps.setString(2, code);
			ps.setTimestamp(3, Timestamp.from(now));
			ps.setTimestamp(4, Timestamp.from(expiresAt));
			ps.executeUpdate();
		}
		return new Code(code, expiresAt);
	}
}
