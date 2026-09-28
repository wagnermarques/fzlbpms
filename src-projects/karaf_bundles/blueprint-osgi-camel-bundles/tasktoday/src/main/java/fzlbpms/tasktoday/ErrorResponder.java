package fzlbpms.tasktoday;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** onException target: ApiError becomes its 4xx, anything else a logged 500. */
public class ErrorResponder {

	private static final Logger LOG = LoggerFactory.getLogger(ErrorResponder.class);

	public void handle(Exchange exchange) {
		Throwable caught = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
		// The bean component may wrap what the processor threw.
		for (Throwable t = caught; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof Http.ApiError) {
				Http.sendError(exchange, (Http.ApiError) t);
				return;
			}
		}
		LOG.error("Task Today request failed", caught);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", "server_error");
		body.put("message", "Erro interno no servidor");
		Http.sendJson(exchange, 500, body);
	}
}
