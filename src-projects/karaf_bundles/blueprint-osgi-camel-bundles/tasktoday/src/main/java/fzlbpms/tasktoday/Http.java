package fzlbpms.tasktoday;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

/** Request/response helpers shared by the Task Today processors. */
public final class Http {

	private Http() {
	}

	/** Thrown by processors to answer a 4xx with {"error","message"}. */
	public static final class ApiError extends RuntimeException {
		final int status;
		final String code;

		ApiError(int status, String code, String message) {
			super(message);
			this.status = status;
			this.code = code;
		}
	}

	static ApiError badRequest(String message) {
		return new ApiError(400, "bad_request", message);
	}

	static ApiError notFound(String message) {
		return new ApiError(404, "not_found", message);
	}

	/**
	 * Replaces the whole message with a JSON response. Request headers are
	 * dropped first: the Jetty consumer would otherwise echo them (Authorization
	 * included) back as response headers.
	 */
	static void sendJson(Exchange exchange, int status, Object body) {
		Message msg = exchange.getMessage();
		msg.removeHeaders("*");
		msg.setHeader(Exchange.HTTP_RESPONSE_CODE, status);
		msg.setHeader(Exchange.CONTENT_TYPE, "application/json; charset=UTF-8");
		msg.setHeader("Access-Control-Allow-Origin", "*");
		msg.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
		msg.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept");
		msg.setBody(body == null ? "" : Json.serialize(body));
	}

	static void sendError(Exchange exchange, ApiError e) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", e.code);
		body.put("message", e.getMessage());
		sendJson(exchange, e.status, body);
	}

	/** Realm role that may manage the shared native categories. */
	static final String ADMIN_ROLE = "tasktoday-admin";

	/** The authenticated caller: Keycloak "sub" and whether it holds ADMIN_ROLE. */
	static final class Caller {
		final String userId;
		final boolean admin;

		Caller(String userId, boolean admin) {
			this.userId = userId;
			this.admin = admin;
		}
	}

	/** Keycloak "sub" of the caller, or ApiError 401. */
	static String requireUser(Exchange exchange, KeycloakJwtVerifier verifier) {
		return requireCaller(exchange, verifier).userId;
	}

	static Caller requireCaller(Exchange exchange, KeycloakJwtVerifier verifier) {
		Map<String, Object> claims = verifier.verify(exchange.getIn().getHeader("Authorization", String.class));
		if (claims == null) {
			throw new ApiError(401, "unauthorized", "Token Bearer do Keycloak ausente, invalido ou expirado");
		}
		return new Caller((String) claims.get("sub"), hasRealmRole(claims, ADMIN_ROLE));
	}

	/** Keycloak puts realm roles in the access token as {"realm_access": {"roles": [...]}}. */
	static boolean hasRealmRole(Map<String, Object> claims, String role) {
		Object realmAccess = claims.get("realm_access");
		if (!(realmAccess instanceof Map)) return false;
		Object roles = ((Map<?, ?>) realmAccess).get("roles");
		return roles instanceof List && ((List<?>) roles).contains(role);
	}

	static Map<String, Object> jsonBody(Exchange exchange) {
		String raw = exchange.getIn().getBody(String.class);
		if (raw == null || raw.isBlank()) return new LinkedHashMap<>();
		Object parsed;
		try {
			parsed = Json.parse(raw);
		} catch (RuntimeException e) {
			throw badRequest("JSON invalido");
		}
		if (!(parsed instanceof Map)) throw badRequest("O corpo deve ser um objeto JSON");
		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) parsed;
		return map;
	}

	/** Parsed CamelHttpQuery; last value wins for repeated keys. */
	static Map<String, String> query(Exchange exchange) {
		Map<String, String> params = new LinkedHashMap<>();
		String raw = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
		if (raw == null || raw.isEmpty()) return params;
		for (String pair : raw.split("&")) {
			if (pair.isEmpty()) continue;
			int eq = pair.indexOf('=');
			String key = eq < 0 ? pair : pair.substring(0, eq);
			String value = eq < 0 ? "" : pair.substring(eq + 1);
			params.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
		}
		return params;
	}

	static String pathParam(Exchange exchange, String name) {
		String value = exchange.getIn().getHeader(name, String.class);
		if (value == null || value.isBlank()) throw badRequest("Parametro de caminho ausente: " + name);
		return value;
	}
}
