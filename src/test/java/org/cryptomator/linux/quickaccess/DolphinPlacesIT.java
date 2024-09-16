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

public class DolphinPlacesIT {

	@Test
	@DisplayName("If dolphin is installed, isSupported returns true and service is contained in the service provider stream")
	@Disabled
	public void testSupport() {
		Assertions.assertTrue(DolphinPlaces.isSupported());

		var optionalService = IntegrationsLoader.loadAll(QuickAccessService.class).filter(s -> s.getClass().getName().equals("org.cryptomator.linux.quickaccess.DolphinPlaces")).findAny();
		Assertions.assertTrue(optionalService.isPresent());
	}

	@Test
	@DisplayName("Adds for 20s an entry to the Dolphin sidebar")
	@Disabled
	public void testSidebarIntegration(@TempDir Path tmpdir) throws QuickAccessServiceException, InterruptedException {
		var entry = new DolphinPlaces().add(tmpdir, "integrations-linux");
		Thread.sleep(Duration.ofSeconds(20));
		entry.remove();
	}
}
