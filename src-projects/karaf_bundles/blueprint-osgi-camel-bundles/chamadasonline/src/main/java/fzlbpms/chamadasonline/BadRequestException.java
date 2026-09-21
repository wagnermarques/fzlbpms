package fzlbpms.chamadasonline;

/** Maps to HTTP 400. Message becomes the {"error": "..."} response body. */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}
}
