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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implemenation of the {@link QuickAccessService} for KDE desktop environments using Dolphin file browser.
 */
@DisplayName("KDE Dolphin Places")
@CheckAvailability
@OperatingSystem(OperatingSystem.Value.LINUX)
@Priority(90)
public class DolphinPlaces implements QuickAccessService {

	private static final int MAX_FILE_SIZE = 1 << 20; //xml is quite verbose
	private static final Path PLACES_FILE = Path.of(System.getProperty("user.home"), ".local/share/user-places.xbel");
	private static final Path TMP_FILE = Path.of(System.getProperty("java.io.tmpdir"), "user-places.xbel.cryptomator.tmp");
	private static final Lock MODIFY_LOCK = new ReentrantLock();
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


	@Override
	public QuickAccessService.QuickAccessEntry add(Path target, String displayName) throws QuickAccessServiceException {
		String id = UUID.randomUUID().toString();
		try {
			MODIFY_LOCK.lock();
			if (Files.size(PLACES_FILE) > MAX_FILE_SIZE) {
				throw new IOException("File %s exceeds size of %d bytes".formatted(PLACES_FILE, MAX_FILE_SIZE));
			}
			var placesContent = Files.readString(PLACES_FILE);
			//validate
			XML_VALIDATOR.validate(new StreamSource(new StringReader(placesContent)));
			// modify
			int insertIndex = placesContent.lastIndexOf("</xbel"); //cannot be -1 due to validation; we do not match the end tag, since betweent tag name and closing bracket can be whitespaces
			try (var writer = Files.newBufferedWriter(TMP_FILE, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				writer.write(placesContent, 0, insertIndex);
				writer.newLine();
				writer.write(ENTRY_TEMPLATE.formatted(target.toUri(), displayName, id).indent(1));
				writer.newLine();
				writer.write(placesContent, insertIndex, placesContent.length() - insertIndex);
			}
			// save
			Files.move(TMP_FILE, PLACES_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return new DolphinPlacesEntry(id);
		} catch (SAXException | IOException e) {
			throw new QuickAccessServiceException("Adding entry to KDE places file failed.", e);
		} finally {
			MODIFY_LOCK.unlock();
		}
	}

	private static class DolphinPlacesEntry implements QuickAccessEntry {

		private final String id;
		private volatile boolean isRemoved = false;

		DolphinPlacesEntry(String id) {
			this.id = id;
		}

		@Override
		public void remove() throws QuickAccessServiceException {
			try {
				MODIFY_LOCK.lock();
				if (isRemoved) {
					return;
				}
				if (Files.size(PLACES_FILE) > MAX_FILE_SIZE) {
					throw new IOException("File %s exceeds size of %d bytes".formatted(PLACES_FILE, MAX_FILE_SIZE));
				}
				var placesContent = Files.readString(PLACES_FILE);
				int idIndex = placesContent.lastIndexOf(id);
				if (idIndex == -1) {
					isRemoved = true;
					return; //we assume someone has removed our entry
				}
				//validate
				XML_VALIDATOR.validate(new StreamSource(new StringReader(placesContent)));
				//modify
				int openingTagIndex = indexOfEntryOpeningTag(placesContent, idIndex);
				var contentToWrite1 = placesContent.substring(0, openingTagIndex).stripTrailing();

				int closingTagEndIndex = placesContent.indexOf('>', placesContent.indexOf("</bookmark", idIndex));
				var part2Tmp = placesContent.substring(closingTagEndIndex + 1).split("\\A\\v+", 2); //removing leading vertical whitespaces, but no indentation
				var contentToWrite2 = part2Tmp[part2Tmp.length - 1];

				try (var writer = Files.newBufferedWriter(TMP_FILE, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
					writer.write(contentToWrite1);
					writer.newLine();
					writer.write(contentToWrite2);
				}
				// save
				Files.move(TMP_FILE, PLACES_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				isRemoved = true;
			} catch (IOException | SAXException e) {
				throw new QuickAccessServiceException("Removing entry from KDE places file failed.", e);
			} finally {
				MODIFY_LOCK.unlock();
			}
		}

		private int indexOfEntryOpeningTag(String placesContent, int idIndex) {
			var xmlWhitespaceChars = List.of(' ', '\t', '\n');
			for (char c : xmlWhitespaceChars) {
				int idx = placesContent.lastIndexOf("<bookmark" + c, idIndex);
				if (idx != -1) {
					return idx;
				}
			}
			throw new IllegalStateException("File " + PLACES_FILE + " is valid xbel file, but does not contain opening bookmark tag.");
		}
	}

	@CheckAvailability
	public static boolean isSupported() {
		return Files.exists(PLACES_FILE);
	}
}
