package fzlbpms.chamadas;

import java.util.*;

public final class Json {

	private Json() {
	}

	public static String escape(String value) {
		if (value == null) return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"': sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\b': sb.append("\\b"); break;
				case '\f': sb.append("\\f"); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				default:
					if (c < ' ') {
						String hex = "000" + Integer.toHexString(c);
						sb.append("\\u").append(hex.substring(hex.length() - 4));
					} else {
						sb.append(c);
					}
			}
		}
		return sb.toString();
	}

	public static String quote(String value) {
		return value == null ? "null" : "\"" + escape(value) + "\"";
	}

	public static String nullableString(String value) {
		return value == null ? "null" : "\"" + escape(value) + "\"";
	}

	public static String serialize(Object obj) {
		if (obj == null) return "null";
		if (obj instanceof String) return quote((String) obj);
		if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
		if (obj instanceof Map) {
			Map<?, ?> map = (Map<?, ?>) obj;
			StringBuilder sb = new StringBuilder("{");
			boolean first = true;
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (!first) sb.append(",");
				sb.append(quote(String.valueOf(entry.getKey()))).append(":");
				sb.append(serialize(entry.getValue()));
				first = false;
			}
			sb.append("}");
			return sb.toString();
		}
		if (obj instanceof Collection) {
			Collection<?> col = (Collection<?>) obj;
			StringBuilder sb = new StringBuilder("[");
			boolean first = true;
			for (Object item : col) {
				if (!first) sb.append(",");
				sb.append(serialize(item));
				first = false;
			}
			sb.append("]");
			return sb.toString();
		}
		if (obj.getClass().isArray()) {
			int len = java.lang.reflect.Array.getLength(obj);
			StringBuilder sb = new StringBuilder("[");
			for (int i = 0; i < len; i++) {
				if (i > 0) sb.append(",");
				sb.append(serialize(java.lang.reflect.Array.get(obj, i)));
			}
			sb.append("]");
			return sb.toString();
		}
		return quote(obj.toString());
	}

	public static Object parse(String json) {
		if (json == null) return null;
		String trimmed = json.trim();
		if (trimmed.isEmpty()) return null;
		return new Parser(trimmed).parseValue();
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> parseObject(String json) {
		Object parsed = parse(json);
		if (parsed instanceof Map) {
			return (Map<String, Object>) parsed;
		}
		return Collections.emptyMap();
	}

	@SuppressWarnings("unchecked")
	public static List<Object> parseArray(String json) {
		Object parsed = parse(json);
		if (parsed instanceof List) {
			return (List<Object>) parsed;
		}
		return Collections.emptyList();
	}

	private static class Parser {
		private final String src;
		private int pos;

		Parser(String src) {
			this.src = src;
			this.pos = 0;
		}

		private void skipWhitespace() {
			while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
				pos++;
			}
		}

		Object parseValue() {
			skipWhitespace();
			if (pos >= src.length()) return null;
			char c = src.charAt(pos);
			if (c == '{') return parseObject();
			if (c == '[') return parseArray();
			if (c == '"' || c == '\'') return parseString();
			if (c == 't' || c == 'f') return parseBoolean();
			if (c == 'n') return parseNull();
			return parseNumber();
		}

		private Map<String, Object> parseObject() {
			Map<String, Object> map = new LinkedHashMap<>();
			pos++; // skip '{'
			while (true) {
				skipWhitespace();
				if (pos >= src.length()) break;
				if (src.charAt(pos) == '}') {
					pos++;
					break;
				}
				String key = parseString();
				skipWhitespace();
				if (pos < src.length() && src.charAt(pos) == ':') {
					pos++;
				}
				Object val = parseValue();
				map.put(key, val);
				skipWhitespace();
				if (pos < src.length() && src.charAt(pos) == ',') {
					pos++;
				} else if (pos < src.length() && src.charAt(pos) == '}') {
					pos++;
					break;
				}
			}
			return map;
		}

		private List<Object> parseArray() {
			List<Object> list = new ArrayList<>();
			pos++; // skip '['
			while (true) {
				skipWhitespace();
				if (pos >= src.length()) break;
				if (src.charAt(pos) == ']') {
					pos++;
					break;
				}
				list.add(parseValue());
				skipWhitespace();
				if (pos < src.length() && src.charAt(pos) == ',') {
					pos++;
				} else if (pos < src.length() && src.charAt(pos) == ']') {
					pos++;
					break;
				}
			}
			return list;
		}

		private String parseString() {
			skipWhitespace();
			if (pos >= src.length()) return "";
			char quoteChar = src.charAt(pos);
			if (quoteChar != '"' && quoteChar != '\'') return "";
			pos++;
			StringBuilder sb = new StringBuilder();
			while (pos < src.length()) {
				char c = src.charAt(pos++);
				if (c == quoteChar) break;
				if (c == '\\' && pos < src.length()) {
					char next = src.charAt(pos++);
					switch (next) {
						case '"': sb.append('"'); break;
						case '\\': sb.append('\\'); break;
						case '/': sb.append('/'); break;
						case 'b': sb.append('\b'); break;
						case 'f': sb.append('\f'); break;
						case 'n': sb.append('\n'); break;
						case 'r': sb.append('\r'); break;
						case 't': sb.append('\t'); break;
						case 'u':
							if (pos + 4 <= src.length()) {
								sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
								pos += 4;
							}
							break;
						default: sb.append(next);
					}
				} else {
					sb.append(c);
				}
			}
			return sb.toString();
		}

		private Boolean parseBoolean() {
			if (src.startsWith("true", pos)) {
				pos += 4;
				return Boolean.TRUE;
			}
			if (src.startsWith("false", pos)) {
				pos += 5;
				return Boolean.FALSE;
			}
			return null;
		}

		private Object parseNull() {
			if (src.startsWith("null", pos)) {
				pos += 4;
			}
			return null;
		}

		private Number parseNumber() {
			int start = pos;
			if (pos < src.length() && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) {
				pos++;
			}
			boolean isFloat = false;
			while (pos < src.length()) {
				char c = src.charAt(pos);
				if (Character.isDigit(c)) {
					pos++;
				} else if (c == '.' || c == 'e' || c == 'E') {
					isFloat = true;
					pos++;
				} else {
					break;
				}
			}
			String raw = src.substring(start, pos);
			try {
				if (isFloat) return Double.parseDouble(raw);
				long l = Long.parseLong(raw);
				if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
				return l;
			} catch (Exception e) {
				return 0;
			}
		}
	}
}
