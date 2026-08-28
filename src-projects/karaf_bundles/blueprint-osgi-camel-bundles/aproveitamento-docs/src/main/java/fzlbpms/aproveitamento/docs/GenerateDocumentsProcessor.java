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
		return value == null ? null : value.toString().trim();
	}

	private List<String> stringList(Exchange exchange, String propertyName) {
		Object value = exchange.getProperty(propertyName);
		if (value == null) {
			return List.of();
		}

		if (value instanceof Collection<?>) {
			List<String> items = new ArrayList<>();
			for (Object item : (Collection<?>) value) {
				if (item != null) {
					String normalized = item.toString().trim();
					if (!normalized.isBlank()) {
						items.add(normalized);
					}
				}
			}
			return items;
		}

		String stringValue = value.toString().trim();
		if (stringValue.startsWith("[") && stringValue.endsWith("]")) {
			String inner = stringValue.substring(1, stringValue.length() - 1);
			if (inner.isBlank()) {
				return List.of();
			}

			List<String> items = new ArrayList<>();
			for (String token : inner.split(",")) {
				String normalized = token.trim().replace("\"", "");
				if (!normalized.isBlank()) {
					items.add(normalized);
				}
			}
			return items;
		}

		return List.of(Objects.toString(value).trim());
	}

	public void setService(AproveitamentoDocumentService service) {
		this.service = service;
	}
}
