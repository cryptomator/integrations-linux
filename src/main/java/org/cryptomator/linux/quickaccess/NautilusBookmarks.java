package org.cryptomator.linux.quickaccess;

import org.cryptomator.integrations.common.CheckAvailability;
import org.cryptomator.integrations.common.DisplayName;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.quickaccess.QuickAccessService;
import org.cryptomator.integrations.quickaccess.QuickAccessServiceException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Priority(100)
@CheckAvailability
@OperatingSystem(OperatingSystem.Value.LINUX)
@DisplayName("GNOME Nautilus Bookmarks")
public class NautilusBookmarks implements QuickAccessService {

	private static final int MAX_FILE_SIZE = 4096;
	private static final Path BOOKMARKS_FILE = Path.of(System.getProperty("user.home"), ".config/gtk-3.0/bookmarks");
	private static final Path TMP_FILE = BOOKMARKS_FILE.resolveSibling("bookmarks.cryptomator.tmp");
	private static final Lock BOOKMARKS_LOCK = new ReentrantReadWriteLock().writeLock();

	@Override
	public QuickAccessService.QuickAccessEntry add(Path target, String displayName) throws QuickAccessServiceException {
		String entryLine = "file://" + target.toAbsolutePath() + " " + displayName;
		try {
			BOOKMARKS_LOCK.lock();
			if (Files.size(BOOKMARKS_FILE) > MAX_FILE_SIZE) {
				throw new IOException("File %s exceeds size of %d bytes".formatted(BOOKMARKS_FILE, MAX_FILE_SIZE));
			}
			//by reading all lines, we ensure that each line is terminated with EOL
			var entries = Files.readAllLines(BOOKMARKS_FILE, StandardCharsets.UTF_8);
			entries.add(entryLine);
			Files.write(TMP_FILE, entries, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			Files.move(TMP_FILE, BOOKMARKS_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return new NautilusQuickAccessEntry(entryLine);
		} catch (IOException e) {
			throw new QuickAccessServiceException("Adding entry to Nautilus bookmarks file failed.", e);
		} finally {
			BOOKMARKS_LOCK.unlock();
		}
	}

	static class NautilusQuickAccessEntry implements QuickAccessEntry {

		private final String line;
		private volatile boolean isRemoved = false;

		NautilusQuickAccessEntry(String line) {
			this.line = line;
		}

		@Override
		public void remove() throws QuickAccessServiceException {
			try {
				BOOKMARKS_LOCK.lock();
				if (isRemoved) {
					return;
				}
				if (Files.size(BOOKMARKS_FILE) > MAX_FILE_SIZE) {
					throw new IOException("File %s exceeds size of %d bytes".formatted(BOOKMARKS_FILE, MAX_FILE_SIZE));
				}
				var entries = Files.readAllLines(BOOKMARKS_FILE);
				if (entries.remove(line)) {
					Files.write(TMP_FILE, entries, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
					Files.move(TMP_FILE, BOOKMARKS_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				}
				isRemoved = true;
			} catch (IOException e) {
				throw new QuickAccessServiceException("Removing entry from Nautilus bookmarks file failed", e);
			} finally {
				BOOKMARKS_LOCK.unlock();
			}
		}
	}

	@CheckAvailability
	public static boolean isSupported() {
		try {
			var nautilusExistsProc = new ProcessBuilder().command("test", "`command -v nautilus`").start();
			if (nautilusExistsProc.waitFor(5000, TimeUnit.MILLISECONDS)) {
				return nautilusExistsProc.exitValue() == 0;
			}
		} catch (IOException | InterruptedException e) {
			//NO-OP
		}
		return false;
	}
}
