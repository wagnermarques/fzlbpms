package fzlbpms.aproveitamento.docs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AproveitamentoDocumentServiceTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void generatesZipAndRenderedDocxFiles() throws Exception {
		Path workDir = temporaryFolder.newFolder("aproveitamento").toPath();
		Path parecerTemplate = workDir.resolve("parecer.docx");
		Path despachoTemplate = workDir.resolve("despacho.docx");
		Path outputDir = workDir.resolve("generated");
		Path componentsPath = workDir.resolve("components.json");

		Files.writeString(componentsPath, "[\"Inglês Instrumental\",\"Banco de Dados I\"]", StandardCharsets.UTF_8);
		Files.write(parecerTemplate, createDocxBytes(parecerTemplateXml()));
		Files.write(despachoTemplate, createDocxBytes(despachoTemplateXml()));

		System.setProperty(AproveitamentoSettings.TEMPLATE_PARECER_PROPERTY, parecerTemplate.toString());
		System.setProperty(AproveitamentoSettings.TEMPLATE_DESPACHO_PROPERTY, despachoTemplate.toString());
		System.setProperty(AproveitamentoSettings.OUTPUT_DIR_PROPERTY, outputDir.toString());
		System.setProperty(AproveitamentoSettings.COMPONENTS_PATH_PROPERTY, componentsPath.toString());

		try {
			AproveitamentoDocumentService service = new AproveitamentoDocumentService();
			assertEquals("[\"Inglês Instrumental\",\"Banco de Dados I\"]", service.loadComponentsJson());

			AproveitamentoRequest request = new AproveitamentoRequest(
				"Maria Silva",
				"12345",
				"Professor A",
				"Professor B",
				"Professor C",
				List.of("Inglês Instrumental", "Banco de Dados I"),
				"2026-001",
				"ETEC TESTE",
				"Programa Teste",
				"Curso Teste",
				"24/08/2026",
				"07",
				"indeferido",
				"não houve equivalência suficiente.");

			AproveitamentoDocumentService.GenerationResult result = service.generate(request);

			assertEquals(2, result.getGeneratedFiles().size());
			assertTrue(Files.exists(result.getGeneratedFiles().get(0).getPath()));
			assertTrue(Files.exists(result.getGeneratedFiles().get(1).getPath()));

			Map<String, byte[]> archiveEntries = unzip(result.getArchiveBytes());
			assertEquals(2, archiveEntries.size());

			String parecerXml = readDocumentXml(result.getGeneratedFiles().get(0).getPath());
			assertTrue(parecerXml.contains("Maria Silva"));
			assertTrue(parecerXml.contains("Programa Teste"));
			assertTrue(parecerXml.contains("Professor A"));
			assertTrue(parecerXml.contains("Inglês Instrumental, Banco de Dados I"));
			assertTrue(parecerXml.contains("não podem ser deferidos"));

			String despachoXml = readDocumentXml(result.getGeneratedFiles().get(1).getPath());
			assertTrue(despachoXml.contains("ETEC TESTE"));
			assertTrue(despachoXml.contains("Curso Teste"));
			assertTrue(despachoXml.contains("indeferido o aproveitamento de estudos"));
		} finally {
			System.clearProperty(AproveitamentoSettings.TEMPLATE_PARECER_PROPERTY);
			System.clearProperty(AproveitamentoSettings.TEMPLATE_DESPACHO_PROPERTY);
			System.clearProperty(AproveitamentoSettings.OUTPUT_DIR_PROPERTY);
			System.clearProperty(AproveitamentoSettings.COMPONENTS_PATH_PROPERTY);
		}
	}

	private byte[] createDocxBytes(String documentXml) throws IOException {
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
			zipOutputStream.putNextEntry(new ZipEntry("[Content_Types].xml"));
			zipOutputStream.write("<Types/>".getBytes(StandardCharsets.UTF_8));
			zipOutputStream.closeEntry();

			zipOutputStream.putNextEntry(new ZipEntry("word/document.xml"));
			zipOutputStream.write(documentXml.getBytes(StandardCharsets.UTF_8));
			zipOutputStream.closeEntry();

			zipOutputStream.finish();
			return outputStream.toByteArray();
		}
	}

	private Map<String, byte[]> unzip(byte[] bytes) throws IOException {
		Map<String, byte[]> entries = new HashMap<>();
		try (ZipInputStream inputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
			ZipEntry entry;
			while ((entry = inputStream.getNextEntry()) != null) {
				entries.put(entry.getName(), inputStream.readAllBytes());
				inputStream.closeEntry();
			}
		}
		return entries;
	}

	private String readDocumentXml(Path docxPath) throws IOException {
		try (ZipInputStream inputStream = new ZipInputStream(Files.newInputStream(docxPath))) {
			ZipEntry entry;
			while ((entry = inputStream.getNextEntry()) != null) {
				if ("word/document.xml".equals(entry.getName())) {
					return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
				}
			}
		}
		throw new IllegalStateException("word/document.xml not found in " + docxPath);
	}

	private String parecerTemplateXml() {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
			+ "<w:p><w:r><w:t>Expediente de Atendimento de Aproveitamento de Estudo nº:</w:t></w:r></w:p>"
			+ "<w:p><w:r><w:t>Fábio Assato Rossi</w:t></w:r></w:p>"
			+ "<w:p><w:r><w:t>Habilitação Profissional de Técnico em Desenvolvimento de Sistemas</w:t></w:r></w:p>"
			+ "<w:p><w:r><w:t>Em conformidade com a Portaria do Sr(a). Diretor(a) da Etec, no 15/08/2022</w:t></w:r></w:p>"
			+ "<w:p><w:r><w:t>Wagner França Marques</w:t></w:r></w:p>"
			+ "<w:p><w:r><w:t>Ralf Gerônimo</w:t></w:r></w:p>"
			+ "<w:p><w:r><w:t>João Paulo Frias</w:t></w:r></w:p>"
			+ "<w:p><w:pPr><w:jc w:val=\"both\"/></w:pPr><w:r><w:t>Após analise documental a comissão entendeu que</w:t></w:r><w:r><w:t xml:space=\"preserve\"> a </w:t></w:r><w:r><w:t>Inglês Instrumental</w:t></w:r><w:r><w:t>, pode ser deferida</w:t></w:r></w:p>"
			+ "<w:p><w:r><w:t>Inglês Instrumental</w:t></w:r></w:p>"
			+ "</w:body></w:document>";
	}

	private String despachoTemplateXml() {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
			+ "<w:p><w:r><w:t>Considerando o Parecer da Comissão de Professores, juntado às folhas</w:t></w:r><w:r><w:t xml:space=\"preserve\"> 01</w:t></w:r></w:p>"
			+ "<w:p><w:r><w:t xml:space=\"preserve\">O (A) Diretor (a) da Etec  ZONA LESTE </w:t></w:r></w:p>"
			+ "<w:p><w:r><w:t>Fábio Assato Rossi</w:t></w:r></w:p>"
			+ "<w:p><w:r><w:t xml:space=\"preserve\"> regularmente matriculado no (a) Curso Técnico de Desenvolvimento de Sistemas Noturno</w:t></w:r></w:p>"
			+ "<w:p><w:r><w:t>Inglês Instrumental</w:t></w:r></w:p>"
			+ "<w:p><w:r><w:t>deferido o aproveitamento de estudos</w:t></w:r></w:p>"
			+ "</w:body></w:document>";
	}
}
