package fzlbpms.tasktoday;

final class Env {

	private Env() {
	}

	static String get(String name, String fallback) {
		String value = System.getenv(name);
		return (value == null || value.isBlank()) ? fallback : value;
	}
}
