package fzlbpms.evasaoescolar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.camel.Exchange;

public class InterventionsProcessor {

	private Db db;
	private KeycloakJwtVerifier verifier;

	public void setDb(Db db) {
		this.db = db;
	}

	public void setVerifier(KeycloakJwtVerifier verifier) {
		this.verifier = verifier;
	}

	public void listStudentInterventions(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);
		String studentId = exchange.getMessage().getHeader("id", String.class);

		List<Map<String, Object>> list = new ArrayList<>();
		String sql = "SELECT id, type, description, status, assigned_to, outcome, scheduled_for, completed_at, created_at "
				+ "FROM evasao_interventions WHERE student_id = ? ORDER BY created_at DESC";

		try (Connection conn = db.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, studentId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("id", rs.getString("id"));
					item.put("studentId", studentId);
					item.put("type", rs.getString("type"));
					item.put("description", rs.getString("description"));
					item.put("status", rs.getString("status"));
					item.put("assignedTo", rs.getString("assigned_to"));
					item.put("outcome", rs.getString("outcome"));
					item.put("scheduledFor", rs.getString("scheduled_for"));
					item.put("completedAt", rs.getString("completed_at"));
					item.put("createdAt", rs.getString("created_at"));
					list.add(item);
				}
			}
		}

		Http.sendJson(exchange, 200, list);
	}

	public void listAllInterventions(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);
		String status = exchange.getMessage().getHeader("status", String.class);

		StringBuilder sql = new StringBuilder(
				"SELECT i.id, i.student_id, i.type, i.description, i.status, i.assigned_to, i.outcome, "
				+ "i.scheduled_for, i.completed_at, i.created_at, "
				+ "s.name AS student_name, s.registration_number, c.name AS class_name "
				+ "FROM evasao_interventions i "
				+ "JOIN evasao_students s ON i.student_id = s.id "
				+ "LEFT JOIN evasao_classes c ON s.class_id = c.id ");

		if (status != null && !status.isBlank()) {
			sql.append("WHERE i.status = ? ");
		}
		sql.append("ORDER BY i.created_at DESC LIMIT 100");

		List<Map<String, Object>> list = new ArrayList<>();
		try (Connection conn = db.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
			if (status != null && !status.isBlank()) {
				ps.setString(1, status.toUpperCase());
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("id", rs.getString("id"));
					item.put("studentId", rs.getString("student_id"));
					item.put("studentName", rs.getString("student_name"));
					item.put("registrationNumber", rs.getString("registration_number"));
					item.put("className", rs.getString("class_name"));
					item.put("type", rs.getString("type"));
					item.put("description", rs.getString("description"));
					item.put("status", rs.getString("status"));
					item.put("assignedTo", rs.getString("assigned_to"));
					item.put("outcome", rs.getString("outcome"));
					item.put("scheduledFor", rs.getString("scheduled_for"));
					item.put("completedAt", rs.getString("completed_at"));
					item.put("createdAt", rs.getString("created_at"));
					list.add(item);
				}
			}
		}

		Http.sendJson(exchange, 200, list);
	}

	public void createIntervention(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);
		String studentId = exchange.getMessage().getHeader("id", String.class);
		String body = exchange.getMessage().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);

		String type = (String) req.get("type");
		String description = (String) req.get("description");
		String assignedTo = (String) req.get("assignedTo");
		String scheduledFor = (String) req.get("scheduledFor");

		if (type == null || type.isBlank() || description == null || description.isBlank()) {
			throw Http.badRequest("Campos obrigatórios: type e description");
		}

		String id = "int-" + UUID.randomUUID().toString().substring(0, 8);

		try (Connection conn = db.getConnection()) {
			String sql = "INSERT INTO evasao_interventions (id, student_id, type, description, status, assigned_to, scheduled_for, created_at) "
					+ "VALUES (?, ?, ?, ?, 'PLANEJADA', ?, ?, NOW())";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, id);
				ps.setString(2, studentId);
				ps.setString(3, type.toUpperCase());
				ps.setString(4, description.trim());
				ps.setString(5, assignedTo);
				ps.setTimestamp(6, scheduledFor != null ? Timestamp.from(Instant.parse(scheduledFor)) : null);
				ps.executeUpdate();
			}
		}

		Map<String, Object> res = new LinkedHashMap<>();
		res.put("id", id);
		res.put("studentId", studentId);
		res.put("type", type.toUpperCase());
		res.put("status", "PLANEJADA");
		res.put("description", description);
		Http.sendJson(exchange, 201, res);
	}

	public void updateIntervention(Exchange exchange) throws Exception {
		Http.requireUser(exchange, verifier);
		String id = exchange.getMessage().getHeader("id", String.class);
		String body = exchange.getMessage().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);

		String status = (String) req.get("status");
		String outcome = (String) req.get("outcome");

		try (Connection conn = db.getConnection()) {
			boolean isCompleted = "CONCLUIDA".equalsIgnoreCase(status);
			String sql = "UPDATE evasao_interventions SET "
					+ "status = COALESCE(?, status), "
					+ "outcome = COALESCE(?, outcome), "
					+ "completed_at = " + (isCompleted ? "NOW() " : "completed_at ")
					+ "WHERE id = ?";

			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, status != null ? status.toUpperCase() : null);
				ps.setString(2, outcome);
				ps.setString(3, id);
				int rows = ps.executeUpdate();
				if (rows == 0) throw Http.notFound("Intervenção não encontrada: " + id);
			}
		}

		Map<String, Object> res = new LinkedHashMap<>();
		res.put("status", "ok");
		res.put("id", id);
		Http.sendJson(exchange, 200, res);
	}
}
