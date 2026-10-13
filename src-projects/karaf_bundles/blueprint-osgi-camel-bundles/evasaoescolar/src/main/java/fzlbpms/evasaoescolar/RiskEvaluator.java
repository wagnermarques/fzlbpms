package fzlbpms.evasaoescolar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RiskEvaluator {

	private static final Logger LOG = LoggerFactory.getLogger(RiskEvaluator.class);

	public Map<String, Object> evaluate(Connection conn, String studentId) throws SQLException {
		double attendanceRate = 100.0;
		int consecutiveAbsences = 0;
		int totalClasses = 0;

		// 1. Read attendance
		String sqlAtt = "SELECT total_classes, attendance_rate, consecutive_absences FROM evasao_attendance WHERE student_id = ?";
		try (PreparedStatement ps = conn.prepareStatement(sqlAtt)) {
			ps.setString(1, studentId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					totalClasses = rs.getInt("total_classes");
					attendanceRate = rs.getDouble("attendance_rate");
					consecutiveAbsences = rs.getInt("consecutive_absences");
				}
			}
		}

		// 2. Read failing grades count
		int failingCount = 0;
		String sqlGrades = "SELECT COUNT(*) FROM evasao_grades WHERE student_id = ? AND failing = true";
		try (PreparedStatement ps = conn.prepareStatement(sqlGrades)) {
			ps.setString(1, studentId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					failingCount = rs.getInt(1);
				}
			}
		}

		// 3. Compute score and reasons
		int score = 0;
		List<String> reasons = new ArrayList<>();

		if (totalClasses > 0) {
			if (attendanceRate < 75.0) {
				score += 40;
				reasons.add("frequencia_abaixo_75");
			} else if (attendanceRate < 85.0) {
				score += 20;
				reasons.add("frequencia_abaixo_85");
			}
		}

		if (consecutiveAbsences >= 5) {
			score += 30;
			reasons.add("faltas_consecutivas_5");
		} else if (consecutiveAbsences >= 3) {
			score += 15;
			reasons.add("faltas_consecutivas_3");
		}

		if (failingCount >= 3) {
			score += 25;
			reasons.add("reprovacao_multiplas_disciplinas");
		} else if (failingCount >= 1) {
			score += 10;
			reasons.add("reprovacao_parcial");
		}

		String riskLevel;
		if (score >= 75) {
			riskLevel = "CRITICO";
		} else if (score >= 50) {
			riskLevel = "ALTO";
		} else if (score >= 25) {
			riskLevel = "MEDIO";
		} else {
			riskLevel = "BAIXO";
		}

		if (reasons.isEmpty()) {
			reasons.add("frequencia_regular");
		}

		String reasonsJson = Json.serialize(reasons);
		String evalId = "risk-" + UUID.randomUUID().toString().substring(0, 8);

		// 4. Upsert into evasao_risk_evaluations
		String sqlUpsert = "INSERT INTO evasao_risk_evaluations (id, student_id, risk_level, risk_score, reasons, evaluated_at) "
				+ "VALUES (?, ?, ?, ?, ?, NOW()) "
				+ "ON CONFLICT (student_id) DO UPDATE SET "
				+ "risk_level = EXCLUDED.risk_level, "
				+ "risk_score = EXCLUDED.risk_score, "
				+ "reasons = EXCLUDED.reasons, "
				+ "evaluated_at = NOW() "
				+ "RETURNING id, evaluated_at";

		String evaluatedAt = "";
		try (PreparedStatement ps = conn.prepareStatement(sqlUpsert)) {
			ps.setString(1, evalId);
			ps.setString(2, studentId);
			ps.setString(3, riskLevel);
			ps.setInt(4, score);
			ps.setString(5, reasonsJson);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					evalId = rs.getString("id");
					evaluatedAt = rs.getString("evaluated_at");
				}
			}
		}

		// 5. Update student status if applicable
		if ("ALTO".equals(riskLevel) || "CRITICO".equals(riskLevel)) {
			try (PreparedStatement ps = conn.prepareStatement(
					"UPDATE evasao_students SET status = 'EM_RISCO', updated_at = NOW() WHERE id = ? AND status = 'ATIVO'")) {
				ps.setString(1, studentId);
				ps.executeUpdate();
			}
		} else if ("BAIXO".equals(riskLevel)) {
			try (PreparedStatement ps = conn.prepareStatement(
					"UPDATE evasao_students SET status = 'ATIVO', updated_at = NOW() WHERE id = ? AND status = 'EM_RISCO'")) {
				ps.setString(1, studentId);
				ps.executeUpdate();
			}
		}

		LOG.info("Avaliação de risco do aluno {}: nível={} score={} motivos={}",
				studentId, riskLevel, score, reasons);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", evalId);
		result.put("studentId", studentId);
		result.put("riskLevel", riskLevel);
		result.put("riskScore", score);
		result.put("reasons", reasons);
		result.put("evaluatedAt", evaluatedAt);
		return result;
	}
}
