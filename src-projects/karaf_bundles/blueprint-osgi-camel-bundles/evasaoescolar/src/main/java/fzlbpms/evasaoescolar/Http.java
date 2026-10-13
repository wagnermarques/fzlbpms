package fzlbpms.evasaoescolar;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

public final class Http {

	private Http() {
	}

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

	static ApiError unauthorized(String message) {
		return new ApiError(401, "unauthorized", message);
	}

	static ApiError forbidden(String message) {
		return new ApiError(403, "forbidden", message);
	}

	static ApiError notFound(String message) {
		return new ApiError(404, "not_found", message);
	}

	static ApiError serverError(String message) {
		return new ApiError(500, "server_error", message);
	}

	static void sendJson(Exchange exchange, int status, Object body) {
		Message msg = exchange.getMessage();
		msg.removeHeaders("*");
		msg.setHeader(Exchange.HTTP_RESPONSE_CODE, status);
		msg.setHeader(Exchange.CONTENT_TYPE, "application/json; charset=UTF-8");
		msg.setHeader("Access-Control-Allow-Origin", "*");
		msg.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
		msg.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept");
		msg.setBody(body == null ? "" : Json.serialize(body));
	}

	static void sendError(Exchange exchange, ApiError e) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", e.code);
		body.put("message", e.getMessage());
		sendJson(exchange, e.status, body);
	}

	static Map<String, Object> requireUser(Exchange exchange, KeycloakJwtVerifier verifier) {
		String auth = exchange.getMessage().getHeader("Authorization", String.class);
		Map<String, Object> claims = verifier != null ? verifier.verify(auth) : null;
		if (claims == null) {
			throw unauthorized("Token de acesso inválido ou expirado.");
		}
		return claims;
	}
}
