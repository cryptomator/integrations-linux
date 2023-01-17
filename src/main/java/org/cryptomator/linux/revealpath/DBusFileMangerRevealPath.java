package org.cryptomator.linux.revealpath;

import org.cryptomator.integrations.revealpath.RevealFailedException;
import org.cryptomator.integrations.revealpath.RevealPathService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DBusFileMangerRevealPath implements RevealPathService {

	private static final String FOR_FOLDERS = "org.freedesktop.FileManager1.ShowFolders";
	private static final String FOR_FILES = "org.freedesktop.FileManager1.ShowItems";
	private static final int TIMEOUT_THRESHOLD=5000;

	@Override
	public void reveal(Path path) throws RevealFailedException {
		try {
			var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			var uriPath = Arrays.stream(path.toUri().getPath().split("/")).map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")).collect(Collectors.joining("/"));
			ProcessBuilder pb = new ProcessBuilder().command("dbus-send",
					"--print-reply",
					"--reply-timeout="+TIMEOUT_THRESHOLD,
					"--dest=org.freedesktop.FileManager1",
					"--type=method_call",
					"/org/freedesktop/FileManager1",
					attrs.isDirectory() ? FOR_FOLDERS : FOR_FILES,
					String.format("array:string:file://%s", uriPath),
					"string:\"\""
			);
			var process = pb.start();
			if (process.waitFor(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS)) {
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
		CountDownLatch waitBarrier = new CountDownLatch(3);
		ProcessBuilder builderExistsDbusSend = new ProcessBuilder().command("which", "dbus-send");
		ProcessBuilder builderExistsNautilus = new ProcessBuilder().command("which", "nautilus");
		ProcessBuilder builderExistsDolphin = new ProcessBuilder().command("which", "dolphin");
		try {
			var existsDbusSend = builderExistsDbusSend.start();
			existsDbusSend.onExit().thenRun(waitBarrier::countDown);
			var existsNautilus = builderExistsNautilus.start();
			existsNautilus.onExit().thenRun(waitBarrier::countDown);
			var existsDolphin = builderExistsDolphin.start();
			existsDolphin.onExit().thenRun(waitBarrier::countDown);
			if (waitBarrier.await(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS)) {
				return existsDbusSend.exitValue() == 0 && (existsNautilus.exitValue() == 0 | existsDolphin.exitValue() == 0);
			}
		} catch (IOException | InterruptedException e) {
			//NO-OP
		}
		return false;
	}

}
