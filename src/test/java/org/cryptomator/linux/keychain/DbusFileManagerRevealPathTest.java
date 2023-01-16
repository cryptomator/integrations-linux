package org.cryptomator.linux.keychain;

import org.cryptomator.integrations.revealpath.RevealFailedException;
import org.cryptomator.linux.revealpath.DBusFileMangerRevealPath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

@EnabledOnOs(OS.LINUX)
@Disabled
public class DbusFileManagerRevealPathTest {

	@TempDir Path tmpDir;
	DBusFileMangerRevealPath inTest = new DBusFileMangerRevealPath();

	@Test
	public void testIsSupported() {
		Assertions.assertDoesNotThrow(() -> inTest.isSupported());
	}

	@Test
	public void testRevealSuccess() {
		DBusFileMangerRevealPath revealPathService = new DBusFileMangerRevealPath();
		Assumptions.assumeTrue(revealPathService.isSupported());

		Assertions.assertDoesNotThrow(() -> revealPathService.reveal(tmpDir));
	}

	@Test
	public void testRevealFail() {
		DBusFileMangerRevealPath revealPathService = new DBusFileMangerRevealPath();
		Assumptions.assumeTrue(revealPathService.isSupported());

		Assertions.assertThrows(RevealFailedException.class, () -> revealPathService.reveal(tmpDir.resolve("foobar")));
	}
}
