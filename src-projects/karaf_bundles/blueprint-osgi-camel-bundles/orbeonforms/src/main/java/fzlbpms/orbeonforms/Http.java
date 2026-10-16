package fzlbpms.orbeonforms;

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

	/**
	 * This bundle is only reachable through nginx's oauth2-proxy auth_request
	 * gate in front of /api/orbeon/ — there is no bearer
	 * token to verify here, just the forwarded, already-authenticated username.
	 */
	static String requireUser(Exchange exchange) {
		String user = exchange.getMessage().getHeader("X-Forwarded-User", String.class);
		if (user == null || user.isBlank()) {
			// Server-to-server callers (a Camel route, a seeding script) never pass
			// through the browser gate and so have no forwarded user — they present
			// the same shared secret Orbeon's own send() uses. Either is accepted;
			// neither is not.
			String secret = exchange.getMessage().getHeader("X-Orbeon-Shared-Secret", String.class);
			String expected = Env.get("FZL_ORBEON_BRIDGE_SECRET", null);
			if (secret != null && expected != null && constantTimeEquals(secret, expected)) {
				return "system";
			}
			throw unauthorized("Usuário não autenticado.");
		}
		return user;
	}

	private static boolean constantTimeEquals(String a, String b) {
		return java.security.MessageDigest.isEqual(
				a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
				b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}
}
