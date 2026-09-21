package fzlbpms.aproveitamento.docs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class AproveitamentoDocumentService {

	private static final String DEFAULT_SCHOOL_NAME = "ZONA LESTE";
	private static final String DEFAULT_PROGRAM_NAME = "Habilitação Profissional de Técnico em Desenvolvimento de Sistemas";
	private static final String DEFAULT_COURSE_NAME = "Curso Técnico de Desenvolvimento de Sistemas Noturno";
	private static final String DEFAULT_PORTARIA_DATE = "15/08/2022";
	private static final String DEFAULT_PARECER_PAGE_REFERENCE = "01";
	private static final String DEFAULT_DECISION = "deferido";
	private static final String DEFAULT_JUSTIFICATION = "há compatibilidade entre as competências demonstradas na documentação e o componente curricular solicitado.";
	private static final DateTimeFormatter OUTPUT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private static final String DEFAULT_PARECER_TEMPLATE_RESOURCE = "/templates/Doc_13_1_anexo_I_Parecer_da_Comissao_Template_Odt.odt";
	private static final String DEFAULT_DESPACHO_TEMPLATE_RESOURCE = "/templates/Doc_13_2_anexo_II_Despacho-Template_Odt.odt";

	private static final Pattern DOCX_PARECER_SUMMARY_PARAGRAPH = Pattern.compile(
		"(?s)<w:p>(?:(?!</w:p>).)*?<w:t>Após analise documental a comissão entendeu que</w:t>(?:(?!</w:p>).)*?</w:p>");
	private static final Pattern DOCX_PARAGRAPH_PROPERTIES = Pattern.compile("<w:pPr>[\\s\\S]*?</w:pPr>");
	private static final Pattern DOCX_DESPACHO_COURSE_TEXT =
		Pattern.compile("<w:t xml:space=\"preserve\"> regularmente matriculado.*?</w:t>");

	public String loadComponentsJson() throws IOException {
		Path configuredPath = AproveitamentoSettings.optionalComponentsPath();
		if (configuredPath != null) {
			return Files.readString(configuredPath, StandardCharsets.UTF_8);
		}

		try (InputStream inputStream = getClass().getResourceAsStream("/components-default.json")) {
			if (inputStream == null) {
				throw new IOException("Missing bundled resource /components-default.json.");
			}
			return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	public GenerationResult generate(AproveitamentoRequest request) throws IOException {
		ResolvedRequest resolved = resolve(request);

		byte[] parecerBytes;
		try (InputStream is = openTemplateStream(AproveitamentoSettings.optionalTemplateParecerPath(), DEFAULT_PARECER_TEMPLATE_RESOURCE)) {
			parecerBytes = renderDocument(is, resolved, true);
		}

		byte[] despachoBytes;
		try (InputStream is = openTemplateStream(AproveitamentoSettings.optionalTemplateDespachoPath(), DEFAULT_DESPACHO_TEMPLATE_RESOURCE)) {
			despachoBytes = renderDocument(is, resolved, false);
		}

		Path outputDirectory = createOutputDirectory(resolved);
		String studentSlug = slugify(resolved.studentName);
		String rmSlug = slugify(resolved.studentRm);
		String suffix = studentSlug + (rmSlug.isBlank() ? "" : "-" + rmSlug);

		String ext = isDocxTemplate(AproveitamentoSettings.optionalTemplateParecerPath()) ? ".docx" : ".odt";
		String parecerName = "parecer-" + suffix + ext;
		String despachoName = "despacho-" + suffix + ext;

		Path parecerPath = outputDirectory.resolve(parecerName);
		Path despachoPath = outputDirectory.resolve(despachoName);

		Files.write(parecerPath, parecerBytes);
		Files.write(despachoPath, despachoBytes);

		byte[] archiveBytes = buildArchive(parecerName, parecerBytes, despachoName, despachoBytes);
		String archiveName = "aproveitamento-" + suffix + "-" + OUTPUT_TIMESTAMP.format(LocalDateTime.now()) + ".zip";

		return new GenerationResult(
			outputDirectory,
			archiveName,
			archiveBytes,
			List.of(new GeneratedFile(parecerName, parecerPath), new GeneratedFile(despachoName, despachoPath)));
	}

	private boolean isDocxTemplate(Path path) {
		return path != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx");
	}

	private InputStream openTemplateStream(Path configuredPath, String resourcePath) throws IOException {
		if (configuredPath != null && Files.exists(configuredPath)) {
			return Files.newInputStream(configuredPath);
		}
		InputStream resourceStream = getClass().getResourceAsStream(resourcePath);
		if (resourceStream != null) {
			return resourceStream;
		}
		if (configuredPath != null) {
			throw new IOException("Template file not found at " + configuredPath + " and resource " + resourcePath + " is missing.");
		}
		throw new IOException("Missing bundled template resource: " + resourcePath);
	}

	private ResolvedRequest resolve(AproveitamentoRequest request) {
		String studentName = requireNonBlank(request.getStudentName(), "studentName");
		String teacherPresident = requireNonBlank(request.getTeacherPresident(), "teacherPresident");
		String teacherMember1 = requireNonBlank(request.getTeacherMember1(), "teacherMember1");
		String teacherMember2 = requireNonBlank(request.getTeacherMember2(), "teacherMember2");
		List<String> componentNames = requireNonEmptyList(request.getComponentNames(), "componentNames");

		String decision = defaultIfBlank(request.getDecision(), DEFAULT_DECISION).toLowerCase(Locale.ROOT);
		if (!Objects.equals(decision, "deferido") && !Objects.equals(decision, "indeferido")) {
			throw new IllegalArgumentException("Field decision must be either \"deferido\" or \"indeferido\".");
		}

		return new ResolvedRequest(
			studentName,
			trimToEmpty(request.getStudentRm()),
			teacherPresident,
			teacherMember1,
			teacherMember2,
			componentNames,
			trimToEmpty(request.getExpedientNumber()),
			defaultIfBlank(request.getSchoolName(), DEFAULT_SCHOOL_NAME),
			defaultIfBlank(request.getProgramName(), DEFAULT_PROGRAM_NAME),
			defaultIfBlank(request.getCourseName(), DEFAULT_COURSE_NAME),
			defaultIfBlank(request.getPortariaDate(), DEFAULT_PORTARIA_DATE),
			defaultIfBlank(request.getParecerPageReference(), DEFAULT_PARECER_PAGE_REFERENCE),
			decision,
			defaultIfBlank(request.getJustification(), DEFAULT_JUSTIFICATION));
	}

	private byte[] renderDocument(InputStream templateStream, ResolvedRequest resolved, boolean isParecer)
		throws IOException {
		try (ZipInputStream zipInputStream = new ZipInputStream(templateStream);
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {

			ZipEntry entry;
			while ((entry = zipInputStream.getNextEntry()) != null) {
				byte[] entryBytes = zipInputStream.readAllBytes();
				ZipEntry outputEntry = new ZipEntry(entry.getName());
				zipOutputStream.putNextEntry(outputEntry);

				if (!entry.isDirectory() && Objects.equals(entry.getName(), "content.xml")) {
					// ODT Format
					String xml = new String(entryBytes, StandardCharsets.UTF_8);
					String rendered = isParecer ? renderOdtParecer(xml, resolved) : renderOdtDespacho(xml, resolved);
					zipOutputStream.write(rendered.getBytes(StandardCharsets.UTF_8));
				} else if (!entry.isDirectory() && Objects.equals(entry.getName(), "word/document.xml")) {
					// DOCX Format
					String xml = new String(entryBytes, StandardCharsets.UTF_8);
					Map<String, String> replacements = buildReplacements(resolved);
					String prepared = isParecer ? prepareDocxParecerXml(xml) : prepareDocxDespachoXml(xml);
					String rendered = applyReplacements(prepared, replacements);
					zipOutputStream.write(rendered.getBytes(StandardCharsets.UTF_8));
				} else if (!entry.isDirectory()) {
					zipOutputStream.write(entryBytes);
				}

				zipOutputStream.closeEntry();
				zipInputStream.closeEntry();
			}

			zipOutputStream.finish();
			return outputStream.toByteArray();
		}
	}

	private String renderOdtParecer(String xml, ResolvedRequest resolved) {
		String result = xml;

		// 1. Parecer conclusivo
		String decisionSummary = buildDecisionSummary(resolved);
		String startMarker = "<text:span text:style-name=\"T12\">Após analise documental";
		String endMarker = "em outro curso apresentado na documentação.</text:span>";
		if (result.contains(startMarker) && result.contains(endMarker)) {
			int startIdx = result.indexOf(startMarker);
			int endIdx = result.indexOf(endMarker, startIdx) + endMarker.length();
			result = result.substring(0, startIdx)
				+ "<text:span text:style-name=\"T1\">" + escapeXml(decisionSummary) + "</text:span>"
				+ result.substring(endIdx);
		}

		// 2. Expediente
		result = result.replace(
			"Expediente de Atendimento de Aproveitamento de Estudo nº:",
			"Expediente de Atendimento de Aproveitamento de Estudo nº: " + escapeXml(resolved.expedientNumber));

		// 3. Aluno
		String studentDisplay = resolved.studentName + (resolved.studentRm.isBlank() ? "" : " (RM: " + resolved.studentRm + ")");
		result = result.replace(
			"<text:span text:style-name=\"T3\"> «</text:span><text:span text:style-name=\"T4\">nomedoaluno»</text:span>",
			"<text:span text:style-name=\"T4\"> " + escapeXml(studentDisplay) + "</text:span>");

		// 4. Curso / Programa
		String programDisplay = resolved.programName;
		if (programDisplay.startsWith("Habilitação Profissional de ")) {
			programDisplay = programDisplay.substring("Habilitação Profissional de ".length());
		}
		result = result.replace(
			"Habilitação Profissional de «</text:span><text:span text:style-name=\"T5\">nomedocurso»</text:span>",
			"Habilitação Profissional de </text:span><text:span text:style-name=\"T5\">" + escapeXml(programDisplay) + "</text:span>");

		// 5. Portaria date
		result = result.replace(
			"Em conformidade com a Portaria do Sr(a). Diretor(a) da Etec, no 15/08/2022",
			"Em conformidade com a Portaria do Sr(a). Diretor(a) da Etec, no " + escapeXml(resolved.portariaDate));

		// 6. Professores
		result = result.replace(
			"<text:span text:style-name=\"T10\">«</text:span><text:span text:style-name=\"T11\">nomeprofpresidente»</text:span>",
			"<text:span text:style-name=\"T11\">" + escapeXml(resolved.teacherPresident) + "</text:span>");
		result = result.replace(
			"<text:span text:style-name=\"T10\">«</text:span><text:span text:style-name=\"T11\">nomeprofmembro1»</text:span>",
			"<text:span text:style-name=\"T11\">" + escapeXml(resolved.teacherMember1) + "</text:span>");
		result = result.replace(
			"<text:span text:style-name=\"T10\">«</text:span><text:span text:style-name=\"T11\">nomeprofmembro2»</text:span>",
			"<text:span text:style-name=\"T11\">" + escapeXml(resolved.teacherMember2) + "</text:span>");

		// 7. Disciplinas
		List<String> components = resolved.componentNames;
		String c1 = components.size() > 0 ? components.get(0) : "";
		String c2 = components.size() > 1 ? components.get(1) : "";
		String c3 = components.size() > 2 ? components.get(2) : "";

		result = result.replace(
			"<text:span text:style-name=\"T1\">«</text:span><text:span text:style-name=\"T7\">disciplina1»</text:span>",
			"<text:span text:style-name=\"T7\">" + escapeXml(c1) + "</text:span>");
		result = result.replace(
			"«<text:span text:style-name=\"T8\">disciplina2»</text:span>",
			"<text:span text:style-name=\"T8\">" + escapeXml(c2) + "</text:span>");
		result = result.replace(
			"«<text:span text:style-name=\"T8\">disciplina3»</text:span>",
			"<text:span text:style-name=\"T8\">" + escapeXml(c3) + "</text:span>");

		return result;
	}

	private String renderOdtDespacho(String xml, ResolvedRequest resolved) {
		String result = xml;

		// 1. Folhas
		result = result.replace(
			"<text:span text:style-name=\"T5\"> 01</text:span>",
			"<text:span text:style-name=\"T5\"> " + escapeXml(resolved.parecerPageReference) + "</text:span>");

		// 2. Escola / Etec
		result = result.replace("ZONA LESTE", escapeXml(resolved.schoolName));

		// 3. Aluno
		String studentDisplay = resolved.studentName + (resolved.studentRm.isBlank() ? "" : " (RM: " + resolved.studentRm + ")");
		result = result.replace(
			"<text:span text:style-name=\"T7\">«</text:span><text:span text:style-name=\"T8\">nomedoaluno»</text:span>",
			"<text:span text:style-name=\"T8\">" + escapeXml(studentDisplay) + "</text:span>");

		// 4. Curso
		result = result.replace(
			"Curso Técnico de Desenvolvimento de Sistemas Noturno",
			escapeXml(resolved.courseName));

		// 5. Disciplinas
		String componentsStr = String.join(", ", resolved.componentNames);
		result = result.replace(
			"<text:span text:style-name=\"T12\">«</text:span><text:span text:style-name=\"T13\">disciplina1»</text:span>",
			"<text:span text:style-name=\"T13\">" + escapeXml(componentsStr) + "</text:span>");

		// 6. Resultado
		String resText = resolved.decision.equals("deferido")
			? "deferido o aproveitamento de estudos"
			: "indeferido o aproveitamento de estudos";
		result = result.replace("deferido o aproveitamento de estudos", escapeXml(resText));

		return result;
	}

	private String prepareDocxParecerXml(String xml) {
		String transformed = xml;
		transformed = replaceExact(
			transformed,
			"<w:t>Expediente de Atendimento de Aproveitamento de Estudo nº:</w:t>",
			"<w:t>Expediente de Atendimento de Aproveitamento de Estudo nº: __EXPEDIENT_NUMBER__</w:t>",
			"parecer expedient number");
		transformed = replaceExact(
			transformed,
			"<w:t>Fábio Assato Rossi</w:t>",
			"<w:t>__STUDENT_NAME__</w:t>",
			"parecer student name");
		transformed = replaceExact(
			transformed,
			"<w:t>Habilitação Profissional de Técnico em Desenvolvimento de Sistemas</w:t>",
			"<w:t>__PROGRAM_NAME__</w:t>",
			"parecer program name");
		transformed = replaceExact(
			transformed,
			"<w:t>Em conformidade com a Portaria do Sr(a). Diretor(a) da Etec, no 15/08/2022</w:t>",
			"<w:t>Em conformidade com a Portaria do Sr(a). Diretor(a) da Etec, no __PORTARIA_DATE__</w:t>",
			"parecer portaria date");
		transformed = replaceExact(
			transformed,
			"<w:t>Wagner França Marques</w:t>",
			"<w:t>__TEACHER_PRESIDENT__</w:t>",
			"parecer teacher president");
		transformed = replaceExact(
			transformed,
			"<w:t>Ralf Gerônimo</w:t>",
			"<w:t>__TEACHER_MEMBER_1__</w:t>",
			"parecer teacher member 1");
		transformed = replaceExact(
			transformed,
			"<w:t>João Paulo Frias</w:t>",
			"<w:t>__TEACHER_MEMBER_2__</w:t>",
			"parecer teacher member 2");
		transformed = replaceParagraph(
			transformed,
			DOCX_PARECER_SUMMARY_PARAGRAPH,
			"__DECISION_SUMMARY__",
			"parecer decision summary");
		transformed = replaceExact(
			transformed,
			"<w:t>Inglês Instrumental</w:t>",
			"<w:t>__COMPONENTS_DISPLAY__</w:t>",
			"parecer components display");
		return transformed;
	}

	private String prepareDocxDespachoXml(String xml) {
		String transformed = xml;
		transformed = replaceExact(
			transformed,
			"<w:t xml:space=\"preserve\"> 01</w:t>",
			"<w:t xml:space=\"preserve\"> __PARECER_PAGE_REFERENCE__</w:t>",
			"despacho parecer page reference");
		transformed = replaceExact(
			transformed,
			"<w:t xml:space=\"preserve\">O (A) Diretor (a) da Etec  ZONA LESTE </w:t>",
			"<w:t xml:space=\"preserve\">O (A) Diretor (a) da Etec __SCHOOL_NAME__ </w:t>",
			"despacho school name");
		transformed = replaceExact(
			transformed,
			"<w:t>Fábio Assato Rossi</w:t>",
			"<w:t>__STUDENT_NAME__</w:t>",
			"despacho student name");
		transformed = replaceRegexOnce(
			transformed,
			DOCX_DESPACHO_COURSE_TEXT,
			"<w:t xml:space=\"preserve\"> regularmente matriculado no (a) __COURSE_NAME__</w:t>",
			"despacho course name");
		transformed = replaceExact(
			transformed,
			"<w:t>Inglês Instrumental</w:t>",
			"<w:t>__COMPONENTS_DISPLAY__</w:t>",
			"despacho components display");
		transformed = replaceExact(
			transformed,
			"<w:t>deferido o aproveitamento de estudos</w:t>",
			"<w:t>__RESULT_TEXT__</w:t>",
			"despacho result text");
		return transformed;
	}

	private String replaceParagraph(String text, Pattern pattern, String placeholder, String label) {
		Matcher matcher = pattern.matcher(text);
		if (!matcher.find()) {
			throw new IllegalStateException("Template mismatch while replacing " + label + ": paragraph not found.");
		}

		String paragraph = matcher.group();
		if (matcher.find()) {
			throw new IllegalStateException("Template mismatch while replacing " + label + ": multiple paragraphs found.");
		}

		Matcher propertiesMatcher = DOCX_PARAGRAPH_PROPERTIES.matcher(paragraph);
		if (!propertiesMatcher.find()) {
			throw new IllegalStateException(
				"Template mismatch while replacing " + label + ": paragraph properties not found.");
		}

		String replacement = "<w:p>" + propertiesMatcher.group()
			+ "<w:r><w:rPr><w:rFonts w:cs=\"Arial\" w:ascii=\"Arial\" w:hAnsi=\"Arial\"/><w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/></w:rPr><w:t>"
			+ placeholder + "</w:t></w:r></w:p>";
		return pattern.matcher(text).replaceFirst(Matcher.quoteReplacement(replacement));
	}

	private String replaceRegexOnce(String text, Pattern pattern, String replacement, String label) {
		Matcher matcher = pattern.matcher(text);
		if (!matcher.find()) {
			throw new IllegalStateException("Template mismatch while replacing " + label + ": no match found.");
		}
		if (matcher.find()) {
			throw new IllegalStateException("Template mismatch while replacing " + label + ": multiple matches found.");
		}
		return pattern.matcher(text).replaceFirst(Matcher.quoteReplacement(replacement));
	}

	private String replaceExact(String text, String search, String replacement, String label) {
		int occurrences = countOccurrences(text, search);
		if (occurrences != 1) {
			throw new IllegalStateException(
				"Template mismatch while replacing " + label + ": expected 1 occurrence, found " + occurrences + ".");
		}
		return text.replace(search, replacement);
	}

	private int countOccurrences(String text, String search) {
		int count = 0;
		int index = text.indexOf(search);
		while (index >= 0) {
			count++;
			index = text.indexOf(search, index + search.length());
		}
		return count;
	}

	private String applyReplacements(String template, Map<String, String> replacements) {
		String result = template;
		for (Map.Entry<String, String> entry : replacements.entrySet()) {
			result = result.replace(entry.getKey(), escapeXml(entry.getValue()));
		}
		return result;
	}

	private Map<String, String> buildReplacements(ResolvedRequest request) {
		Map<String, String> replacements = new LinkedHashMap<>();
		replacements.put("__STUDENT_NAME__", request.studentName);
		replacements.put("__EXPEDIENT_NUMBER__", request.expedientNumber);
		replacements.put("__PROGRAM_NAME__", request.programName);
		replacements.put("__PORTARIA_DATE__", request.portariaDate);
		replacements.put("__TEACHER_PRESIDENT__", request.teacherPresident);
		replacements.put("__TEACHER_MEMBER_1__", request.teacherMember1);
		replacements.put("__TEACHER_MEMBER_2__", request.teacherMember2);
		replacements.put("__DECISION_SUMMARY__", buildDecisionSummary(request));
		replacements.put("__COMPONENTS_DISPLAY__", String.join(", ", request.componentNames));
		replacements.put("__PARECER_PAGE_REFERENCE__", request.parecerPageReference);
		replacements.put("__SCHOOL_NAME__", request.schoolName);
		replacements.put("__COURSE_NAME__", request.courseName);
		replacements.put(
			"__RESULT_TEXT__",
			request.decision.equals("deferido") ? "deferido o aproveitamento de estudos"
				: "indeferido o aproveitamento de estudos");
		return replacements;
	}

	private String buildDecisionSummary(ResolvedRequest request) {
		String componentsDisplay = String.join(", ", request.componentNames);
		boolean single = request.componentNames.size() == 1;
		boolean deferido = request.decision.equals("deferido");
		String predicate;
		if (single) {
			predicate = deferido
				? "o componente curricular " + componentsDisplay + " pode ser deferido"
				: "o componente curricular " + componentsDisplay + " não pode ser deferido";
		} else {
			predicate = deferido
				? "os componentes curriculares " + componentsDisplay + " podem ser deferidos"
				: "os componentes curriculares " + componentsDisplay + " não podem ser deferidos";
		}
		return "Após analise documental, a comissão entendeu que " + predicate + ", pois " + request.justification;
	}

	private Path createOutputDirectory(ResolvedRequest request) throws IOException {
		Path root = AproveitamentoSettings.outputDirectory();
		String timestamp = OUTPUT_TIMESTAMP.format(LocalDateTime.now());
		String folderName = timestamp + "_" + slugify(request.studentName)
			+ (request.studentRm.isBlank() ? "" : "_" + slugify(request.studentRm));
		Path outputDirectory = root.resolve(folderName);
		Files.createDirectories(outputDirectory);
		return outputDirectory;
	}

	private byte[] buildArchive(String parecerName, byte[] parecerBytes, String despachoName, byte[] despachoBytes)
		throws IOException {
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
			addZipEntry(zipOutputStream, parecerName, parecerBytes);
			addZipEntry(zipOutputStream, despachoName, despachoBytes);
			zipOutputStream.finish();
			return outputStream.toByteArray();
		}
	}

	private void addZipEntry(ZipOutputStream zipOutputStream, String entryName, byte[] bytes) throws IOException {
		ZipEntry zipEntry = new ZipEntry(entryName);
		zipOutputStream.putNextEntry(zipEntry);
		zipOutputStream.write(bytes);
		zipOutputStream.closeEntry();
	}

	private String requireNonBlank(String value, String fieldName) {
		String trimmed = trimToEmpty(value);
		if (trimmed.isBlank()) {
			throw new IllegalArgumentException("Missing required field: " + fieldName + ".");
		}
		return trimmed;
	}

	private List<String> requireNonEmptyList(List<String> values, String fieldName) {
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("Missing required field: " + fieldName + ".");
		}

		List<String> sanitized = new ArrayList<>();
		for (String value : values) {
			String trimmed = trimToEmpty(value);
			if (!trimmed.isBlank()) {
				sanitized.add(trimmed);
			}
		}

		if (sanitized.isEmpty()) {
			throw new IllegalArgumentException("Missing required field: " + fieldName + ".");
		}

		return List.copyOf(sanitized);
	}

	private String defaultIfBlank(String value, String defaultValue) {
		String trimmed = trimToEmpty(value);
		return trimmed.isBlank() ? defaultValue : trimmed;
	}

	private String trimToEmpty(String value) {
		return value == null ? "" : value.trim();
	}

	private String slugify(String value) {
		String normalized = Normalizer.normalize(trimToEmpty(value), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		String slug = normalized.replaceAll("[^A-Za-z0-9_-]+", "-").replaceAll("^-+|-+$", "").toLowerCase(Locale.ROOT);
		return slug.isBlank() ? "documento" : slug;
	}

	private String escapeXml(String value) {
		if (value == null) {
			return "";
		}
		String escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return escaped.replace("\"", "&quot;").replace("'", "&apos;");
	}

	public static final class GeneratedFile {
		private final String fileName;
		private final Path path;

		public GeneratedFile(String fileName, Path path) {
			this.fileName = fileName;
			this.path = path;
		}

		public String getFileName() {
			return fileName;
		}

		public Path getPath() {
			return path;
		}
	}

	public static final class GenerationResult {
		private final Path outputDirectory;
		private final String archiveFileName;
		private final byte[] archiveBytes;
		private final List<GeneratedFile> generatedFiles;

		public GenerationResult(Path outputDirectory, String archiveFileName, byte[] archiveBytes,
				List<GeneratedFile> generatedFiles) {
			this.outputDirectory = outputDirectory;
			this.archiveFileName = archiveFileName;
			this.archiveBytes = archiveBytes;
			this.generatedFiles = generatedFiles;
		}

		public Path getOutputDirectory() {
			return outputDirectory;
		}

		public String getArchiveFileName() {
			return archiveFileName;
		}

		public byte[] getArchiveBytes() {
			return archiveBytes;
		}

		public List<GeneratedFile> getGeneratedFiles() {
			return generatedFiles;
		}
	}

	private static final class ResolvedRequest {
		private final String studentName;
		private final String studentRm;
		private final String teacherPresident;
		private final String teacherMember1;
		private final String teacherMember2;
		private final List<String> componentNames;
		private final String expedientNumber;
		private final String schoolName;
		private final String programName;
		private final String courseName;
		private final String portariaDate;
		private final String parecerPageReference;
		private final String decision;
		private final String justification;

		private ResolvedRequest(String studentName, String studentRm, String teacherPresident, String teacherMember1,
				String teacherMember2, List<String> componentNames, String expedientNumber, String schoolName,
				String programName, String courseName, String portariaDate, String parecerPageReference, String decision,
				String justification) {
			this.studentName = studentName;
			this.studentRm = studentRm;
			this.teacherPresident = teacherPresident;
			this.teacherMember1 = teacherMember1;
			this.teacherMember2 = teacherMember2;
			this.componentNames = componentNames;
			this.expedientNumber = expedientNumber;
			this.schoolName = schoolName;
			this.programName = programName;
			this.courseName = courseName;
			this.portariaDate = portariaDate;
			this.parecerPageReference = parecerPageReference;
			this.decision = decision;
			this.justification = justification;
		}
	}
}
