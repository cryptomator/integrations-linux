package org.cryptomator.linux.quickaccess;

import org.cryptomator.integrations.common.IntegrationsLoader;
import org.cryptomator.integrations.quickaccess.QuickAccessService;
import org.cryptomator.integrations.quickaccess.QuickAccessServiceException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
	@DisplayName("Adds for 20s an entryto the Nautilus sidebar")
	@Disabled
	public void testSidebarIntegration(@TempDir Path tmpdir) throws QuickAccessServiceException, InterruptedException {
		var entry = new NautilusBookmarks().add(tmpdir, "integrations-linux");
		Thread.sleep(Duration.ofSeconds(20));
		entry.remove();
	}
}
