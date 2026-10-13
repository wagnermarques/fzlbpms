package fzlbpms.evasaoescolar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.camel.Exchange;

public class GradesProcessor {

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

	public void listGrades(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);
		String studentId = exchange.getMessage().getHeader("id", String.class);

		List<Map<String, Object>> grades = new ArrayList<>();
		String sql = "SELECT id, subject, term, grade, failing, created_at FROM evasao_grades WHERE student_id = ? ORDER BY term ASC, subject ASC";

		try (Connection conn = db.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, studentId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Map<String, Object> g = new LinkedHashMap<>();
					g.put("id", rs.getString("id"));
					g.put("studentId", studentId);
					g.put("subject", rs.getString("subject"));
					g.put("term", rs.getString("term"));
					g.put("grade", rs.getDouble("grade"));
					g.put("failing", rs.getBoolean("failing"));
					g.put("createdAt", rs.getString("created_at"));
					grades.add(g);
				}
			}
		}

		Http.sendJson(exchange, 200, grades);
	}

	public void recordGrade(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);
		String studentId = exchange.getMessage().getHeader("id", String.class);
		String body = exchange.getMessage().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);

		String subject = (String) req.get("subject");
		String term = (String) req.get("term");
		Number gradeNum = (Number) req.get("grade");

		if (subject == null || subject.isBlank() || term == null || term.isBlank() || gradeNum == null) {
			throw Http.badRequest("Campos obrigatórios: subject, term, grade");
		}

		double grade = gradeNum.doubleValue();
		boolean failing = grade < 6.0;
		String id = "grd-" + UUID.randomUUID().toString().substring(0, 8);

		Map<String, Object> evaluation;
		try (Connection conn = db.getConnection()) {
			String sql = "INSERT INTO evasao_grades (id, student_id, subject, term, grade, failing, created_at) "
					+ "VALUES (?, ?, ?, ?, ?, ?, NOW())";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, id);
				ps.setString(2, studentId);
				ps.setString(3, subject.trim());
				ps.setString(4, term.trim());
				ps.setDouble(5, grade);
				ps.setBoolean(6, failing);
				ps.executeUpdate();
			}

			// Automatically re-evaluate risk upon recording grades!
			evaluation = riskEvaluator.evaluate(conn, studentId);
		}

		Map<String, Object> res = new LinkedHashMap<>();
		res.put("id", id);
		res.put("studentId", studentId);
		res.put("subject", subject);
		res.put("term", term);
		res.put("grade", grade);
		res.put("failing", failing);
		res.put("riskEvaluation", evaluation);
		Http.sendJson(exchange, 201, res);
	}
}
