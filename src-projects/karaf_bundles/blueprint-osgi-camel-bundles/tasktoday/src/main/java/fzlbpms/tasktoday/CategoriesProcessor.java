package fzlbpms.tasktoday;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.camel.Exchange;

/**
 * /categories endpoints: the shared native categories plus the caller's own.
 *
 * Native categories (user_id NULL, seen by everyone) are read-only, except for
 * callers holding the realm role tasktoday-admin, who may create, edit and
 * delete them.
 */
public class CategoriesProcessor {

	private static final String COLUMNS = "id, name, color, icon, is_native, created_at";

	private Db db;
	private KeycloakJwtVerifier verifier;

	public void setDb(Db db) {
		this.db = db;
	}

	public void setVerifier(KeycloakJwtVerifier verifier) {
		this.verifier = verifier;
	}

	/** GET /categories — natives first, then the caller's in creation order. */
	public void list(Exchange exchange) throws SQLException {
		String userId = Http.requireUser(exchange, verifier);
		List<Map<String, Object>> categories = new ArrayList<>();
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement("SELECT " + COLUMNS + " FROM tasktoday_categories "
						+ "WHERE is_native OR user_id = ? ORDER BY is_native DESC, created_at, name")) {
			ps.setString(1, userId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) categories.add(toJson(rs));
			}
		}
		Http.sendJson(exchange, 200, categories);
	}

	/** POST /categories → 201. "isNative": true (admins only) creates a shared category. */
	public void create(Exchange exchange) throws SQLException {
		Http.Caller caller = Http.requireCaller(exchange, verifier);
		Map<String, Object> body = Http.jsonBody(exchange);

		boolean isNative = Boolean.TRUE.equals(Values.bool(body, "isNative"));
		if (isNative) requireAdmin(caller);
		String id = Values.idOrNew(body, "cat-");
		String name = Values.text(body, "name", 120, true);
		String color = Objects.requireNonNullElse(Values.text(body, "color", 32, false), "#6750a4");
		String icon = Objects.requireNonNullElse(Values.text(body, "icon", 64, false), "label");

		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement("INSERT INTO tasktoday_categories "
						+ "(id, user_id, name, color, icon, is_native) VALUES (?, ?, ?, ?, ?, ?) RETURNING " + COLUMNS)) {
			ps.setString(1, id);
			ps.setString(2, isNative ? null : caller.userId);
			ps.setString(3, name);
			ps.setString(4, color);
			ps.setString(5, icon);
			ps.setBoolean(6, isNative);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				Http.sendJson(exchange, 201, toJson(rs));
			}
		} catch (SQLException e) {
			if ("23505".equals(e.getSQLState())) {
				throw new Http.ApiError(409, "conflict", "Ja existe uma categoria com o id " + id);
			}
			throw e;
		}
	}

	/**
	 * PUT /categories/{id} → 200. Partial update of name, color and icon. Own
	 * categories for everyone; native ones for admins only (403 otherwise).
	 */
	public void update(Exchange exchange) throws SQLException {
		Http.Caller caller = Http.requireCaller(exchange, verifier);
		String id = Http.pathParam(exchange, "id");
		Map<String, Object> patch = Http.jsonBody(exchange);

		try (Connection conn = db.getConnection()) {
			requireWritable(conn, caller, id);
			String sql = "UPDATE tasktoday_categories SET "
					+ "name = COALESCE(?, name), color = COALESCE(?, color), icon = COALESCE(?, icon) "
					+ "WHERE id = ? RETURNING " + COLUMNS;
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, patch.containsKey("name") ? Values.text(patch, "name", 120, true) : null);
				ps.setString(2, Values.text(patch, "color", 32, false));
				ps.setString(3, Values.text(patch, "icon", 64, false));
				ps.setString(4, id);
				try (ResultSet rs = ps.executeQuery()) {
					rs.next();
					Http.sendJson(exchange, 200, toJson(rs));
				}
			}
		}
	}

	/**
	 * DELETE /categories/{id} → 204. Native categories: admins only (403
	 * otherwise). Tasks that used the category keep existing with categoryId
	 * null (FK ON DELETE SET NULL) — for a native one, that is every user's tasks.
	 */
	public void delete(Exchange exchange) throws SQLException {
		Http.Caller caller = Http.requireCaller(exchange, verifier);
		String id = Http.pathParam(exchange, "id");
		try (Connection conn = db.getConnection()) {
			requireWritable(conn, caller, id);
			try (PreparedStatement ps = conn.prepareStatement("DELETE FROM tasktoday_categories WHERE id = ?")) {
				ps.setString(1, id);
				ps.executeUpdate();
			}
		}
		Http.sendJson(exchange, 204, null);
	}

	/**
	 * 404 unless the category is the caller's own or native; 403 for a native
	 * category when the caller is not an admin.
	 */
	private static void requireWritable(Connection conn, Http.Caller caller, String id) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT is_native FROM tasktoday_categories WHERE id = ? AND (is_native OR user_id = ?)")) {
			ps.setString(1, id);
			ps.setString(2, caller.userId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) throw Http.notFound("Categoria nao encontrada");
				if (rs.getBoolean(1) && !caller.admin) {
					throw new Http.ApiError(403, "forbidden",
							"Categorias nativas so podem ser alteradas por " + Http.ADMIN_ROLE);
				}
			}
		}
	}

	private static void requireAdmin(Http.Caller caller) {
		if (!caller.admin) {
			throw new Http.ApiError(403, "forbidden", "Somente " + Http.ADMIN_ROLE + " pode criar categorias nativas");
		}
	}

	private static Map<String, Object> toJson(ResultSet rs) throws SQLException {
		Map<String, Object> c = new LinkedHashMap<>();
		c.put("id", rs.getString("id"));
		c.put("name", rs.getString("name"));
		c.put("color", rs.getString("color"));
		c.put("icon", rs.getString("icon"));
		c.put("isNative", rs.getBoolean("is_native"));
		c.put("createdAt", Values.iso(rs.getTimestamp("created_at")));
		return c;
	}
}
