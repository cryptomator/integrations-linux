package org.cryptomator.linux.quickaccess;

import org.cryptomator.integrations.common.CheckAvailability;
import org.cryptomator.integrations.common.DisplayName;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.quickaccess.QuickAccessService;
import org.cryptomator.integrations.quickaccess.QuickAccessServiceException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;

@Priority(100)
@CheckAvailability
@OperatingSystem(OperatingSystem.Value.LINUX)
@DisplayName("GNOME Nautilus Bookmarks")
public class NautilusBookmarks extends FileConfiguredQuickAccess implements QuickAccessService {

	private static final int MAX_FILE_SIZE = 4096;
	private static final Path BOOKMARKS_FILE = Path.of(System.getProperty("user.home"), ".config/gtk-3.0/bookmarks");

	//SPI constructor
	public NautilusBookmarks() {
		super(BOOKMARKS_FILE, MAX_FILE_SIZE);
	}

	@Override
	EntryAndConfig addEntryToConfig(String config, Path target, String displayName) throws QuickAccessServiceException {
		var uriPath = target.toAbsolutePath().toString().replace(" ", "%20");
		String entryLine = "file://" + uriPath + " " + displayName;
		var entry = new NautilusQuickAccessEntry(entryLine);
		var adjustedConfig = config.stripTrailing() +
				"\n" +
				entryLine;
		return new EntryAndConfig(entry, adjustedConfig);
	}

	class NautilusQuickAccessEntry extends FileConfiguredQuickAccessEntry implements QuickAccessEntry {

		private final String line;

		NautilusQuickAccessEntry(String line) {
			this.line = line;
		}

		@Override
		public String removeEntryFromConfig(String config) throws QuickAccessServiceException {
			return config.lines() //
					.map(l -> l.equals(line) ? null : l) //
					.filter(Objects::nonNull) //
					.collect(Collectors.joining("\n"));
		}
	}

	@CheckAvailability
	public static boolean isSupported() {
		return Files.exists(BOOKMARKS_FILE);
	}
}
