package fzlbpms.orbeonforms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The generic Orbeon renderer for in-flight Flowable tasks.
 *
 * Opening a task seeds an Orbeon data document whose id IS the Flowable task
 * id, then redirects to Form Runner. That identity is what makes a single,
 * form-agnostic submit action possible: the form posts back to
 * .../tasks/{fr:document-id()}/complete, so the task being completed is carried
 * in the URL. Orbeon's send() runs server-to-server with no caller identity, so
 * without this there would be nothing to correlate a submission with.
 */
public class TasksProcessor {

	private static final Logger LOG = LoggerFactory.getLogger(TasksProcessor.class);

	private OrbeonDb orbeonDb;
	private Flowable flowable;

	public void setOrbeonDb(OrbeonDb orbeonDb) {
		this.orbeonDb = orbeonDb;
	}

	public void setFlowable(Flowable flowable) {
		this.flowable = flowable;
	}

	/** The signed-in user's pending tasks, annotated with whether a form exists. */
	public void listMyTasks(Exchange exchange) throws Exception {
		String user = Http.requireUser(exchange);
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> t : flowable.listTasksForAssignee(user)) {
			String formKey = str(t.get("formKey"));
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", t.get("id"));
			item.put("name", t.get("name"));
			item.put("description", t.get("description"));
			item.put("createTime", t.get("createTime"));
			item.put("dueDate", t.get("dueDate"));
			item.put("processInstanceId", t.get("processInstanceId"));
			item.put("formKey", formKey);
			boolean hasForm = parseFormKey(formKey) != null;
			item.put("hasOrbeonForm", hasForm);
			// Where the UI should send the user for this task.
			item.put("openUrl", hasForm
					? "/api/orbeon/tasks/" + t.get("id") + "/open"
					: "/flowable-ui/workflow/#/task/" + t.get("id"));
			out.add(item);
		}
		Http.sendJson(exchange, 200, out);
	}

	/**
	 * Seeds the Orbeon document for this task and redirects to Form Runner.
	 * Tasks without an Orbeon formKey fall back to Flowable's own task UI, which
	 * is what every task used before this existed.
	 */
	public void openTask(Exchange exchange) throws Exception {
		Http.requireUser(exchange);
		String taskId = requirePathParam(exchange, "taskId");

		Map<String, Object> task = flowable.getTask(taskId);
		String formKey = str(task.get("formKey"));
		String[] appForm = parseFormKey(formKey);
		if (appForm == null) {
			redirect(exchange, "/flowable-ui/workflow/#/task/" + taskId);
			return;
		}

		String app = appForm[0];
		String form = appForm[1];
		String definition = loadFormDefinition(app, form);
		if (definition == null) {
			throw Http.notFound("Formulário '" + formKey + "' da tarefa não está publicado no Orbeon.");
		}

		Map<String, Object> variables = new LinkedHashMap<>();
		String processInstanceId = str(task.get("processInstanceId"));
		if (processInstanceId != null) {
			variables.putAll(flowable.getProcessVariables(processInstanceId));
		}

		String dataXml = FormData.buildPrefilled(definition, variables);
		seedDocument(app, form, taskId, dataXml);

		LOG.info("Tarefa {} aberta no formulário {}/{} ({} variáveis disponíveis para preenchimento)",
				taskId, app, form, variables.size());
		redirect(exchange, "/orbeon/fr/" + app + "/" + form + "/edit/" + taskId);
	}

	/**
	 * Completes the task from the submitted form. Called by Orbeon's send(),
	 * which carries the shared secret but no user — the task id comes from the
	 * path, having been the Orbeon document id all along.
	 */
	public void completeTask(Exchange exchange) throws Exception {
		Http.requireUser(exchange);
		String taskId = requirePathParam(exchange, "taskId");
		String body = exchange.getMessage().getBody(String.class);

		Map<String, Object> variables = (body != null && body.trim().startsWith("<"))
				? FormData.toVariables(body)
				: flatten(Json.parseObject(body));

		if (variables.isEmpty()) {
			throw Http.badRequest("Submissão sem campos — nada a gravar na tarefa.");
		}

		flowable.completeTask(taskId, variables);
		deleteDocument(taskId);
		LOG.info("Tarefa {} concluída com {} variáveis vindas do formulário Orbeon.", taskId, variables.size());

		Map<String, Object> res = new LinkedHashMap<>();
		res.put("status", "ok");
		res.put("taskId", taskId);
		res.put("variables", variables.size());
		Http.sendJson(exchange, 200, res);
	}

	// ---------- Orbeon document plumbing ----------

	private String loadFormDefinition(String app, String form) throws Exception {
		String sql = "SELECT xml::text AS xml FROM orbeon_form_definition "
				+ "WHERE app = ? AND form = ? AND deleted = 'N' "
				+ "ORDER BY form_version DESC, last_modified_time DESC LIMIT 1";
		try (Connection conn = orbeonDb.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, app);
			ps.setString(2, form);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getString("xml") : null;
			}
		}
	}

	/**
	 * Writes the data document Form Runner will open. Re-opening a task replaces
	 * the previous seed, so reopening always reflects current process variables
	 * rather than a stale snapshot.
	 */
	private void seedDocument(String app, String form, String taskId, String dataXml) throws Exception {
		try (Connection conn = orbeonDb.getConnection()) {
			conn.setAutoCommit(false);
			try {
				deleteDocument(conn, taskId);
				try (PreparedStatement ps = conn.prepareStatement(
						"INSERT INTO orbeon_form_data (created, last_modified_time, last_modified_by, username,"
						+ " app, form, form_version, document_id, deleted, draft, xml)"
						+ " VALUES (NOW(), NOW(), 'flowable', 'flowable', ?, ?, 1, ?, 'N', 'N',"
						+ " XMLPARSE(DOCUMENT ?))")) {
					ps.setString(1, app);
					ps.setString(2, form);
					ps.setString(3, taskId);
					ps.setString(4, dataXml);
					ps.executeUpdate();
				}
				try (PreparedStatement ps = conn.prepareStatement(
						"INSERT INTO orbeon_i_current (data_id, document_id, draft, app, form, form_version,"
						+ " last_modified_time, created)"
						+ " VALUES (currval('orbeon_form_data_id_seq'), ?, 'N', ?, ?, 1, NOW(), NOW())")) {
					ps.setString(1, taskId);
					ps.setString(2, app);
					ps.setString(3, form);
					ps.executeUpdate();
				}
				conn.commit();
			} catch (Exception e) {
				conn.rollback();
				throw e;
			}
		}
	}

	/** Drops the seeded document once the task is done, so Orbeon keeps no orphans. */
	private void deleteDocument(String taskId) throws Exception {
		try (Connection conn = orbeonDb.getConnection()) {
			deleteDocument(conn, taskId);
		}
	}

	private static void deleteDocument(Connection conn, String taskId) throws Exception {
		for (String sql : new String[] {
				"DELETE FROM orbeon_i_current WHERE document_id = ?",
				"DELETE FROM orbeon_form_data_attach WHERE document_id = ?",
				"DELETE FROM orbeon_form_data WHERE document_id = ?" }) {
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, taskId);
				ps.executeUpdate();
			}
		}
	}

	// ---------- helpers ----------

	/** {@code app/form} — anything else (an expression, a Flowable form id) is not ours. */
	static String[] parseFormKey(String formKey) {
		if (formKey == null) return null;
		String k = formKey.trim();
		if (k.isEmpty() || k.contains("${") || k.contains(" ")) return null;
		int slash = k.indexOf('/');
		if (slash <= 0 || slash == k.length() - 1) return null;
		String app = k.substring(0, slash);
		String form = k.substring(slash + 1);
		if (form.indexOf('/') >= 0) return null;
		return new String[] { app, form };
	}

	/** Orbeon's send(content="json") posts the instance tree; flatten it to leaves. */
	private static Map<String, Object> flatten(Map<String, Object> tree) {
		Map<String, Object> out = new LinkedHashMap<>();
		flattenInto(tree, out);
		return out;
	}

	@SuppressWarnings("unchecked")
	private static void flattenInto(Map<String, Object> node, Map<String, Object> out) {
		for (Map.Entry<String, Object> e : node.entrySet()) {
			Object v = e.getValue();
			if (v instanceof Map) {
				flattenInto((Map<String, Object>) v, out);
			} else if (!(v instanceof List)) {
				out.put(e.getKey(), v);
			}
		}
	}

	private static void redirect(Exchange exchange, String location) {
		exchange.getMessage().removeHeaders("*");
		exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 302);
		exchange.getMessage().setHeader("Location", location);
		exchange.getMessage().setBody("");
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
