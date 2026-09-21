package fzlbpms.aproveitamento.docs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

public class GenerateDocumentsProcessor implements Processor {

	private AproveitamentoDocumentService service;

	@Override
	public void process(Exchange exchange) throws Exception {
		AproveitamentoRequest request = new AproveitamentoRequest(
			requiredString(exchange, "studentName"),
			optionalString(exchange, "studentRm"),
			requiredString(exchange, "teacherPresident"),
			requiredString(exchange, "teacherMember1"),
			requiredString(exchange, "teacherMember2"),
			stringList(exchange, "componentNames"),
			optionalString(exchange, "expedientNumber"),
			optionalString(exchange, "schoolName"),
			optionalString(exchange, "programName"),
			optionalString(exchange, "courseName"),
			optionalString(exchange, "portariaDate"),
			optionalString(exchange, "parecerPageReference"),
			optionalString(exchange, "decision"),
			optionalString(exchange, "justification"));

		AproveitamentoDocumentService.GenerationResult result = service.generate(request);
		Message message = exchange.getMessage();
		message.setBody(result.getArchiveBytes());
		message.setHeader(Exchange.CONTENT_TYPE, "application/zip");
		message.setHeader("Content-Disposition", "attachment; filename=\"" + result.getArchiveFileName() + "\"");
		message.setHeader("X-Generated-Output-Dir", result.getOutputDirectory().toString());
	}

	private String requiredString(Exchange exchange, String propertyName) {
		String value = optionalString(exchange, propertyName);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing required field: " + propertyName + ".");
		}
		return value;
	}

	private String optionalString(Exchange exchange, String propertyName) {
		Object value = exchange.getProperty(propertyName);
		if (value == null) {
			return null;
		}
		if (value instanceof Collection<?>) {
			Collection<?> col = (Collection<?>) value;
			if (col.isEmpty()) {
				return null;
			}
			for (Object item : col) {
				if (item != null && !item.toString().trim().isBlank()) {
					return item.toString().trim();
				}
			}
			return null;
		}
		return value.toString().trim();
	}

	private List<String> stringList(Exchange exchange, String propertyName) {
		Object value = exchange.getProperty(propertyName);
		if (value == null) {
			return List.of();
		}
		List<String> items = new ArrayList<>();
		collectStrings(value, items);
		return items;
	}

	private void collectStrings(Object value, List<String> target) {
		if (value == null) {
			return;
		}
		if (value instanceof Collection<?>) {
			for (Object item : (Collection<?>) value) {
				collectStrings(item, target);
			}
			return;
		}
		String stringValue = value.toString().trim();
		if (stringValue.startsWith("[") && stringValue.endsWith("]")) {
			String inner = stringValue.substring(1, stringValue.length() - 1);
			for (String token : inner.split(",")) {
				String normalized = token.trim().replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");
				if (!normalized.isBlank()) {
					target.add(normalized);
				}
			}
			return;
		}
		if (!stringValue.isBlank()) {
			target.add(stringValue);
		}
	}

	public void setService(AproveitamentoDocumentService service) {
		this.service = service;
	}
}
