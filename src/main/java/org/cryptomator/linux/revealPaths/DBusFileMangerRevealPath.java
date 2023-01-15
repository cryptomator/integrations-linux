package org.cryptomator.linux.revealPaths;

import org.cryptomator.integrations.revealpath.RevealFailedException;
import org.cryptomator.integrations.revealpath.RevealPathService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

public class DBusFileMangerRevealPath implements RevealPathService {

	private static final String FOR_FOLDERS = "org.freedesktop.FileManager1.ShowFolders";
	private static final String FOR_FILES = "org.freedesktop.FileManager1.ShowItems";

	@Override
	public void reveal(Path path) throws RevealFailedException {
		try {
			var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			ProcessBuilder pb = new ProcessBuilder().command("dbus-send",
					"--dest=org.freedesktop.FileManager1",
					"--type=method_call",
					"/org/freedesktop/FileManager1",
					attrs.isDirectory() ? FOR_FOLDERS : FOR_FILES,
					String.format("array:string:\"%s\"", path.toUri()),
					"string:\"\""
			);
			var process = pb.start();
			if (process.waitFor(5000, TimeUnit.MILLISECONDS)) {
				int exitValue = process.exitValue();
				if (exitValue != 0) {
					throw new RevealFailedException("dbus-send returned with code" + exitValue);
				}
			}
		} catch (IOException e) {
			throw new RevealFailedException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RevealFailedException(e);
		}
	}

	@Override
	public boolean isSupported() {
		ProcessBuilder builderExistsDbusSend = new ProcessBuilder().command("which", "dbus-send");
		ProcessBuilder builderExistsNautilus = new ProcessBuilder().command("which", "nautilus");
		ProcessBuilder builderExistsDolphin = new ProcessBuilder().command("which", "dolphin");
		try {
			var existsDbusSend = builderExistsDbusSend.start();
			var existsNautilus = builderExistsNautilus.start();
			var existsDolphin = builderExistsDolphin.start();
			if (existsDbusSend.waitFor(5000, TimeUnit.MILLISECONDS) && existsDolphin.waitFor(5000, TimeUnit.MILLISECONDS) && existsDolphin.waitFor(5000, TimeUnit.MILLISECONDS)) {
				return existsDbusSend.exitValue() == 0 && (existsNautilus.exitValue() == 0 | existsDolphin.exitValue() == 0);
			}
		} catch (IOException | InterruptedException e) {
			//NO-OP
		}
		return false;
	}

}
