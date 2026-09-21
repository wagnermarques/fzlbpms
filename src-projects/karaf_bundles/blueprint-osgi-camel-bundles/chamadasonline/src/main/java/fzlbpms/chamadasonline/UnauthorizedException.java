package fzlbpms.chamadasonline;

/** Maps to HTTP 401. Message becomes the {"error": "..."} response body. */
public class UnauthorizedException extends RuntimeException {

	public UnauthorizedException(String message) {
		super(message);
	}
}
