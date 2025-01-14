package org.cryptomator.linux.quickaccess;

import org.cryptomator.integrations.common.CheckAvailability;
import org.cryptomator.integrations.common.DisplayName;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.quickaccess.QuickAccessService;
import org.cryptomator.integrations.quickaccess.QuickAccessServiceException;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Implemenation of the {@link QuickAccessService} for KDE desktop environments using Dolphin file browser.
 */
@DisplayName("KDE Dolphin Places")
@CheckAvailability
@OperatingSystem(OperatingSystem.Value.LINUX)
@Priority(90)
public class DolphinPlaces extends FileConfiguredQuickAccess implements QuickAccessService {

	private static final int MAX_FILE_SIZE = 1 << 20; //1MiB, xml is quite verbose
	private static final Path PLACES_FILE = Path.of(System.getProperty("user.home"), ".local/share/user-places.xbel");
	private static final String ENTRY_TEMPLATE = """
			<bookmark href=\"%s\">
			 <title>%s</title>
			 <info>
			  <metadata owner=\"http://freedesktop.org\">
			   <bookmark:icon name="drive-harddisk-encrypted"/>
			  </metadata>
			  <metadata owner=\"https://cryptomator.org\">
			  	<id>%s</id>
			  </metadata>
			 </info>
			</bookmark>""";

	private static final Validator XML_VALIDATOR;

	static {
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		try (var schemaDefinition = DolphinPlaces.class.getResourceAsStream("/xbel-1.0.xsd")) {
			Source schemaFile = new StreamSource(schemaDefinition);
			XML_VALIDATOR = factory.newSchema(schemaFile).newValidator();
		} catch (IOException | SAXException e) {
			throw new IllegalStateException("Failed to load included XBEL schema definition file.", e);
		}
	}

	//SPI constructor
	public DolphinPlaces() {
		super(PLACES_FILE, MAX_FILE_SIZE);
	}

	@Override
	EntryAndConfig addEntryToConfig(String config, Path target, String displayName) throws QuickAccessServiceException {
		try {
			String id = UUID.randomUUID().toString();
			//validate
			XML_VALIDATOR.validate(new StreamSource(new StringReader(config)));
			// modify
			int insertIndex = config.lastIndexOf("</xbel"); //cannot be -1 due to validation; we do not match the whole end tag, since between tag name and closing bracket can be whitespaces
			var adjustedConfig = config.substring(0, insertIndex) //
					+ "\n" //
					+ ENTRY_TEMPLATE.formatted(target.toUri(), displayName, id).indent(1) //
					+ "\n" //
					+ config.substring(insertIndex);
			return new EntryAndConfig(new DolphinPlacesEntry(id), adjustedConfig);
		} catch (SAXException | IOException e) {
			throw new QuickAccessServiceException("Adding entry to KDE places file failed.", e);
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
				int idIndex = config.lastIndexOf(id);
				if (idIndex == -1) {
					return config; //assume someone has removed our entry, nothing to do
				}
				//validate
				XML_VALIDATOR.validate(new StreamSource(new StringReader(config)));
				//modify
				int openingTagIndex = indexOfEntryOpeningTag(config, idIndex);
				var contentToWrite1 = config.substring(0, openingTagIndex).stripTrailing();

				int closingTagEndIndex = config.indexOf('>', config.indexOf("</bookmark", idIndex));
				var part2Tmp = config.substring(closingTagEndIndex + 1).split("\\A\\v+", 2); //removing leading vertical whitespaces, but no indentation
				var contentToWrite2 = part2Tmp[part2Tmp.length - 1];

				return contentToWrite1 + "\n" + contentToWrite2;
			} catch (IOException | SAXException | IllegalStateException e) {
				throw new QuickAccessServiceException("Removing entry from KDE places file failed.", e);
			}
		}

		/**
		 * Returns the start index (inclusive)  of the {@link DolphinPlaces#ENTRY_TEMPLATE} entry
		 * @param placesContent the content of the XBEL places file
		 * @param idIndex start index (inclusive) of the entrys id tag value
		 * @return start index of the first bookmark tag, searching backwards from idIndex
		 */
		private int indexOfEntryOpeningTag(String placesContent, int idIndex) {
			var xmlWhitespaceChars = List.of(' ', '\t', '\n');
			for (char c : xmlWhitespaceChars) {
				int idx = placesContent.lastIndexOf("<bookmark" + c, idIndex); //with the whitespace we ensure, that no tags starting with "bookmark" (e.g. bookmarkz) are selected
				if (idx != -1) {
					return idx;
				}
			}
			throw new IllegalStateException("Found entry id " + id + " in " + PLACES_FILE + ", but it is not a child of <bookmark> tag.");
		}
	}

	@CheckAvailability
	public static boolean isSupported() {
		return Files.exists(PLACES_FILE);
	}
}
