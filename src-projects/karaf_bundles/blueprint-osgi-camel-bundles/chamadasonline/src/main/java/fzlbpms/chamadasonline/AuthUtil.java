package fzlbpms.chamadasonline;

import org.apache.camel.Exchange;

/** Shared Bearer-token extraction, used by AuthenticateProcessor/RequireStaffProcessor. */
final class AuthUtil {

	static final String USER_ID_PROPERTY = "chamadasonline.userId";
	static final String USER_ROLE_PROPERTY = "chamadasonline.userRole";

	private AuthUtil() {
	}

	/** Ports app.authenticate from plugins/auth.ts. */
	static JwtUtil.Payload authenticate(Exchange exchange, JwtUtil jwtUtil) {
		String authHeader = exchange.getIn().getHeader("Authorization", String.class);
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			throw new UnauthorizedException("unauthorized");
		}
		JwtUtil.Payload payload = jwtUtil.verify(authHeader.substring("Bearer ".length()));
		if (payload == null) {
			throw new UnauthorizedException("unauthorized");
		}
		exchange.setProperty(USER_ID_PROPERTY, payload.sub);
		exchange.setProperty(USER_ROLE_PROPERTY, payload.role);
		return payload;
	}

	static String requireUserId(Exchange exchange) {
		String userId = exchange.getProperty(USER_ID_PROPERTY, String.class);
		if (userId == null) {
			throw new UnauthorizedException("unauthorized");
		}
		return userId;
	}

	static String requireUserRole(Exchange exchange) {
		String role = exchange.getProperty(USER_ROLE_PROPERTY, String.class);
		if (role == null) {
			throw new UnauthorizedException("unauthorized");
		}
		return role;
	}
}
