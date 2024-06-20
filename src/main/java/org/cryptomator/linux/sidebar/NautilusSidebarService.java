package org.cryptomator.linux.sidebar;

import org.cryptomator.integrations.common.CheckAvailability;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.sidebar.SidebarService;
import org.cryptomator.integrations.sidebar.SidebarServiceException;

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
public class NautilusSidebarService implements SidebarService {

	private static final int MAX_FILE_SIZE = 4096;
	private static final Path BOOKMARKS_FILE = Path.of(System.getProperty("user.home"), ".config/gtk-3.0/bookmarks");
	private static final Path TMP_FILE = BOOKMARKS_FILE.resolveSibling("bookmarks.cryptomator.tmp");
	private static final Lock BOOKMARKS_LOCK = new ReentrantReadWriteLock().writeLock();

	@Override
	public SidebarEntry add(Path target, String displayName) throws SidebarServiceException {
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
			return new NautilusSidebarEntry(entryLine);
		} catch (IOException e) {
			throw new SidebarServiceException("Adding entry to Nautilus bookmarks file failed.", e);
		} finally {
			BOOKMARKS_LOCK.unlock();
		}
	}

	static class NautilusSidebarEntry implements SidebarEntry {

		private final String line;
		private volatile boolean isRemoved = false;

		NautilusSidebarEntry(String line) {
			this.line = line;
		}

		@Override
		public void remove() throws SidebarServiceException {
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
				throw new SidebarServiceException("Removing entry from Nautilus bookmarks file failed", e);
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
