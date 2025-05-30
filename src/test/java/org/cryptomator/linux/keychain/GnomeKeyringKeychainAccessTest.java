package org.cryptomator.linux.keychain;

import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for GNOME keyring access via DBUS.
 */
@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".*")
public class GnomeKeyringKeychainAccessTest {

	private static boolean isInstalled;

	@BeforeAll
	public static void checkSystemAndSetup() throws IOException {
		ProcessBuilder dbusSend = new ProcessBuilder("dbus-send", "--print-reply", "--dest=org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus.ListNames");
		ProcessBuilder grep = new ProcessBuilder("grep", "-q", "org.gnome.keyring");
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
		var gnomeKeyring = new GnomeKeyringKeychainAccess();
		Assertions.assertEquals(isInstalled, gnomeKeyring.isSupported());
	}

	@Nested
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@EnabledIf("gnomeKeyringAvailableAndUnlocked")
	class FunctionalTests {

		static final String KEY_ID = "cryptomator-test-" + UUID.randomUUID();
		final GnomeKeyringKeychainAccess gnomeKeyring = new GnomeKeyringKeychainAccess();

		@Test
		@Order(1)
		public void testStore() throws KeychainAccessException {
			gnomeKeyring.storePassphrase(KEY_ID, "cryptomator-test", "p0ssw0rd");
		}

		@Test
		@Order(2)
		public void testLoad() throws KeychainAccessException {
			var passphrase = gnomeKeyring.loadPassphrase(KEY_ID);
			Assertions.assertNotNull(passphrase);
			Assertions.assertEquals("p0ssw0rd", String.copyValueOf(passphrase));
		}

		@Test
		@Order(3)
		public void testDelete() throws KeychainAccessException {
			gnomeKeyring.deletePassphrase(KEY_ID);
		}

		@Test
		@Order(4)
		public void testLoadNotExisting() throws KeychainAccessException {
			var result = gnomeKeyring.loadPassphrase(KEY_ID);
			Assertions.assertNull(result);
		}

		public static boolean gnomeKeyringAvailableAndUnlocked() {
			var secretServiceKeychain = new GnomeKeyringKeychainAccess();
			return secretServiceKeychain.isSupported() && !secretServiceKeychain.isLocked();
		}
	}

}