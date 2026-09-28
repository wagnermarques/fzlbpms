package fzlbpms.tasktoday;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.camel.Exchange;

/**
 * /tasks endpoints. Every statement is scoped to the caller's Keycloak "sub",
 * so a task id belonging to someone else behaves exactly like a missing one.
 *
 * JSON uses the PWA's camelCase model (src/services/task-service.js); the
 * columns are the spec's snake_case.
 */
public class TasksProcessor {

	private static final String COLUMNS = "id, category_id, title, description, priority, status, deadline, "
			+ "alert_type, trigger_minutes, is_archived, completed_at, alarm_fired, created_at, updated_at";

	private Db db;
	private KeycloakJwtVerifier verifier;

	public void setDb(Db db) {
		this.db = db;
	}

	public void setVerifier(KeycloakJwtVerifier verifier) {
		this.verifier = verifier;
	}

	/**
	 * GET /tasks?deadline_scope=overdue|today|this_week|all&category_id=&priority=&status=
	 *            &include_archived=false|true|only&q=&tz=
	 * Same semantics as TaskService.filterTasks() in the PWA; "today" and
	 * "this_week" are computed in tz (default: server zone).
	 */
	public void list(Exchange exchange) throws SQLException {
		String userId = Http.requireUser(exchange, verifier);
		Map<String, String> q = Http.query(exchange);

		StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM tasktoday_tasks WHERE user_id = ?");
		List<Object> params = new ArrayList<>();
		params.add(userId);

		String archived = q.getOrDefault("include_archived", "false");
		if ("only".equals(archived)) {
			sql.append(" AND is_archived = TRUE");
		} else if (!"true".equals(archived)) {
			sql.append(" AND is_archived = FALSE");
		}

		String categoryId = q.get("category_id");
		if (categoryId != null && !categoryId.isEmpty() && !"all".equals(categoryId)) {
			sql.append(" AND category_id = ?");
			params.add(categoryId);
		}
		String priority = q.get("priority");
		if (priority != null && !priority.isEmpty() && !"all".equals(priority)) {
			if (!Values.PRIORITIES.contains(priority)) throw Http.badRequest("priority invalido");
			sql.append(" AND priority = ?");
			params.add(priority);
		}
		String status = q.get("status");
		if (status != null && !status.isEmpty() && !"all".equals(status)) {
			if (!Values.STATUSES.contains(status)) throw Http.badRequest("status invalido");
			sql.append(" AND status = ?");
			params.add(status);
		}
		String search = q.get("q");
		if (search != null && !search.isBlank()) {
			sql.append(" AND (title ILIKE ? OR description ILIKE ?)");
			String like = "%" + search.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
			params.add(like);
			params.add(like);
		}

		String scope = q.getOrDefault("deadline_scope", "all");
		ZoneId zone = Values.zone(q.get("tz"));
		ZonedDateTime startOfToday = ZonedDateTime.now(zone).toLocalDate().atStartOfDay(zone);
		switch (scope) {
			case "all":
				break;
			case "overdue":
				sql.append(" AND deadline < now() AND status <> 'CONCLUIDA'");
				break;
			case "today":
				sql.append(" AND deadline >= ? AND deadline < ?");
				params.add(Timestamp.from(startOfToday.toInstant()));
				params.add(Timestamp.from(startOfToday.plusDays(1).toInstant()));
				break;
			case "this_week":
				sql.append(" AND deadline >= ? AND deadline <= ?");
				params.add(Timestamp.from(startOfToday.toInstant()));
				params.add(Timestamp.from(startOfToday.plusDays(7).toInstant()));
				break;
			default:
				throw Http.badRequest("deadline_scope invalido: use overdue, today, this_week ou all");
		}
		sql.append(" ORDER BY deadline ASC NULLS LAST, created_at DESC");

		List<Map<String, Object>> tasks = new ArrayList<>();
		try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
			for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) tasks.add(toJson(rs));
			}
		}
		Http.sendJson(exchange, 200, tasks);
	}

	/** POST /tasks */
	public void create(Exchange exchange) throws SQLException {
		String userId = Http.requireUser(exchange, verifier);
		Map<String, Object> body = Http.jsonBody(exchange);

		String id = Values.idOrNew(body, "task-");
		String title = Values.text(body, "title", 255, true);
		String description = Values.text(body, "description", 100_000, false);
		String categoryId = Values.text(body, "categoryId", 64, false);
		String priority = Objects.requireNonNullElse(Values.oneOf(body, "priority", Values.PRIORITIES), "MEDIA");
		String status = Objects.requireNonNullElse(Values.oneOf(body, "status", Values.STATUSES), "PENDENTE");
		Timestamp deadline = Values.timestamp(body.get("deadline"), "deadline");
		String alertType = Objects.requireNonNullElse(Values.oneOf(body, "alertType", Values.ALERT_TYPES), "sound");
		int triggerMinutes = Objects.requireNonNullElse(Values.minutes(body, "triggerMinutes"), 15);
		boolean archived = Boolean.TRUE.equals(Values.bool(body, "isArchived"));
		Timestamp now = Values.now();

		try (Connection conn = db.getConnection()) {
			requireVisibleCategory(conn, categoryId, userId);
			String sql = "INSERT INTO tasktoday_tasks (id, user_id, category_id, title, description, priority, status, "
					+ "deadline, alert_type, trigger_minutes, is_archived, completed_at, alarm_fired, created_at, updated_at) "
					+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?, ?) RETURNING " + COLUMNS;
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, id);
				ps.setString(2, userId);
				ps.setString(3, categoryId);
				ps.setString(4, title);
				ps.setString(5, description);
				ps.setString(6, priority);
				ps.setString(7, status);
				ps.setTimestamp(8, deadline);
				ps.setString(9, alertType);
				ps.setInt(10, triggerMinutes);
				ps.setBoolean(11, archived);
				ps.setTimestamp(12, "CONCLUIDA".equals(status) ? now : null);
				ps.setTimestamp(13, now);
				ps.setTimestamp(14, now);
				try (ResultSet rs = ps.executeQuery()) {
					rs.next();
					Http.sendJson(exchange, 201, toJson(rs));
				}
			} catch (SQLException e) {
				if ("23505".equals(e.getSQLState())) {
					throw new Http.ApiError(409, "conflict", "Ja existe uma tarefa com o id " + id);
				}
				throw e;
			}
		}
	}

	/**
	 * PUT /tasks/{id} — partial update, like TaskService.updateTask(id, patch):
	 * only the fields present in the body change. Moving the deadline (or the
	 * trigger) re-arms the alarm; entering CONCLUIDA stamps completedAt, leaving
	 * it clears completedAt.
	 */
	public void update(Exchange exchange) throws SQLException {
		String userId = Http.requireUser(exchange, verifier);
		String id = Http.pathParam(exchange, "id");
		Map<String, Object> patch = Http.jsonBody(exchange);

		try (Connection conn = db.getConnection()) {
			conn.setAutoCommit(false);
			try {
				Map<String, Object> result = applyUpdate(conn, userId, id, patch);
				conn.commit();
				Http.sendJson(exchange, 200, result);
			} catch (RuntimeException | SQLException e) {
				conn.rollback();
				throw e;
			}
		}
	}

	private Map<String, Object> applyUpdate(Connection conn, String userId, String id, Map<String, Object> patch)
			throws SQLException {
		String title, description, categoryId, priority, status, alertType;
		Timestamp deadline, completedAt;
		int triggerMinutes;
		boolean archived, alarmFired;

		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT " + COLUMNS + " FROM tasktoday_tasks WHERE id = ? AND user_id = ? FOR UPDATE")) {
			ps.setString(1, id);
			ps.setString(2, userId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) throw Http.notFound("Tarefa nao encontrada");
				title = rs.getString("title");
				description = rs.getString("description");
				categoryId = rs.getString("category_id");
				priority = rs.getString("priority");
				status = rs.getString("status");
				deadline = rs.getTimestamp("deadline");
				alertType = rs.getString("alert_type");
				triggerMinutes = rs.getInt("trigger_minutes");
				archived = rs.getBoolean("is_archived");
				completedAt = rs.getTimestamp("completed_at");
				alarmFired = rs.getBoolean("alarm_fired");
			}
		}

		if (patch.containsKey("title")) title = Values.text(patch, "title", 255, true);
		if (patch.containsKey("description")) description = Values.text(patch, "description", 100_000, false);
		if (patch.containsKey("categoryId")) {
			categoryId = Values.text(patch, "categoryId", 64, false);
			requireVisibleCategory(conn, categoryId, userId);
		}
		if (patch.get("priority") != null) priority = Values.oneOf(patch, "priority", Values.PRIORITIES);
		if (patch.get("alertType") != null) alertType = Values.oneOf(patch, "alertType", Values.ALERT_TYPES);
		if (patch.get("isArchived") != null) archived = Values.bool(patch, "isArchived");

		if (patch.containsKey("deadline")) {
			Timestamp newDeadline = Values.timestamp(patch.get("deadline"), "deadline");
			if (!Objects.equals(newDeadline, deadline)) alarmFired = false;
			deadline = newDeadline;
		}
		if (patch.get("triggerMinutes") != null) {
			int newTrigger = Values.minutes(patch, "triggerMinutes");
			if (newTrigger != triggerMinutes) alarmFired = false;
			triggerMinutes = newTrigger;
		}
		// An explicit value wins (rescheduleTask() sends alarmFired:false).
		if (patch.get("alarmFired") != null) alarmFired = Values.bool(patch, "alarmFired");

		if (patch.get("status") != null) {
			status = Values.oneOf(patch, "status", Values.STATUSES);
			if ("CONCLUIDA".equals(status)) {
				if (completedAt == null) completedAt = Values.now();
			} else {
				completedAt = null;
			}
		}

		String sql = "UPDATE tasktoday_tasks SET title = ?, description = ?, category_id = ?, priority = ?, status = ?, "
				+ "deadline = ?, alert_type = ?, trigger_minutes = ?, is_archived = ?, completed_at = ?, alarm_fired = ?, "
				+ "updated_at = now() WHERE id = ? AND user_id = ? RETURNING " + COLUMNS;
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, title);
			ps.setString(2, description);
			ps.setString(3, categoryId);
			ps.setString(4, priority);
			ps.setString(5, status);
			ps.setTimestamp(6, deadline);
			ps.setString(7, alertType);
			ps.setInt(8, triggerMinutes);
			ps.setBoolean(9, archived);
			ps.setTimestamp(10, completedAt);
			ps.setBoolean(11, alarmFired);
			ps.setString(12, id);
			ps.setString(13, userId);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return toJson(rs);
			}
		}
	}

	/** DELETE /tasks/{id} → 204 */
	public void delete(Exchange exchange) throws SQLException {
		String userId = Http.requireUser(exchange, verifier);
		String id = Http.pathParam(exchange, "id");
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement("DELETE FROM tasktoday_tasks WHERE id = ? AND user_id = ?")) {
			ps.setString(1, id);
			ps.setString(2, userId);
			if (ps.executeUpdate() == 0) throw Http.notFound("Tarefa nao encontrada");
		}
		Http.sendJson(exchange, 204, null);
	}

	/** A task may point at a native category or one of the caller's own. */
	static void requireVisibleCategory(Connection conn, String categoryId, String userId) throws SQLException {
		if (categoryId == null) return;
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT 1 FROM tasktoday_categories WHERE id = ? AND (is_native OR user_id = ?)")) {
			ps.setString(1, categoryId);
			ps.setString(2, userId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) throw Http.badRequest("categoryId inexistente: " + categoryId);
			}
		}
	}

	static Map<String, Object> toJson(ResultSet rs) throws SQLException {
		Map<String, Object> t = new LinkedHashMap<>();
		t.put("id", rs.getString("id"));
		t.put("title", rs.getString("title"));
		t.put("description", rs.getString("description"));
		t.put("categoryId", rs.getString("category_id"));
		t.put("priority", rs.getString("priority"));
		t.put("status", rs.getString("status"));
		t.put("deadline", Values.iso(rs.getTimestamp("deadline")));
		t.put("alertType", rs.getString("alert_type"));
		int trigger = rs.getInt("trigger_minutes");
		t.put("triggerMinutes", rs.wasNull() ? null : trigger);
		t.put("isArchived", rs.getBoolean("is_archived"));
		t.put("completedAt", Values.iso(rs.getTimestamp("completed_at")));
		t.put("alarmFired", rs.getBoolean("alarm_fired"));
		t.put("createdAt", Values.iso(rs.getTimestamp("created_at")));
		t.put("updatedAt", Values.iso(rs.getTimestamp("updated_at")));
		return t;
	}
}
