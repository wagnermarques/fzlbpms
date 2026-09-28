package fzlbpms.tasktoday;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Validation and conversion of JSON request values. */
final class Values {

	static final Set<String> PRIORITIES = Set.of("BAIXA", "MEDIA", "ALTA", "URGENTE");
	static final Set<String> STATUSES = Set.of("PENDENTE", "EM_ANDAMENTO", "CONCLUIDA");
	static final Set<String> ALERT_TYPES = Set.of("sound", "notification", "none");

	private static final Pattern ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

	private Values() {
	}

	/** Client-supplied id (the PWA creates ids offline) or a generated one. */
	static String idOrNew(Map<String, Object> body, String prefix) {
		Object id = body.get("id");
		if (id == null || String.valueOf(id).isBlank()) return prefix + UUID.randomUUID();
		String s = String.valueOf(id);
		if (!ID.matcher(s).matches()) throw Http.badRequest("id invalido: use ate 64 caracteres [A-Za-z0-9._:-]");
		return s;
	}

	static String text(Map<String, Object> body, String field, int maxLen, boolean required) {
		Object v = body.get(field);
		if (v == null || String.valueOf(v).isBlank()) {
			if (required) throw Http.badRequest(field + " e obrigatorio");
			return null;
		}
		String s = String.valueOf(v).trim();
		if (s.length() > maxLen) throw Http.badRequest(field + " excede " + maxLen + " caracteres");
		return s;
	}

	static String oneOf(Map<String, Object> body, String field, Set<String> allowed) {
		Object v = body.get(field);
		if (v == null) return null;
		String s = String.valueOf(v);
		if (!allowed.contains(s)) throw Http.badRequest(field + " invalido: use um de " + allowed);
		return s;
	}

	static Integer minutes(Map<String, Object> body, String field) {
		Object v = body.get(field);
		if (v == null) return null;
		int n;
		try {
			n = v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(String.valueOf(v).trim());
		} catch (NumberFormatException e) {
			throw Http.badRequest(field + " deve ser um inteiro");
		}
		if (n < 0 || n > 525_600) throw Http.badRequest(field + " deve estar entre 0 e 525600");
		return n;
	}

	static Boolean bool(Map<String, Object> body, String field) {
		Object v = body.get(field);
		if (v == null) return null;
		if (v instanceof Boolean) return (Boolean) v;
		String s = String.valueOf(v);
		if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
		if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
		throw Http.badRequest(field + " deve ser booleano");
	}

	/**
	 * ISO-8601 instant ("2026-09-28T21:00:00.000Z", as Date.toISOString() sends)
	 * or with an offset; a local date-time without offset is read in the
	 * server zone (TZ=America/Sao_Paulo in the Karaf container).
	 */
	static Timestamp timestamp(Object v, String field) {
		if (v == null || String.valueOf(v).isBlank()) return null;
		String s = String.valueOf(v).trim();
		try {
			return Timestamp.from(OffsetDateTime.parse(s).toInstant());
		} catch (DateTimeParseException ignored) {
			// fall through
		}
		try {
			return Timestamp.from(LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant());
		} catch (DateTimeParseException e) {
			throw Http.badRequest(field + " deve ser uma data ISO-8601");
		}
	}

	static String iso(Timestamp ts) {
		return ts == null ? null : ts.toInstant().toString();
	}

	static ZoneId zone(String tz) {
		if (tz == null || tz.isBlank()) return ZoneId.systemDefault();
		try {
			return ZoneId.of(tz);
		} catch (Exception e) {
			throw Http.badRequest("tz invalido: use um identificador IANA, ex. America/Sao_Paulo");
		}
	}

	static Timestamp now() {
		return Timestamp.from(Instant.now());
	}
}
