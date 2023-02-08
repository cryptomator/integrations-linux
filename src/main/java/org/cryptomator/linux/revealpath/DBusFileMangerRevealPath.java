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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DBusFileMangerRevealPath implements RevealPathService {

	private static final String[] FILEMANAGER_OBJECT_PATHS = {"/org/gnome/Nautilus", "/org/kde/dolphin", "/xfce/Thunar"};
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
		CountDownLatch waitBarrier = new CountDownLatch(FILEMANAGER_OBJECT_PATHS.length + 1);
		ProcessBuilder dbusSendExistBuilder = new ProcessBuilder().command("test", " `command -v dbus-send`");
		List<ProcessBuilder> fileManagerExistBuilders = Arrays.stream(FILEMANAGER_OBJECT_PATHS)
				.map(DBusFileMangerRevealPath::createDbusObjectCheck).toList();

		try {
			var existsDbusSend = dbusSendExistBuilder.start();
			existsDbusSend.onExit().thenRun(waitBarrier::countDown);
			//TODO: process process-output in paralell
			List<Process> fileManagerChecks = fileManagerExistBuilders.stream().map(builder -> {
				try {
					return builder.start();
				} catch (IOException e) {
					waitBarrier.countDown(); //to prevent blocking
					return null;
				}
			}).filter(Objects::nonNull).toList();

			fileManagerChecks.forEach(process -> process.onExit().thenRun(waitBarrier::countDown));
			if (waitBarrier.await(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS)) {
				var zeroExitfileManagerChecks = fileManagerChecks.stream().filter(p -> p.exitValue() == 0).toList();
				if (existsDbusSend.exitValue() == 0 && zeroExitfileManagerChecks.size() != 0) {
					return parseOutputsForActualDBusObject(zeroExitfileManagerChecks);
				}
			}
		} catch (IOException | InterruptedException e) {
			//NO-OP
		}
		return false;
	}

	/**
	 * Parses process stdout to see if dbus-send answer is just an empty node.
	 * <p>
	 * Some dbus-send implementations return on calling methods on not-existing objects an empty node
	 * <pre>
	 *    &lt;node&gt;
	 *    &lt;/node&gt;
	 * </pre>
	 * instead of throwing an error.
	 * <p>
	 * Regarding parsing, see the dbus spec <a href="https://dbus.freedesktop.org/doc/dbus-specification.html#introspection-format">on the introsepction format</a>.
	 *
	 * @param dbusChecks List of dbus-send processes with zero-exitcode
	 * @return if one dbus-send output contains actual content
	 * @throws IOException if the Inputer reader on the process output cannot be created
	 */
	private boolean parseOutputsForActualDBusObject(List<Process> dbusChecks) throws IOException {
		for (var check : dbusChecks) {
			try (var reader = check.inputReader(StandardCharsets.UTF_8)) {
				boolean passedInitialNode = false;
				var line = reader.readLine();
				while (line != null) {
					if (passedInitialNode) {
						return !line.equals("</node>");
					}
					passedInitialNode = line.startsWith("<node"); //root node might also contain name, hence no closing bracket
					line = reader.readLine();
				}
			}
		}
		return false;
	}

	private static ProcessBuilder createDbusObjectCheck(String dbusObjectPath) {
		return new ProcessBuilder().command(
				"dbus-send",
				"--session",
				"--print-reply",
				"--reply-timeout=" + TIMEOUT_THRESHOLD,
				"--dest=org.freedesktop.FileManager1",
				"--type=method_call",
				dbusObjectPath,
				"org.freedesktop.DBus.Introspectable.Introspect"
		);
	}
}
