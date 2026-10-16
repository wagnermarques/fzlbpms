package fzlbpms.orbeonforms;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin client over Flowable's process REST API — only the handful of calls the
 * generic task renderer needs. Credentials match the ones the existing bridge
 * routes already use (Basic admin:test against fzl-flowable-ui).
 */
public class Flowable {

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private final String base = Env.get("FLOWABLE_API_BASE",
			"http://fzl-flowable-ui:8080/flowable-ui/process-api");
	private final String authHeader = "Basic " + Base64.getEncoder().encodeToString(
			(Env.get("FLOWABLE_API_USER", "admin") + ":" + Env.get("FLOWABLE_API_PASSWORD", "test"))
					.getBytes(StandardCharsets.UTF_8));

	public Map<String, Object> getTask(String taskId) throws Exception {
		HttpResponse<String> res = send(request("/runtime/tasks/" + enc(taskId)).GET());
		if (res.statusCode() == 404) throw Http.notFound("Tarefa não encontrada: " + taskId);
		requireOk(res, "consultar a tarefa");
		return Json.parseObject(res.body());
	}

	/** Tasks pending for one assignee, newest first, as returned by Flowable. */
	public List<Map<String, Object>> listTasksForAssignee(String assignee) throws Exception {
		HttpResponse<String> res = send(request(
				"/runtime/tasks?size=200&assignee=" + enc(assignee)).GET());
		requireOk(res, "listar as tarefas");
		Object data = Json.parseObject(res.body()).get("data");
		List<Map<String, Object>> tasks = new ArrayList<>();
		if (data instanceof List) {
			for (Object o : (List<?>) data) {
				if (o instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> t = (Map<String, Object>) o;
					tasks.add(t);
				}
			}
		}
		return tasks;
	}

	/** Process variables as a flat name -&gt; value map. */
	public Map<String, Object> getProcessVariables(String processInstanceId) throws Exception {
		HttpResponse<String> res = send(request(
				"/runtime/process-instances/" + enc(processInstanceId) + "/variables").GET());
		if (res.statusCode() == 404) return new LinkedHashMap<>();
		requireOk(res, "ler as variáveis do processo");
		Map<String, Object> vars = new LinkedHashMap<>();
		for (Object o : Json.parseArray(res.body())) {
			if (o instanceof Map) {
				Map<?, ?> v = (Map<?, ?>) o;
				Object name = v.get("name");
				if (name != null) vars.put(String.valueOf(name), v.get("value"));
			}
		}
		return vars;
	}

	public void completeTask(String taskId, Map<String, Object> variables) throws Exception {
		List<Map<String, Object>> vars = new ArrayList<>();
		for (Map.Entry<String, Object> e : variables.entrySet()) {
			Map<String, Object> v = new LinkedHashMap<>();
			v.put("name", e.getKey());
			v.put("value", e.getValue());
			vars.add(v);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("action", "complete");
		body.put("variables", vars);

		HttpResponse<String> res = send(request("/runtime/tasks/" + enc(taskId))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(Json.serialize(body), StandardCharsets.UTF_8)));
		if (res.statusCode() == 404) throw Http.notFound("Tarefa não encontrada ou já concluída: " + taskId);
		requireOk(res, "concluir a tarefa");
	}

	// ---------- helpers ----------

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder()
				.uri(URI.create(base + path))
				.timeout(Duration.ofSeconds(30))
				.header("Authorization", authHeader)
				.header("Accept", "application/json");
	}

	private HttpResponse<String> send(HttpRequest.Builder b) throws Exception {
		return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	private static void requireOk(HttpResponse<String> res, String what) {
		int s = res.statusCode();
		if (s < 200 || s > 299) {
			throw Http.serverError("Falha ao " + what + " no Flowable (HTTP " + s + "): " + res.body());
		}
	}

	private static String enc(String s) {
		return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}
}
