package org.cryptomator.linux.keychain;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for KWallet access via DBUS.
 * 
 * @deprecated Cryptomator has Secret Service as the successor of KDE Wallet and GNOME keyring as a keychain backend since version 1.19.0
 */
@EnabledOnOs(OS.LINUX)
@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".*")
@Deprecated(since = "1.6.2")
public class KDEWalletKeychainAccessTest {

	private static boolean isInstalled;

	@BeforeAll
	public static void checkSystemAndSetup() throws IOException {
		ProcessBuilder dbusSend = new ProcessBuilder("dbus-send", "--print-reply", "--dest=org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus.ListActivatableNames");
		ProcessBuilder grep = new ProcessBuilder("grep", "-q", "org.kde.kwallet");
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
	public void testIsSupported() {
		KDEWalletKeychainAccess keychainAccess = new KDEWalletKeychainAccess();
		Assertions.assertEquals(isInstalled, keychainAccess.isSupported());
	}
}
