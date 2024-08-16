package org.cryptomator.linux.util;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class SupportUtil {

	public static boolean commandExists(String commandName) {
		var shell = Objects.requireNonNullElse(System.getenv("SHELL"),"sh");
		try {
			var cmdExistsProcess = new ProcessBuilder().command(shell, "-c", "command -v " + commandName).start();
			if (cmdExistsProcess.waitFor(5000, TimeUnit.MILLISECONDS)) {
				return cmdExistsProcess.exitValue() == 0;
			}
		} catch (IOException | InterruptedException e) {
			//NO-OP
		}
		return false;
	}
}
