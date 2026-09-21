package fzlbpms.chamadasonline;

/** Maps to HTTP 404. Message becomes the {"error": "..."} response body. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
