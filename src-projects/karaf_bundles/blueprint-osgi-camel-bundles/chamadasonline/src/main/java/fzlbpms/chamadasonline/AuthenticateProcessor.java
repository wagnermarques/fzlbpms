package fzlbpms.chamadasonline;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/** Ports app.authenticate (plugins/auth.ts) — any logged-in user, either role. */
public class AuthenticateProcessor implements Processor {

	private JwtUtil jwtUtil;

	@Override
	public void process(Exchange exchange) {
		AuthUtil.authenticate(exchange, jwtUtil);
	}

	public void setJwtUtil(JwtUtil jwtUtil) {
		this.jwtUtil = jwtUtil;
	}
}
