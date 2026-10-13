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

public class StudentsProcessor {

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

	public void listClasses(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);
		List<Map<String, Object>> classes = new ArrayList<>();
		String sql = "SELECT id, name, course, year, period, created_at FROM evasao_classes ORDER BY year DESC, name ASC";
		try (Connection conn = db.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql);
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				Map<String, Object> c = new LinkedHashMap<>();
				c.put("id", rs.getString("id"));
				c.put("name", rs.getString("name"));
				c.put("course", rs.getString("course"));
				c.put("year", rs.getInt("year"));
				c.put("period", rs.getString("period"));
				c.put("createdAt", rs.getString("created_at"));
				classes.add(c);
			}
		}
		Http.sendJson(exchange, 200, classes);
	}

	public void createClass(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);
		String body = exchange.getMessage().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);

		String name = (String) req.get("name");
		String course = (String) req.get("course");
		Number year = (Number) req.get("year");
		String period = (String) req.get("period");

		if (name == null || name.isBlank() || course == null || course.isBlank() || year == null || period == null) {
			throw Http.badRequest("Campos obrigatórios: name, course, year, period");
		}

		String id = "turma-" + UUID.randomUUID().toString().substring(0, 8);
		String sql = "INSERT INTO evasao_classes (id, name, course, year, period) VALUES (?, ?, ?, ?, ?)";
		try (Connection conn = db.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, id);
			ps.setString(2, name);
			ps.setString(3, course);
			ps.setInt(4, year.intValue());
			ps.setString(5, period.toUpperCase());
			ps.executeUpdate();
		}

		Map<String, Object> res = new LinkedHashMap<>();
		res.put("id", id);
		res.put("name", name);
		res.put("course", course);
		res.put("year", year.intValue());
		res.put("period", period.toUpperCase());
		Http.sendJson(exchange, 201, res);
	}

	public void listStudents(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);

		String classId = exchange.getMessage().getHeader("classId", String.class);
		String riskLevel = exchange.getMessage().getHeader("riskLevel", String.class);
		String status = exchange.getMessage().getHeader("status", String.class);
		String q = exchange.getMessage().getHeader("q", String.class);

		StringBuilder sql = new StringBuilder(
				"SELECT s.id, s.registration_number, s.name, s.email, s.phone, s.class_id, s.status, "
				+ "c.name AS class_name, r.risk_level, r.risk_score, a.attendance_rate "
				+ "FROM evasao_students s "
				+ "LEFT JOIN evasao_classes c ON s.class_id = c.id "
				+ "LEFT JOIN evasao_risk_evaluations r ON s.id = r.student_id "
				+ "LEFT JOIN evasao_attendance a ON s.id = a.student_id "
				+ "WHERE 1=1 ");

		List<Object> params = new ArrayList<>();
		if (classId != null && !classId.isBlank()) {
			sql.append("AND s.class_id = ? ");
			params.add(classId);
		}
		if (riskLevel != null && !riskLevel.isBlank()) {
			sql.append("AND r.risk_level = ? ");
			params.add(riskLevel.toUpperCase());
		}
		if (status != null && !status.isBlank()) {
			sql.append("AND s.status = ? ");
			params.add(status.toUpperCase());
		}
		if (q != null && !q.isBlank()) {
			sql.append("AND (s.name ILIKE ? OR s.registration_number ILIKE ?) ");
			params.add("%" + q + "%");
			params.add("%" + q + "%");
		}

		sql.append("ORDER BY s.name ASC");

		List<Map<String, Object>> students = new ArrayList<>();
		try (Connection conn = db.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
			for (int i = 0; i < params.size(); i++) {
				ps.setObject(i + 1, params.get(i));
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Map<String, Object> s = new LinkedHashMap<>();
					s.put("id", rs.getString("id"));
					s.put("registrationNumber", rs.getString("registration_number"));
					s.put("name", rs.getString("name"));
					s.put("email", rs.getString("email"));
					s.put("phone", rs.getString("phone"));
					s.put("classId", rs.getString("class_id"));
					s.put("className", rs.getString("class_name"));
					s.put("status", rs.getString("status"));
					s.put("riskLevel", rs.getString("risk_level") != null ? rs.getString("risk_level") : "BAIXO");
					s.put("riskScore", rs.getObject("risk_score") != null ? rs.getInt("risk_score") : 0);
					s.put("attendanceRate", rs.getObject("attendance_rate") != null ? rs.getDouble("attendance_rate") : 100.0);
					students.add(s);
				}
			}
		}

		Http.sendJson(exchange, 200, students);
	}

	public void getStudent(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);
		String id = exchange.getMessage().getHeader("id", String.class);
		if (id == null || id.isBlank()) throw Http.badRequest("Parâmetro id é obrigatório");

		Map<String, Object> student = new LinkedHashMap<>();

		try (Connection conn = db.getConnection()) {
			// Student info
			String sql = "SELECT s.id, s.registration_number, s.name, s.email, s.phone, s.class_id, s.status, s.created_at, "
					+ "c.name AS class_name, c.course, c.year, c.period "
					+ "FROM evasao_students s "
					+ "LEFT JOIN evasao_classes c ON s.class_id = c.id "
					+ "WHERE s.id = ?";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, id);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						throw Http.notFound("Aluno não encontrado: " + id);
					}
					student.put("id", rs.getString("id"));
					student.put("registrationNumber", rs.getString("registration_number"));
					student.put("name", rs.getString("name"));
					student.put("email", rs.getString("email"));
					student.put("phone", rs.getString("phone"));
					student.put("classId", rs.getString("class_id"));
					student.put("className", rs.getString("class_name"));
					student.put("course", rs.getString("course"));
					student.put("year", rs.getObject("year"));
					student.put("period", rs.getString("period"));
					student.put("status", rs.getString("status"));
					student.put("createdAt", rs.getString("created_at"));
				}
			}

			// Attendance
			String sqlAtt = "SELECT total_classes, attended_classes, absences, consecutive_absences, attendance_rate, last_absence_date "
					+ "FROM evasao_attendance WHERE student_id = ?";
			try (PreparedStatement ps = conn.prepareStatement(sqlAtt)) {
				ps.setString(1, id);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						Map<String, Object> att = new LinkedHashMap<>();
						att.put("totalClasses", rs.getInt("total_classes"));
						att.put("attendedClasses", rs.getInt("attended_classes"));
						att.put("absences", rs.getInt("absences"));
						att.put("consecutiveAbsences", rs.getInt("consecutive_absences"));
						att.put("attendanceRate", rs.getDouble("attendance_rate"));
						att.put("lastAbsenceDate", rs.getString("last_absence_date"));
						student.put("attendance", att);
					} else {
						student.put("attendance", null);
					}
				}
			}

			// Risk evaluation
			String sqlRisk = "SELECT risk_level, risk_score, reasons, evaluated_at FROM evasao_risk_evaluations WHERE student_id = ?";
			try (PreparedStatement ps = conn.prepareStatement(sqlRisk)) {
				ps.setString(1, id);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						Map<String, Object> risk = new LinkedHashMap<>();
						risk.put("riskLevel", rs.getString("risk_level"));
						risk.put("riskScore", rs.getInt("risk_score"));
						risk.put("reasons", Json.parseArray(rs.getString("reasons")));
						risk.put("evaluatedAt", rs.getString("evaluated_at"));
						student.put("riskEvaluation", risk);
					} else {
						student.put("riskEvaluation", null);
					}
				}
			}
		}

		Http.sendJson(exchange, 200, student);
	}

	public void createStudent(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);
		String body = exchange.getMessage().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);

		String rm = (String) req.get("registrationNumber");
		String name = (String) req.get("name");
		String email = (String) req.get("email");
		String phone = (String) req.get("phone");
		String classId = (String) req.get("classId");

		if (rm == null || rm.isBlank() || name == null || name.isBlank()) {
			throw Http.badRequest("Campos obrigatórios: registrationNumber e name");
		}

		String id = "aluno-" + UUID.randomUUID().toString().substring(0, 8);

		try (Connection conn = db.getConnection()) {
			String sql = "INSERT INTO evasao_students (id, registration_number, name, email, phone, class_id, status) "
					+ "VALUES (?, ?, ?, ?, ?, ?, 'ATIVO')";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, id);
				ps.setString(2, rm.trim());
				ps.setString(3, name.trim());
				ps.setString(4, email);
				ps.setString(5, phone);
				ps.setString(6, classId);
				ps.executeUpdate();
			}

			// Initialize default attendance
			String sqlAtt = "INSERT INTO evasao_attendance (id, student_id, total_classes, attended_classes, absences, consecutive_absences, attendance_rate) "
					+ "VALUES (?, ?, 0, 0, 0, 0, 100.00)";
			try (PreparedStatement ps = conn.prepareStatement(sqlAtt)) {
				ps.setString(1, "att-" + id);
				ps.setString(2, id);
				ps.executeUpdate();
			}

			// Initial risk evaluation
			riskEvaluator.evaluate(conn, id);
		}

		Map<String, Object> res = new LinkedHashMap<>();
		res.put("id", id);
		res.put("registrationNumber", rm);
		res.put("name", name);
		res.put("status", "ATIVO");
		Http.sendJson(exchange, 201, res);
	}

	public void updateStudent(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);
		String id = exchange.getMessage().getHeader("id", String.class);
		String body = exchange.getMessage().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);

		String name = (String) req.get("name");
		String email = (String) req.get("email");
		String phone = (String) req.get("phone");
		String classId = (String) req.get("classId");
		String status = (String) req.get("status");

		try (Connection conn = db.getConnection()) {
			String sql = "UPDATE evasao_students SET "
					+ "name = COALESCE(?, name), "
					+ "email = COALESCE(?, email), "
					+ "phone = COALESCE(?, phone), "
					+ "class_id = COALESCE(?, class_id), "
					+ "status = COALESCE(?, status), "
					+ "updated_at = NOW() "
					+ "WHERE id = ?";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, name);
				ps.setString(2, email);
				ps.setString(3, phone);
				ps.setString(4, classId);
				ps.setString(5, status);
				ps.setString(6, id);
				int rows = ps.executeUpdate();
				if (rows == 0) throw Http.notFound("Aluno não encontrado: " + id);
			}
		}

		Map<String, Object> res = new LinkedHashMap<>();
		res.put("status", "ok");
		res.put("id", id);
		Http.sendJson(exchange, 200, res);
	}

	public void evaluateStudent(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);
		String id = exchange.getMessage().getHeader("id", String.class);
		if (id == null || id.isBlank()) throw Http.badRequest("id é obrigatório");

		Map<String, Object> eval;
		try (Connection conn = db.getConnection()) {
			eval = riskEvaluator.evaluate(conn, id);
		}
		Http.sendJson(exchange, 200, eval);
	}
}
