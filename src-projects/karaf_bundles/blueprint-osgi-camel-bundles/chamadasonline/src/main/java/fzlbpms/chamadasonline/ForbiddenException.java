package fzlbpms.chamadasonline;

/** Maps to HTTP 403. Message becomes the {"error": "..."} response body. */
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}
}
