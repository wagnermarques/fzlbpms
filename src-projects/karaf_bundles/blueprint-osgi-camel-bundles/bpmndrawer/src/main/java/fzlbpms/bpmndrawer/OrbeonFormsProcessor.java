package fzlbpms.bpmndrawer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.Exchange;

/**
 * Lists the Orbeon forms available to attach to a BPMN user task, so the
 * modeler can offer a picker instead of a free-text formKey.
 *
 * Only a listing is exposed: creating and editing a form happens in Orbeon's
 * own Form Builder (the modeler just links to it), which keeps us out of the
 * business of generating Form Builder's internal XHTML format.
 */
public class OrbeonFormsProcessor {

	private OrbeonDb orbeonDb;

	public void setOrbeonDb(OrbeonDb orbeonDb) {
		this.orbeonDb = orbeonDb;
	}

	/**
	 * A form is editable in Form Builder only when its builder document holds a
	 * full form definition (root {@code xh:html}). Forms seeded by
	 * publish_forms.py store a bare {@code <form>} summary stub instead, which
	 * Form Builder cannot open — those are still perfectly usable at runtime,
	 * so they are listed, just flagged as not editable.
	 */
	private static final String SQL =
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
			 PreparedStatement ps = conn.prepareStatement(SQL);
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
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
				forms.add(f);
			}
		}
		Http.sendJson(exchange, 200, forms);
	}
}
