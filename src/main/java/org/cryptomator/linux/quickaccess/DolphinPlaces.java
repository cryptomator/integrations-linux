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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implemenation of the {@link QuickAccessService} for KDE desktop environments using Dolphin file browser.
 */
@DisplayName("KDE Dolphin Places")
@OperatingSystem(OperatingSystem.Value.LINUX)
@Priority(90)
public class DolphinPlaces implements QuickAccessService {

	private static final int MAX_FILE_SIZE = 1 << 15; //xml is quite verbose
	private static final Path PLACES_FILE = Path.of(System.getProperty("user.home"), ".local/share/user-places.xbel");
	private static final Path TMP_FILE = Path.of(System.getProperty("java.io.tmpdir"), "user-places.xbel.cryptomator.tmp");
	private static final Lock MODIFY_LOCK = new ReentrantLock();
	private static final String ENTRY_TEMPLATE = """
			<bookmark href="%s">
			 <title>%s</title>
			 <info>
			  <metadata owner="http://freedesktop.org">
			   <bookmark:icon name="drive-harddisk"/>
			  </metadata>
			  <metadata owner="https://cryptomator.org">
			  	<id>%s</id>
			  </metadata>
			 </info>
			</bookmark>""";


	private static final Validator xmlValidator;

	static {
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		try (var schemaDefinition = DolphinPlaces.class.getResourceAsStream("/xbel-1.0.xsd")) {
			Source schemaFile = new StreamSource(schemaDefinition);
			xmlValidator = factory.newSchema(schemaFile).newValidator();
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
			xmlValidator.validate(new StreamSource(new StringReader(placesContent)));
			// modify
			int insertIndex = placesContent.lastIndexOf("</xbel>"); //cannot be -1 due to validation
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
				xmlValidator.validate(new StreamSource(new StringReader(placesContent)));
				//modify
				var placesContentPart1 = placesContent.substring(0, idIndex);
				int openingTagIndex = placesContentPart1.lastIndexOf("<bookmark href=");
				var contentToWrite1 = placesContentPart1.substring(0, openingTagIndex).stripTrailing();

				int closingTagIndex = placesContent.indexOf("</bookmark>", idIndex);
				var part2Tmp = placesContent.substring(closingTagIndex + "</bookmark>".length()).split("\\v*", 2); //removing leading vertical whitespaces
				var contentToWrite2 = part2Tmp.length == 1 ? part2Tmp[0] : part2Tmp[1];

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
	}

	@CheckAvailability
	public static boolean isSupported() {
		try {
			var nautilusExistsProc = new ProcessBuilder().command("test", "`command -v dolphin`").start();
			if (nautilusExistsProc.waitFor(5000, TimeUnit.MILLISECONDS)) {
				return nautilusExistsProc.exitValue() == 0;
			}
		} catch (IOException | InterruptedException e) {
			//NO-OP
		}
		return false;
	}
}
