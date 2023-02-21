package org.cryptomator.linux.revealpath;

import com.google.common.base.Preconditions;
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

/**
 * RevealPathService provider using the <a href="https://freedesktop.org/wiki/Specifications/file-manager-interface/">DBus freedesktop FileManager1 interface</a> and dbus-send command.
 */
public class DBusSendRevealPathService implements RevealPathService {

	private static final String FILEMANAGER1_XML_ELEMENT = "<interface name=\"org.freedesktop.FileManager1\">";
	private static final String FOR_FOLDERS = "org.freedesktop.FileManager1.ShowFolders";
	private static final String FOR_FILES = "org.freedesktop.FileManager1.ShowItems";
	private static final int TIMEOUT_THRESHOLD = 5000;

	@Override
	public void reveal(Path path) throws RevealFailedException {
		try {
			var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			var uriPath = Arrays.stream(path.toUri().getPath().split("/")).map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")).collect(Collectors.joining("/"));
			ProcessBuilder pb = new ProcessBuilder().command("dbus-send",
					"--print-reply",
					"--reply-timeout=" + TIMEOUT_THRESHOLD,
					"--dest=org.freedesktop.FileManager1",
					"--type=method_call",
					"/org/freedesktop/FileManager1",
					attrs.isDirectory() ? FOR_FOLDERS : FOR_FILES,
					String.format("array:string:file://%s", uriPath),
					"string:\"\""
			);
			var process = pb.start();
			try (var reader = process.errorReader()) {
				if (process.waitFor(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS)) {
					int exitValue = process.exitValue();
					if (process.exitValue() != 0) {
						String error = reader.lines().collect(Collectors.joining());
						throw new RevealFailedException("dbus-send exited with code " + exitValue + " and error message: " + error);
					}
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
		CountDownLatch waitBarrier = new CountDownLatch(2);
		ProcessBuilder dbusSendExistsBuilder = new ProcessBuilder().command("test", " `command -v dbus-send`");
		ProcessBuilder fileManager1ExistsBuilder = createFileManager1Check();

		try {
			var dbusSendExists = dbusSendExistsBuilder.start();
			dbusSendExists.onExit().thenRun(waitBarrier::countDown);
			var fileManager1Exists = fileManager1ExistsBuilder.start();
			fileManager1Exists.onExit().thenRun(waitBarrier::countDown);

			if (waitBarrier.await(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS)) {
				if (dbusSendExists.exitValue() == 0 && fileManager1Exists.exitValue() == 0) {
					return parseOutputForFileManagerInterface(fileManager1Exists);
				}
			}
		} catch (IOException | InterruptedException e) {
			//NO-OP
		}
		return false;
	}

	/**
	 * Parses process stdout to see if the answer contains "{@value FILEMANAGER1_XML_ELEMENT}".
	 * DBus introspection output is defined in the <a href="https://dbus.freedesktop.org/doc/dbus-specification.html#introspection-format">dbus spec</a>.
	 *
	 * @param fileManager1Process The already exited process for checking the FileManager1 interface
	 * @return {@code true} if the interface is found in the introspection output, otherwise false
	 * @throws IOException if the Inputer reader on the process output cannot be created
	 */
	private boolean parseOutputForFileManagerInterface(Process fileManager1Process) throws IOException {
		Preconditions.checkState(!fileManager1Process.isAlive());
		try (var reader = fileManager1Process.inputReader(StandardCharsets.UTF_8)) {
			return reader.lines().map(String::trim).anyMatch(FILEMANAGER1_XML_ELEMENT::equals);
		}
	}

	private static ProcessBuilder createFileManager1Check() {
		return new ProcessBuilder().command(
				"dbus-send",
				"--session",
				"--print-reply",
				"--reply-timeout=" + TIMEOUT_THRESHOLD,
				"--dest=org.freedesktop.FileManager1",
				"--type=method_call",
				"/org/freedesktop/FileManager1",
				"org.freedesktop.DBus.Introspectable.Introspect"
		);
	}
}
