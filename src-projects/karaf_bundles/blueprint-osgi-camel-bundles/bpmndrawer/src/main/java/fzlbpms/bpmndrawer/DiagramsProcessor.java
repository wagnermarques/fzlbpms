package fzlbpms.bpmndrawer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.camel.Exchange;

public class DiagramsProcessor {

	private Db db;

	public void setDb(Db db) {
		this.db = db;
	}

	public void listDiagrams(Exchange exchange) throws Exception {
		Http.requireUser(exchange);
		List<Map<String, Object>> diagrams = new ArrayList<>();
		String sql = "SELECT id, name, process_key, version, updated_at, updated_by FROM bpmndrawer_diagrams ORDER BY updated_at DESC";
		try (Connection conn = db.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql);
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				Map<String, Object> d = new LinkedHashMap<>();
				d.put("id", rs.getString("id"));
				d.put("name", rs.getString("name"));
				d.put("processKey", rs.getString("process_key"));
				d.put("version", rs.getInt("version"));
				d.put("updatedAt", rs.getString("updated_at"));
				d.put("updatedBy", rs.getString("updated_by"));
				diagrams.add(d);
			}
		}
		Http.sendJson(exchange, 200, diagrams);
	}

	public void getDiagram(Exchange exchange) throws Exception {
		Http.requireUser(exchange);
		String id = exchange.getMessage().getHeader("id", String.class);
		if (id == null || id.isBlank()) throw Http.badRequest("Parâmetro id é obrigatório");

		String sql = "SELECT id, name, process_key, xml, version, created_at, created_by, updated_at, updated_by "
				+ "FROM bpmndrawer_diagrams WHERE id = ?";
		try (Connection conn = db.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) throw Http.notFound("Diagrama não encontrado: " + id);
				Map<String, Object> d = new LinkedHashMap<>();
				d.put("id", rs.getString("id"));
				d.put("name", rs.getString("name"));
				d.put("processKey", rs.getString("process_key"));
				d.put("xml", rs.getString("xml"));
				d.put("version", rs.getInt("version"));
				d.put("createdAt", rs.getString("created_at"));
				d.put("createdBy", rs.getString("created_by"));
				d.put("updatedAt", rs.getString("updated_at"));
				d.put("updatedBy", rs.getString("updated_by"));
				Http.sendJson(exchange, 200, d);
			}
		}
	}

	public void createDiagram(Exchange exchange) throws Exception {
		String user = Http.requireUser(exchange);
		String body = exchange.getMessage().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);

		String name = (String) req.get("name");
		String xml = (String) req.get("xml");
		String processKey = (String) req.get("processKey");

		if (name == null || name.isBlank() || xml == null || xml.isBlank()) {
			throw Http.badRequest("Campos obrigatórios: name, xml");
		}

		String id = UUID.randomUUID().toString();
		String sql = "INSERT INTO bpmndrawer_diagrams (id, name, process_key, xml, version, created_by, updated_by) "
				+ "VALUES (?, ?, ?, ?, 1, ?, ?)";
		try (Connection conn = db.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, id);
			ps.setString(2, name);
			ps.setString(3, processKey);
			ps.setString(4, xml);
			ps.setString(5, user);
			ps.setString(6, user);
			ps.executeUpdate();
		}

		Map<String, Object> res = new LinkedHashMap<>();
		res.put("id", id);
		res.put("name", name);
		res.put("processKey", processKey);
		res.put("xml", xml);
		res.put("version", 1);
		Http.sendJson(exchange, 201, res);
	}

	public void updateDiagram(Exchange exchange) throws Exception {
		String user = Http.requireUser(exchange);
		String id = exchange.getMessage().getHeader("id", String.class);
		String body = exchange.getMessage().getBody(String.class);
		Map<String, Object> req = Json.parseObject(body);

		String name = (String) req.get("name");
		String xml = (String) req.get("xml");
		String processKey = (String) req.get("processKey");

		if (name == null || name.isBlank() || xml == null || xml.isBlank()) {
			throw Http.badRequest("Campos obrigatórios: name, xml");
		}

		String sql = "UPDATE bpmndrawer_diagrams SET "
				+ "name = ?, process_key = ?, xml = ?, version = version + 1, updated_at = NOW(), updated_by = ? "
				+ "WHERE id = ?";
		int newVersion;
		try (Connection conn = db.getConnection()) {
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, name);
				ps.setString(2, processKey);
				ps.setString(3, xml);
				ps.setString(4, user);
				ps.setString(5, id);
				int rows = ps.executeUpdate();
				if (rows == 0) throw Http.notFound("Diagrama não encontrado: " + id);
			}
			try (PreparedStatement ps = conn.prepareStatement("SELECT version FROM bpmndrawer_diagrams WHERE id = ?")) {
				ps.setString(1, id);
				try (ResultSet rs = ps.executeQuery()) {
					rs.next();
					newVersion = rs.getInt("version");
				}
			}
		}

		Map<String, Object> res = new LinkedHashMap<>();
		res.put("id", id);
		res.put("name", name);
		res.put("processKey", processKey);
		res.put("xml", xml);
		res.put("version", newVersion);
		Http.sendJson(exchange, 200, res);
	}

	public void deleteDiagram(Exchange exchange) throws Exception {
		Http.requireUser(exchange);
		String id = exchange.getMessage().getHeader("id", String.class);
		if (id == null || id.isBlank()) throw Http.badRequest("Parâmetro id é obrigatório");

		try (Connection conn = db.getConnection();
			 PreparedStatement ps = conn.prepareStatement("DELETE FROM bpmndrawer_diagrams WHERE id = ?")) {
			ps.setString(1, id);
			int rows = ps.executeUpdate();
			if (rows == 0) throw Http.notFound("Diagrama não encontrado: " + id);
		}

		Map<String, Object> res = new LinkedHashMap<>();
		res.put("status", "ok");
		res.put("id", id);
		Http.sendJson(exchange, 200, res);
	}
}
