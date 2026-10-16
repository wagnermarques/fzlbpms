package fzlbpms.orbeonforms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.camel.Exchange;

/**
 * The shared Orbeon form catalogue: which forms exist, what they are called,
 * and whether Form Builder can open them. Any app that needs to attach an
 * Orbeon form to something (today the BPMN modeler's user tasks) reads this
 * instead of hard-coding form names.
 *
 * Creating and editing a form visually stays in Orbeon's own Form Builder —
 * see the README for why we deliberately do not generate its format here.
 */
public class FormsProcessor {

	private OrbeonDb orbeonDb;

	public void setOrbeonDb(OrbeonDb orbeonDb) {
		this.orbeonDb = orbeonDb;
	}

	/**
	 * A form is editable in Form Builder only when its builder document holds a
	 * full form definition (root {@code xh:html}). Forms seeded as a bare
	 * {@code <form>} summary stub still run fine, so they are listed — just
	 * flagged, so callers can disable an "edit" affordance that would 500.
	 */
	private static final String LIST_SQL =
			"WITH builder AS ("
			+ "  SELECT DISTINCT ON (app_x, form_x) app_x, form_x, document_id, root_elem"
			+ "  FROM ("
			+ "    SELECT (xpath('//application-name/text()', d.xml))[1]::text AS app_x,"
			+ "           (xpath('//form-name/text()',        d.xml))[1]::text AS form_x,"
			+ "           d.document_id,"
			+ "           (xpath('local-name(/*)', d.xml))[1]::text            AS root_elem,"
			+ "           d.last_modified_time"
			+ "    FROM orbeon_form_data d"
			+ "    WHERE d.app = 'orbeon' AND d.form = 'builder' AND d.deleted = 'N' AND d.draft = 'N'"
			+ "  ) z"
			+ "  ORDER BY app_x, form_x, (root_elem = 'html') DESC, last_modified_time DESC"
			+ ") "
			+ "SELECT DISTINCT ON (fd.app, fd.form) "
			+ "       fd.app, fd.form,"
			+ "       (xpath('//title/text()',       fd.form_metadata::xml))[1]::text AS title,"
			+ "       (xpath('//description/text()', fd.form_metadata::xml))[1]::text AS description,"
			+ "       b.document_id AS builder_doc,"
			+ "       COALESCE(b.root_elem = 'html', false) AS editable "
			+ "FROM orbeon_form_definition fd "
			+ "LEFT JOIN builder b ON b.app_x = fd.app AND b.form_x = fd.form "
			+ "WHERE fd.deleted = 'N' "
			+ "ORDER BY fd.app, fd.form, fd.last_modified_time DESC";

	public void listForms(Exchange exchange) throws Exception {
		Http.requireUser(exchange);
		List<Map<String, Object>> forms = new ArrayList<>();
		try (Connection conn = orbeonDb.getConnection();
			 PreparedStatement ps = conn.prepareStatement(LIST_SQL);
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				forms.add(toSummary(rs));
			}
		}
		Http.sendJson(exchange, 200, forms);
	}

	public void getForm(Exchange exchange) throws Exception {
		Http.requireUser(exchange);
		String app = requirePathParam(exchange, "app");
		String form = requirePathParam(exchange, "form");

		String sql = "SELECT xml::text AS xml, form_metadata FROM orbeon_form_definition "
				+ "WHERE app = ? AND form = ? AND deleted = 'N' "
				+ "ORDER BY form_version DESC, last_modified_time DESC LIMIT 1";
		try (Connection conn = orbeonDb.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, app);
			ps.setString(2, form);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) throw Http.notFound("Formulário não encontrado: " + app + "/" + form);
				Map<String, Object> d = new LinkedHashMap<>();
				d.put("app", app);
				d.put("form", form);
				d.put("formKey", app + "/" + form);
				d.put("xml", rs.getString("xml"));
				d.put("metadata", rs.getString("form_metadata"));
				Http.sendJson(exchange, 200, d);
			}
		}
	}

	/**
	 * Publishes a form definition — the runtime equivalent of
	 * src-projects/orbeon-forms/publish_forms.py, so a form can be seeded by a
	 * process or a script without shelling into the database container.
	 *
	 * The form is published for Form Runner only. It will NOT be editable in
	 * Form Builder: that needs a builder document in Form Builder's internal
	 * format, which we deliberately do not synthesise (see README).
	 */
	public void publishForm(Exchange exchange) throws Exception {
		String user = Http.requireUser(exchange);
		Map<String, Object> req = Json.parseObject(exchange.getMessage().getBody(String.class));

		String app = str(req.get("app"));
		String form = str(req.get("form"));
		String xml = str(req.get("xml"));
		if (app == null || form == null || xml == null) {
			throw Http.badRequest("Campos obrigatórios: app, form, xml");
		}
		if (!isSafeName(app) || !isSafeName(form)) {
			throw Http.badRequest("app e form devem conter apenas letras, números, '-' e '_'");
		}

		String metadata = metadataFor(xml, app, form, str(req.get("title")), str(req.get("description")));

		try (Connection conn = orbeonDb.getConnection()) {
			conn.setAutoCommit(false);
			try {
				exec(conn, "DELETE FROM orbeon_form_definition WHERE app = ? AND form = ?", app, form);
				exec(conn, "DELETE FROM orbeon_form_definition_attach WHERE app = ? AND form = ?", app, form);

				try (PreparedStatement ps = conn.prepareStatement(
						"INSERT INTO orbeon_form_definition (created, last_modified_time, last_modified_by,"
						+ " app, form, form_version, form_metadata, deleted, xml)"
						+ " VALUES (NOW(), NOW(), ?, ?, ?, 1, ?, 'N', XMLPARSE(DOCUMENT ?))")) {
					ps.setString(1, user);
					ps.setString(2, app);
					ps.setString(3, form);
					ps.setString(4, metadata);
					ps.setString(5, xml);
					ps.executeUpdate();
				}

				try (PreparedStatement ps = conn.prepareStatement(
						"INSERT INTO orbeon_form_definition_attach (created, last_modified_time, last_modified_by,"
						+ " app, form, form_version, deleted, file_name, file_content)"
						+ " VALUES (NOW(), NOW(), ?, ?, ?, 1, 'N', 'form.xhtml', convert_to(?, 'UTF8'))")) {
					ps.setString(1, user);
					ps.setString(2, app);
					ps.setString(3, form);
					ps.setString(4, xml);
					ps.executeUpdate();
				}
				conn.commit();
			} catch (Exception e) {
				conn.rollback();
				throw e;
			}
		}

		Map<String, Object> res = new LinkedHashMap<>();
		res.put("app", app);
		res.put("form", form);
		res.put("formKey", app + "/" + form);
		res.put("editable", false);
		res.put("runUrl", "/orbeon/fr/" + app + "/" + form + "/new");
		Http.sendJson(exchange, 201, res);
	}

	// ---------- helpers ----------

	private static Map<String, Object> toSummary(ResultSet rs) throws Exception {
		String app = rs.getString("app");
		String form = rs.getString("form");
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("app", app);
		f.put("form", form);
		// The value stored in the BPMN as flowable:formKey.
		f.put("formKey", app + "/" + form);
		f.put("title", rs.getString("title"));
		f.put("description", rs.getString("description"));
		f.put("builderDocId", rs.getString("builder_doc"));
		f.put("editable", rs.getBoolean("editable"));
		return f;
	}

	private static final Pattern METADATA = Pattern.compile("<metadata>[\\s\\S]*?</metadata>");
	private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

	/** Reuses the form's own &lt;metadata&gt; when present, like publish_forms.py does. */
	private static String metadataFor(String xml, String app, String form, String title, String description) {
		Matcher m = METADATA.matcher(xml);
		if (m.find()) {
			String meta = m.group();
			return meta.contains("<available>")
					? meta
					: meta.replace("</metadata>", "    <available>true</available>\n</metadata>");
		}
		return "<metadata>\n"
				+ "    <application-name>" + app + "</application-name>\n"
				+ "    <form-name>" + form + "</form-name>\n"
				+ "    <title xml:lang=\"pt\">" + xmlEscape(title != null ? title : form) + "</title>\n"
				+ "    <description xml:lang=\"pt\">" + xmlEscape(description != null ? description : "") + "</description>\n"
				+ "    <version-comment>1.0.0</version-comment>\n"
				+ "    <available>true</available>\n"
				+ "</metadata>";
	}

	private static String xmlEscape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static boolean isSafeName(String s) {
		return SAFE_NAME.matcher(s).matches();
	}

	private static void exec(Connection conn, String sql, String a, String b) throws Exception {
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, a);
			ps.setString(2, b);
			ps.executeUpdate();
		}
	}

	private static String str(Object o) {
		if (o == null) return null;
		String s = String.valueOf(o).trim();
		return s.isEmpty() ? null : s;
	}

	private static String requirePathParam(Exchange exchange, String name) {
		String v = exchange.getMessage().getHeader(name, String.class);
		if (v == null || v.isBlank()) throw Http.badRequest("Parâmetro " + name + " é obrigatório");
		return v;
	}
}
