package org.cryptomator.linux.quickaccess;

import org.cryptomator.integrations.common.CheckAvailability;
import org.cryptomator.integrations.common.DisplayName;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.quickaccess.QuickAccessService;
import org.cryptomator.integrations.quickaccess.QuickAccessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathVariableResolver;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implemenation of the {@link QuickAccessService} for KDE desktop environments using Dolphin file browser.
 */
@DisplayName("KDE Dolphin Places")
@CheckAvailability
@OperatingSystem(OperatingSystem.Value.LINUX)
@Priority(90)
public class DolphinPlaces extends FileConfiguredQuickAccess implements QuickAccessService {

	private static final Logger LOG = LoggerFactory.getLogger(DolphinPlaces.class);

	private static final String XBEL_NAMESPACE = "http://www.freedesktop.org/standards/desktop-bookmarks";
	private static final int MAX_FILE_SIZE = 1 << 20; //1MiB, xml is quite verbose
	private static final String HOME_DIR = System.getProperty("user.home");
	private static final String CONFIG_PATH_IN_HOME = ".local/share";
	private static final String CONFIG_FILE_NAME = "user-places.xbel";
	private static final Path PLACES_FILE = Path.of(HOME_DIR,CONFIG_PATH_IN_HOME, CONFIG_FILE_NAME);

	private static final Schema XBEL_SCHEMA;

	static {

		try (var schemaDefinition = DolphinPlaces.class.getResourceAsStream("/xbel-1.0.xsd")) {

			SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			XBEL_SCHEMA = factory.newSchema(new StreamSource(schemaDefinition));

		} catch (IOException | SAXException e) {
			throw new IllegalStateException("Failed to load included XBEL schema definition file.", e);
		}
	}

	//SPI constructor
	public DolphinPlaces() {
		super(PLACES_FILE, MAX_FILE_SIZE);
	}

	public DolphinPlaces(Path configFilePath) {
		super(configFilePath.resolve(CONFIG_FILE_NAME), MAX_FILE_SIZE);
	}

	@Override
	EntryAndConfig addEntryToConfig(String config, Path target, String displayName) throws QuickAccessServiceException {

		try {

			String id = UUID.randomUUID().toString();

			var validator = XBEL_SCHEMA.newValidator();

			LOG.trace("Adding bookmark for target: '{}', displayName: '{}', id: '{}'", target, displayName, id);

			validator.validate(new StreamSource(new StringReader(config)));

			Document xmlDocument = loadXmlDocument(config);

			NodeList nodeList = extractBookmarksByPath(target, xmlDocument);

			removeStaleBookmarks(nodeList);

			createBookmark(target, displayName, id, xmlDocument);

			validator.validate(new StreamSource(new StringReader(config)));

			return new EntryAndConfig(new DolphinPlacesEntry(id), documentToString(xmlDocument));

		} catch (SAXException e) {
			throw new QuickAccessServiceException("Invalid structure in xbel bookmark file", e);
		} catch (IOException e) {
			throw new QuickAccessServiceException("Failed reading/writing the xbel bookmark file", e);
		}
	}

	private void removeStaleBookmarks(NodeList nodeList) {

		for (int i = nodeList.getLength() - 1; i >= 0; i--) {
			Node node = nodeList.item(i);
			node.getParentNode().removeChild(node);
		}
	}

	private NodeList extractBookmarksByPath(Path target, Document xmlDocument) throws QuickAccessServiceException {

		try {

			XPathFactory xpathFactory = XPathFactory.newInstance();
			XPath xpath = xpathFactory.newXPath();

			SimpleVariableResolver variableResolver = new SimpleVariableResolver();

			variableResolver.addVariable(new QName("uri"), target.toUri().toString());

			xpath.setXPathVariableResolver(variableResolver);

			String expression = "/xbel/bookmark[info/metadata[@owner='https://cryptomator.org']][@href=$uri]";

			return (NodeList) xpath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);

		} catch (Exception e) {
			throw new QuickAccessServiceException("Failed to extract bookmarks by path", e);
		}
	}

	private NodeList extractBookmarksById(String id, Document xmlDocument) throws QuickAccessServiceException {

		try {

			XPathFactory xpathFactory = XPathFactory.newInstance();
			XPath xpath = xpathFactory.newXPath();

			SimpleVariableResolver variableResolver = new SimpleVariableResolver();

			variableResolver.addVariable(new QName("id"), id);

			xpath.setXPathVariableResolver(variableResolver);

			String expression = "/xbel/bookmark[info/metadata[@owner='https://cryptomator.org']][info/metadata/id[text()=$id]]";

			return (NodeList) xpath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);

		} catch (Exception e) {
  			throw new QuickAccessServiceException("Failed to extract bookmarks by id", e);
		}
	}

	private Document loadXmlDocument(String config) throws QuickAccessServiceException {

		try {

			DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();

			builderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			builderFactory.setXIncludeAware(false);
			builderFactory.setExpandEntityReferences(false);
			builderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			builderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			builderFactory.setNamespaceAware(true);

			DocumentBuilder builder = builderFactory.newDocumentBuilder();

			// Prevent external entities from being resolved
			builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));

			return builder.parse(new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8)));

		} catch (Exception e) {
			throw new QuickAccessServiceException("Failed to parse the xbel bookmark file", e);
		}
	}

	private String documentToString(Document xmlDocument) throws QuickAccessServiceException {

		try {

			StringWriter buf = new StringWriter();

			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "");
			transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

			transformer.transform(new DOMSource(xmlDocument), new StreamResult(buf));

			var content = buf.toString();
			content = content.replaceFirst("\\s*standalone=\"(yes|no)\"", "");
			content = content.replace("<!DOCTYPE xbel PUBLIC \"\" \"\">","<!DOCTYPE xbel>");

			return content;

		} catch (Exception e) {
			throw new QuickAccessServiceException("Failed to read document into string", e);
		}
	}

	/**
	 *
	 * Adds a xml bookmark element to the specified xml document
	 *
	 * <pre>{@code
	 * <bookmark href="file:///home/someuser/folder1/">
	 *   <title>integrations-linux</title>
	 *   <info>
	 *     <metadata owner="http://freedesktop.org">
	 *       <bookmark:icon name="drive-harddisk-encrypted"/>
	 *     </metadata>
	 *     <metadata owner="https://cryptomator.org">
	 *       <id>sldkf-sadf-sadf-sadf</id>
	 *     </metadata>
	 *   </info>
	 * </bookmark>
	 * }</pre>
	 *
	 * @param target The mount point of the vault
	 * @param displayName Caption of the vault link in dolphin
	 * @param xmlDocument The xbel document to which the bookmark should be added
	 *
	 * @throws QuickAccessServiceException if the bookmark could not be created
	 */
	private void createBookmark(Path target, String displayName, String id, Document xmlDocument) throws QuickAccessServiceException {

		try {
			var bookmark = xmlDocument.createElement("bookmark");
			var title = xmlDocument.createElement("title");
			var info = xmlDocument.createElement("info");
			var metadataBookmark = xmlDocument.createElement("metadata");
			var metadataOwner = xmlDocument.createElement("metadata");
			var bookmarkIcon = xmlDocument.createElementNS(XBEL_NAMESPACE, "bookmark:icon");
			var idElem = xmlDocument.createElement("id");

			bookmark.setAttribute("href", target.toUri().toString());

			title.setTextContent(displayName);

			bookmark.appendChild(title);
			bookmark.appendChild(info);

			info.appendChild(metadataBookmark);
			info.appendChild(metadataOwner);

			metadataBookmark.appendChild(bookmarkIcon);
			metadataOwner.appendChild(idElem);

			metadataBookmark.setAttribute("owner", "http://freedesktop.org");

			bookmarkIcon.setAttribute("name","drive-harddisk-encrypted");

			metadataOwner.setAttribute("owner", "https://cryptomator.org");

			idElem.setTextContent(id);
			xmlDocument.getDocumentElement().appendChild(bookmark);

		} catch (Exception e) {
			throw new QuickAccessServiceException("Failed to insert bookmark for target: " + target, e);
		}
	}

	private class DolphinPlacesEntry extends FileConfiguredQuickAccessEntry implements QuickAccessEntry {

		private final String id;

		DolphinPlacesEntry(String id) {
			this.id = id;
		}

		@Override
		public String removeEntryFromConfig(String config) throws QuickAccessServiceException {

			try {
				var validator = XBEL_SCHEMA.newValidator();

				validator.validate(new StreamSource(new StringReader(config)));

				Document xmlDocument = loadXmlDocument(config);

				NodeList nodeList = extractBookmarksById(id, xmlDocument);

				removeStaleBookmarks(nodeList);

				validator.validate(new StreamSource(new StringReader(config)));

				return documentToString(xmlDocument);

			} catch (IOException | SAXException | IllegalStateException e) {
				throw new QuickAccessServiceException("Removing entry from KDE places file failed.", e);
			}
		}
	}

	/**
	 * Resolver in order to define parameter for XPATH expression.
	 */
	private class SimpleVariableResolver implements XPathVariableResolver {

		private final Map<QName, Object> vars = new HashMap<>();

		/**
		 * Adds a variable to the resolver.
		 *
		 * @param name  The name of the variable
		 * @param value The value of the variable
		 */
		public void addVariable(QName name, Object value) {
			vars.put(name, value);
		}

		/**
		 * Resolves a variable by its name.
		 *
		 * @param variableName The name of the variable to resolve
		 * @return The value of the variable, or null if not found
		 */
		public Object resolveVariable(QName variableName) {
			return vars.get(variableName);
		}
	}

	@CheckAvailability
	public static boolean isSupported() {
		return Files.exists(PLACES_FILE);
	}
}
