package fzlbpms.orbeonforms;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Turns a form definition into a Form Runner *data* document — the tree Form
 * Runner reads when opening /fr/{app}/{form}/edit/{documentId}.
 *
 * The empty tree is the form definition's own {@code fr-form-instance}; values
 * are filled in by matching a leaf element's name against a process variable of
 * the same name. Variables with no matching field (Flowable adds plenty, e.g.
 * createMoodleUserResponseHeaders) are simply ignored.
 */
final class FormData {

	private FormData() {
	}

	/** Extracts the blank data tree and fills it from {@code variables}. */
	static String buildPrefilled(String formDefinitionXhtml, Map<String, Object> variables) throws Exception {
		Document def = parse(formDefinitionXhtml);
		Element instanceData = findFormInstanceData(def);
		if (instanceData == null) {
			throw Http.serverError("Definição do formulário não tem instance 'fr-form-instance'.");
		}

		Document out = newDocument();
		Node imported = out.importNode(instanceData, true);
		out.appendChild(imported);
		fill(out.getDocumentElement(), variables);
		return serialize(out);
	}

	/** Reads a submitted data tree back into a flat name -&gt; value map. */
	static Map<String, Object> toVariables(String dataXml) throws Exception {
		Map<String, Object> vars = new LinkedHashMap<>();
		collect(parse(dataXml).getDocumentElement(), vars);
		return vars;
	}

	// ---------- internals ----------

	/**
	 * Finds the single child element of {@code <xf:instance id="fr-form-instance">}
	 * (conventionally {@code <form>}), which is the blank data tree.
	 */
	private static Element findFormInstanceData(Document def) {
		NodeList all = def.getElementsByTagName("*");
		for (int i = 0; i < all.getLength(); i++) {
			Node n = all.item(i);
			if (!(n instanceof Element)) continue;
			Element e = (Element) n;
			if (!"instance".equals(localName(e))) continue;
			if (!"fr-form-instance".equals(e.getAttribute("id"))) continue;
			for (Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) {
				if (c instanceof Element) return (Element) c;
			}
		}
		return null;
	}

	/** Depth-first: a leaf element takes the value of the variable sharing its name. */
	private static void fill(Element el, Map<String, Object> variables) {
		boolean hasChildElements = false;
		for (Node c = el.getFirstChild(); c != null; c = c.getNextSibling()) {
			if (c instanceof Element) {
				hasChildElements = true;
				fill((Element) c, variables);
			}
		}
		if (hasChildElements) return;

		Object value = variables.get(localName(el));
		if (value != null) {
			el.setTextContent(String.valueOf(value));
		}
	}

	private static void collect(Element el, Map<String, Object> out) {
		boolean hasChildElements = false;
		for (Node c = el.getFirstChild(); c != null; c = c.getNextSibling()) {
			if (c instanceof Element) {
				hasChildElements = true;
				collect((Element) c, out);
			}
		}
		if (!hasChildElements) {
			out.put(localName(el), el.getTextContent());
		}
	}

	private static String localName(Element e) {
		return e.getLocalName() != null ? e.getLocalName() : e.getNodeName();
	}

	private static Document parse(String xml) throws Exception {
		return builder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	private static Document newDocument() throws Exception {
		return builder().newDocument();
	}

	private static DocumentBuilder builder() throws Exception {
		DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
		f.setNamespaceAware(true);
		// The form definition is repo-controlled, but it is still XML from a
		// database — no external entities, no DTDs.
		f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		f.setFeature("http://xml.org/sax/features/external-general-entities", false);
		f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		f.setExpandEntityReferences(false);
		return f.newDocumentBuilder();
	}

	private static String serialize(Document doc) throws Exception {
		Transformer t = TransformerFactory.newInstance().newTransformer();
		t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		StringWriter w = new StringWriter();
		t.transform(new DOMSource(doc), new StreamResult(w));
		return w.toString();
	}
}
