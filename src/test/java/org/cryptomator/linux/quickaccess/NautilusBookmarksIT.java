package org.cryptomator.linux.quickaccess;

import org.cryptomator.integrations.common.IntegrationsLoader;
import org.cryptomator.integrations.quickaccess.QuickAccessService;
import org.cryptomator.integrations.quickaccess.QuickAccessServiceException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class NautilusBookmarksIT {

	@Test
	@DisplayName("If nautilus is installed, isSupported returns true and service is contained in the service provider stream")
	@Disabled
	public void testSupport() {
		Assertions.assertTrue(NautilusBookmarks.isSupported());

		var optionalService = IntegrationsLoader.loadAll(QuickAccessService.class).filter(s -> s.getClass().getName().equals("org.cryptomator.linux.quickaccess.NautilusBookmarks")).findAny();
		Assertions.assertTrue(optionalService.isPresent());
	}

	@Test
	@DisplayName("Adds for 20s an entry to the Nautilus sidebar")
	@Disabled
	public void testSidebarIntegrationEasy(@TempDir Path tmpdir) throws QuickAccessServiceException, InterruptedException, IOException {
		var target = tmpdir.resolve("foobar");
		testSidebarIntegration(target, "foobar");
	}

	@Test
	@DisplayName("Adds for 20s an entry to the Nautilus sidebar. The target dir contains a space.")
	@Disabled
	public void testSidebarIntegrationSpace(@TempDir Path tmpdir) throws QuickAccessServiceException, InterruptedException, IOException {
		var target = tmpdir.resolve("foo bar");
		testSidebarIntegration(target, "foobar");
	}

	@Test
	@DisplayName("Adds for 20s an entry to the Nautilus sidebar. The target dir contains non ascii.")
	@Disabled
	public void testSidebarIntegrationNonASCII(@TempDir Path tmpdir) throws QuickAccessServiceException, InterruptedException, IOException {
		var target = tmpdir.resolve("f한obÄr");
		testSidebarIntegration(target, "foobar");
	}

	@Test
	@DisplayName("Adds for 20s an entry to the Nautilus sidebar. The target dir contains non ascii.")
	@Disabled
	public void testSidebarIntegrationName(@TempDir Path tmpdir) throws QuickAccessServiceException, InterruptedException, IOException {
		var target = tmpdir.resolve("foobar");
		testSidebarIntegration(target, "f한o bÄr");
	}

	private void testSidebarIntegration(Path target, String name) throws IOException, InterruptedException, QuickAccessServiceException {
		Files.createDirectory(target);
		var entry = new NautilusBookmarks().add(target, name);
		Thread.sleep(Duration.ofSeconds(20));
		entry.remove();
	}

}
