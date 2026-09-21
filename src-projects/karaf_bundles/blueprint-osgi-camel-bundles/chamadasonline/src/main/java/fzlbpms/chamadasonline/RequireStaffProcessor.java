package fzlbpms.chamadasonline;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/** Ports app.requireStaff (plugins/auth.ts) — logged-in AND role == STAFF. */
public class RequireStaffProcessor implements Processor {

	private JwtUtil jwtUtil;

	@Override
	public void process(Exchange exchange) {
		JwtUtil.Payload payload = AuthUtil.authenticate(exchange, jwtUtil);
		if (!"STAFF".equals(payload.role)) {
			throw new ForbiddenException("forbidden");
		}
	}

	public void setJwtUtil(JwtUtil jwtUtil) {
		this.jwtUtil = jwtUtil;
	}
}
