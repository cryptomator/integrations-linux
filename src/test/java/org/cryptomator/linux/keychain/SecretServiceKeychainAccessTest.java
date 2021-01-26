package org.cryptomator.linux.keychain;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for GNOME keyring access via DBUS.
 */
@EnabledOnOs(OS.LINUX)
public class SecretServiceKeychainAccessTest {

	private static boolean isInstalled;

	@BeforeAll
	public static void checkSystemAndSetup() throws IOException {
		ProcessBuilder dbusSend = new ProcessBuilder("dbus-send","--print-reply","--dest=org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus.ListNames");
		ProcessBuilder grep = new ProcessBuilder("grep", "org.gnome.keyring");
		try {
			Process end = ProcessBuilder.startPipeline(List.of(dbusSend, grep)).get(1);
			if (end.waitFor(1000, TimeUnit.MILLISECONDS)) {
				isInstalled = end.exitValue() == 0;
			} else {
				isInstalled = false;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}


	@Test
	public void testIsSupported(){
		SecretServiceKeychainAccess secretService = new SecretServiceKeychainAccess();
		Assertions.assertEquals(isInstalled, secretService.isSupported());
	}


}
