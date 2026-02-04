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
 * Unit tests for Secret Service access via Dbus.
 */
@EnabledIfEnvironmentVariable(named = "DBUS_SESSION_BUS_ADDRESS", matches = ".*")
public class SecretServiceKeychainAccessTest {

	private static boolean isInstalled;

	@BeforeAll
	public static void checkSystemAndSetup() throws IOException {
		ProcessBuilder dbusSend = new ProcessBuilder("dbus-send", "--print-reply", "--dest=org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus.ListNames");
		ProcessBuilder grep = new ProcessBuilder("grep", "-q", "org.freedesktop.secrets");
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
		var service = new SecretServiceKeychainAccess();
		Assertions.assertEquals(isInstalled, service.isSupported());
	}

	@Nested
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@EnabledIf("serviceAvailableAndUnlocked")
	class FunctionalTests {

		static final String KEY_ID = "cryptomator-test-" + UUID.randomUUID();
		final static SecretServiceKeychainAccess KEYRING = new SecretServiceKeychainAccess();

		@Test
		@Order(1)
		public void testStore() throws KeychainAccessException {
			KEYRING.isSupported(); // ensure encrypted session
			KEYRING.storePassphrase(KEY_ID, "cryptomator-test", "p0ssw0rd");
		}

		@Test
		@Order(2)
		public void testLoad() throws KeychainAccessException {
			var passphrase = KEYRING.loadPassphrase(KEY_ID);
			Assertions.assertNotNull(passphrase);
			Assertions.assertEquals("p0ssw0rd", String.copyValueOf(passphrase));
		}

		@Test
		@Order(3)
		public void testDelete() throws KeychainAccessException {
			KEYRING.deletePassphrase(KEY_ID);
		}

		@Test
		@Order(4)
		public void testLoadNotExisting() throws KeychainAccessException {
			var result = KEYRING.loadPassphrase(KEY_ID);
			Assertions.assertNull(result);
		}

		public static boolean serviceAvailableAndUnlocked() {
			var service = new SecretServiceKeychainAccess();
			return service.isSupported() && !service.isLocked();
		}
	}

}
