package fzlbpms.chamadasonline;

import java.util.UUID;

import org.apache.camel.Exchange;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

/**
 * Small JSON-body parsing/validation helpers shared by the request
 * processors, standing in for the zod schemas (checkinSchema, loginSchema,
 * ...) in the Node source. Any failure maps to the same "invalid_payload"
 * error code Fastify's `.safeParse` failures return (400), just without the
 * field-level `details` object zod attaches.
 */
final class RequestUtil {

	private static final Configuration LENIENT = Configuration.defaultConfiguration()
			.addOptions(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL);

	private RequestUtil() {
	}

	static DocumentContext parseBody(Exchange exchange) {
		String body = exchange.getIn().getBody(String.class);
		if (body == null || body.isBlank()) {
			throw new BadRequestException("invalid_payload");
		}
		try {
			return JsonPath.using(LENIENT).parse(body);
		} catch (RuntimeException e) {
			throw new BadRequestException("invalid_payload");
		}
	}

	static String requireString(DocumentContext ctx, String path) {
		Object value = ctx.read(path);
		if (!(value instanceof String) || ((String) value).isBlank()) {
			throw new BadRequestException("invalid_payload");
		}
		return (String) value;
	}

	static String optionalString(DocumentContext ctx, String path) {
		Object value = ctx.read(path);
		return value instanceof String ? (String) value : null;
	}

	static UUID requireUuid(DocumentContext ctx, String path) {
		String value = requireString(ctx, path);
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			throw new BadRequestException("invalid_payload");
		}
	}

	static double requireDouble(DocumentContext ctx, String path) {
		Object value = ctx.read(path);
		if (!(value instanceof Number)) {
			throw new BadRequestException("invalid_payload");
		}
		return ((Number) value).doubleValue();
	}

	static String requirePathParam(Exchange exchange, String name) {
		String value = exchange.getIn().getHeader(name, String.class);
		if (value == null || value.isBlank()) {
			throw new BadRequestException("missing_path_param");
		}
		return value;
	}

	static UUID requireUuidPathParam(Exchange exchange, String name) {
		try {
			return UUID.fromString(requirePathParam(exchange, name));
		} catch (IllegalArgumentException e) {
			throw new BadRequestException("invalid_path_param");
		}
	}
}
