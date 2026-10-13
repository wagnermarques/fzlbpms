package fzlbpms.evasaoescolar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.Exchange;

public class DashboardProcessor {

	private Db db;
	private KeycloakJwtVerifier verifier;

	public void setDb(Db db) {
		this.db = db;
	}

	public void setVerifier(KeycloakJwtVerifier verifier) {
		this.verifier = verifier;
	}

	public void getStats(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);

		Map<String, Object> stats = new LinkedHashMap<>();

		try (Connection conn = db.getConnection()) {
			// 1. Total students
			try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM evasao_students");
				 ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					stats.put("totalStudents", rs.getInt(1));
				}
			}

			// 2. Status distribution
			Map<String, Integer> byStatus = new LinkedHashMap<>();
			try (PreparedStatement ps = conn.prepareStatement("SELECT status, COUNT(*) FROM evasao_students GROUP BY status");
				 ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					byStatus.put(rs.getString("status"), rs.getInt(2));
				}
			}
			stats.put("studentsByStatus", byStatus);

			// 3. Risk level distribution
			Map<String, Integer> byRisk = new LinkedHashMap<>();
			try (PreparedStatement ps = conn.prepareStatement("SELECT risk_level, COUNT(*) FROM evasao_risk_evaluations GROUP BY risk_level");
				 ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					byRisk.put(rs.getString("risk_level"), rs.getInt(2));
				}
			}
			stats.put("studentsByRiskLevel", byRisk);

			// 4. Active interventions
			try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM evasao_interventions WHERE status IN ('PLANEJADA', 'EM_ANDAMENTO')");
				 ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					stats.put("activeInterventions", rs.getInt(1));
				}
			}

			// 5. Average attendance rate
			try (PreparedStatement ps = conn.prepareStatement("SELECT COALESCE(AVG(attendance_rate), 100.0) FROM evasao_attendance");
				 ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					stats.put("averageAttendanceRate", Math.round(rs.getDouble(1) * 100.0) / 100.0);
				}
			}
		}

		Http.sendJson(exchange, 200, stats);
	}

	public void getAlerts(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);

		List<Map<String, Object>> alerts = new ArrayList<>();

		String sql = "SELECT s.id, s.registration_number, s.name, s.email, s.status, "
				+ "c.name AS class_name, r.risk_level, r.risk_score, r.reasons, r.evaluated_at, "
				+ "a.attendance_rate, a.consecutive_absences "
				+ "FROM evasao_students s "
				+ "JOIN evasao_risk_evaluations r ON s.id = r.student_id "
				+ "LEFT JOIN evasao_classes c ON s.class_id = c.id "
				+ "LEFT JOIN evasao_attendance a ON s.id = a.student_id "
				+ "WHERE r.risk_level IN ('CRITICO', 'ALTO') "
				+ "ORDER BY r.risk_score DESC, a.attendance_rate ASC "
				+ "LIMIT 50";

		try (Connection conn = db.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql);
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("id", rs.getString("id"));
				item.put("registrationNumber", rs.getString("registration_number"));
				item.put("name", rs.getString("name"));
				item.put("email", rs.getString("email"));
				item.put("status", rs.getString("status"));
				item.put("className", rs.getString("class_name"));
				item.put("riskLevel", rs.getString("risk_level"));
				item.put("riskScore", rs.getInt("risk_score"));
				item.put("reasons", Json.parseArray(rs.getString("reasons")));
				item.put("evaluatedAt", rs.getString("evaluated_at"));
				item.put("attendanceRate", rs.getDouble("attendance_rate"));
				item.put("consecutiveAbsences", rs.getInt("consecutive_absences"));
				alerts.add(item);
			}
		}

		Http.sendJson(exchange, 200, alerts);
	}
}
