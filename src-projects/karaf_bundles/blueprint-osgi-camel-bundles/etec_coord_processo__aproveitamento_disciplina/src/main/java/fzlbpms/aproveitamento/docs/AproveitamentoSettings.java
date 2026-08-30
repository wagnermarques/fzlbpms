package fzlbpms.aproveitamento.docs;

import java.nio.file.Path;
import java.nio.file.Paths;

final class AproveitamentoSettings {

	static final String TEMPLATE_PARECER_PROPERTY = "fzlbpms.aproveitamento.template.parecer";
	static final String TEMPLATE_DESPACHO_PROPERTY = "fzlbpms.aproveitamento.template.despacho";
	static final String COMPONENTS_PATH_PROPERTY = "fzlbpms.aproveitamento.components.path";
	static final String OUTPUT_DIR_PROPERTY = "fzlbpms.aproveitamento.output.dir";

	private AproveitamentoSettings() {
	}

	static Path optionalTemplateParecerPath() {
		String value = optional(TEMPLATE_PARECER_PROPERTY, "FZLBPMS_APROVEITAMENTO_TEMPLATE_PARECER");
		return value == null || value.isBlank() ? null : Paths.get(value);
	}

	static Path optionalTemplateDespachoPath() {
		String value = optional(TEMPLATE_DESPACHO_PROPERTY, "FZLBPMS_APROVEITAMENTO_TEMPLATE_DESPACHO");
		return value == null || value.isBlank() ? null : Paths.get(value);
	}

	static Path optionalComponentsPath() {
		String value = optional(COMPONENTS_PATH_PROPERTY, "FZLBPMS_APROVEITAMENTO_COMPONENTS_PATH");
		return value == null || value.isBlank() ? null : Paths.get(value);
	}

	static Path outputDirectory() {
		String value = optional(OUTPUT_DIR_PROPERTY, "FZLBPMS_APROVEITAMENTO_OUTPUT_DIR");
		if (value == null || value.isBlank()) {
			return Paths.get(System.getProperty("java.io.tmpdir"), "fzlbpms-aproveitamento-docs");
		}
		return Paths.get(value);
	}

	private static String required(String propertyName, String envName) {
		String value = optional(propertyName, envName);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(
				"Missing required configuration. Set system property " + propertyName + " or environment variable " + envName + ".");
		}
		return value;
	}

	private static String optional(String propertyName, String envName) {
		String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		String envValue = System.getenv(envName);
		return envValue == null || envValue.isBlank() ? null : envValue;
	}
}
