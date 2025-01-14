package org.cryptomator.linux.quickaccess;

import org.cryptomator.integrations.quickaccess.QuickAccessService;
import org.cryptomator.integrations.quickaccess.QuickAccessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

abstract class FileConfiguredQuickAccess implements QuickAccessService {

	private static final Logger LOG = LoggerFactory.getLogger(FileConfiguredQuickAccess.class);

	private final int maxFileSize;
	private final Path configFile;
	private final Path tmpFile;
	private final Lock modifyLock = new ReentrantReadWriteLock().writeLock();

	FileConfiguredQuickAccess(Path configFile, int maxFileSize) {
		this.configFile = configFile;
		this.maxFileSize = maxFileSize;
		this.tmpFile = configFile.resolveSibling("." + configFile.getFileName() + ".cryptomator.tmp");
		Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
	}

	@Override
	public QuickAccessEntry add(Path target, String displayName) throws QuickAccessServiceException {
		try {
			modifyLock.lock();
			var entryAndConfig = addEntryToConfig(readConfig(), target, displayName);
			persistConfig(entryAndConfig.config());
			return entryAndConfig.entry();
		} catch (IOException e) {
			throw new QuickAccessServiceException("Failed to add entry to %s.".formatted(configFile), e);
		} finally {
			modifyLock.unlock();
		}
	}

	record EntryAndConfig(FileConfiguredQuickAccessEntry entry, String config) {
	}

	abstract EntryAndConfig addEntryToConfig(String config, Path target, String displayName) throws QuickAccessServiceException;


	protected abstract class FileConfiguredQuickAccessEntry implements QuickAccessEntry {

		private volatile boolean isRemoved = false;

		@Override
		public void remove() throws QuickAccessServiceException {
			try {
				modifyLock.lock();
				if (isRemoved) {
					return;
				}
				checkFileSize();
				var config = readConfig();
				var adjustedConfig = removeEntryFromConfig(config);
				persistConfig(adjustedConfig);
				isRemoved = true;
			} catch (IOException e) {
				throw new QuickAccessServiceException("Failed to remove entry to %s.".formatted(configFile), e);
			} finally {
				modifyLock.unlock();
			}
		}

		abstract String removeEntryFromConfig(String config) throws QuickAccessServiceException;
	}

	private String readConfig() throws IOException {
		return Files.readString(configFile, StandardCharsets.UTF_8);
	}

	private void persistConfig(String newConfig) throws IOException {
		Files.writeString(tmpFile, newConfig, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		try {
			Files.move(tmpFile, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmpFile, configFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void checkFileSize() throws IOException {
		if (Files.size(configFile) > maxFileSize) {
			throw new IOException("File %s exceeds size of %d bytes".formatted(configFile, maxFileSize));
		}
	}

	private void cleanup() {
		try {
			Files.deleteIfExists(tmpFile);
		} catch (IOException e) {
			LOG.warn("Unable to delete {}. Need to be deleted manually.", tmpFile);
		}
	}
}
