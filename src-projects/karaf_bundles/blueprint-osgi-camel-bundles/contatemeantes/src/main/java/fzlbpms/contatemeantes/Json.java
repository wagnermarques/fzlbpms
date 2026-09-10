package fzlbpms.contatemeantes;

final class Json {

	private Json() {
	}

	static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	static String nullableString(String value) {
		return value == null ? "null" : "\"" + escape(value) + "\"";
	}

	static String nullableNumber(Object value) {
		return value == null ? "null" : value.toString();
	}

	static String nullableBoolean(Object value) {
		return value == null ? "null" : value.toString();
	}
}
