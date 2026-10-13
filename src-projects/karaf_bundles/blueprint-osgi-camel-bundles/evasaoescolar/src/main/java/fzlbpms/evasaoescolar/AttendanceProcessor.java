package fzlbpms.evasaoescolar;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.camel.Exchange;

public class AttendanceProcessor {

	private Db db;
	private KeycloakJwtVerifier verifier;
	private RiskEvaluator riskEvaluator;

	public void setDb(Db db) {
		this.db = db;
	}

	public void setVerifier(KeycloakJwtVerifier verifier) {
		this.verifier = verifier;
	}

	public void setRiskEvaluator(RiskEvaluator riskEvaluator) {
		this.riskEvaluator = riskEvaluator;
	}

	public void getAttendance(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);
		String studentId = exchange.getMessage().getHeader("id", String.class);

		Map<String, Object> att = new LinkedHashMap<>();
		String sql = "SELECT total_classes, attended_classes, absences, consecutive_absences, attendance_rate, last_absence_date, updated_at "
				+ "FROM evasao_attendance WHERE student_id = ?";

		try (Connection conn = db.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, studentId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw Http.notFound("Registro de frequência não encontrado para o aluno: " + studentId);
				}
				att.put("studentId", studentId);
				att.put("totalClasses", rs.getInt("total_classes"));
				att.put("attendedClasses", rs.getInt("attended_classes"));
				att.put("absences", rs.getInt("absences"));
				att.put("consecutiveAbsences", rs.getInt("consecutive_absences"));
				att.put("attendanceRate", rs.getDouble("attendance_rate"));
				att.put("lastAbsenceDate", rs.getString("last_absence_date"));
				att.put("updatedAt", rs.getString("updated_at"));
			}
		}

		Http.sendJson(exchange, 200, att);
	}

	public void recordAttendance(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);
		String studentId = exchange.getMessage().getHeader("id", String.class);
		String body = exchange.getMessage().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);

		Number total = (Number) req.get("totalClasses");
		Number attended = (Number) req.get("attendedClasses");
		Number consecutive = (Number) req.get("consecutiveAbsences");
		String lastAbsence = (String) req.get("lastAbsenceDate");

		if (total == null || attended == null) {
			throw Http.badRequest("Campos obrigatórios: totalClasses e attendedClasses");
		}

		int totalClasses = total.intValue();
		int attendedClasses = attended.intValue();
		int absences = Math.max(0, totalClasses - attendedClasses);
		int consecutiveAbsences = consecutive != null ? consecutive.intValue() : 0;
		double rate = totalClasses > 0 ? (attendedClasses * 100.0) / totalClasses : 100.0;
		rate = Math.round(rate * 100.0) / 100.0;

		Map<String, Object> evaluation;
		try (Connection conn = db.getConnection()) {
			String sql = "INSERT INTO evasao_attendance (id, student_id, total_classes, attended_classes, absences, consecutive_absences, attendance_rate, last_absence_date, updated_at) "
					+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW()) "
					+ "ON CONFLICT (student_id) DO UPDATE SET "
					+ "total_classes = EXCLUDED.total_classes, "
					+ "attended_classes = EXCLUDED.attended_classes, "
					+ "absences = EXCLUDED.absences, "
					+ "consecutive_absences = EXCLUDED.consecutive_absences, "
					+ "attendance_rate = EXCLUDED.attendance_rate, "
					+ "last_absence_date = EXCLUDED.last_absence_date, "
					+ "updated_at = NOW()";

			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, "att-" + studentId);
				ps.setString(2, studentId);
				ps.setInt(3, totalClasses);
				ps.setInt(4, attendedClasses);
				ps.setInt(5, absences);
				ps.setInt(6, consecutiveAbsences);
				ps.setDouble(7, rate);
				ps.setDate(8, lastAbsence != null ? Date.valueOf(lastAbsence) : null);
				ps.executeUpdate();
			}

			// Automatically re-evaluate risk upon attendance update!
			evaluation = riskEvaluator.evaluate(conn, studentId);
		}

		Map<String, Object> res = new LinkedHashMap<>();
		res.put("studentId", studentId);
		res.put("totalClasses", totalClasses);
		res.put("attendedClasses", attendedClasses);
		res.put("absences", absences);
		res.put("consecutiveAbsences", consecutiveAbsences);
		res.put("attendanceRate", rate);
		res.put("riskEvaluation", evaluation);
		Http.sendJson(exchange, 200, res);
	}
}
