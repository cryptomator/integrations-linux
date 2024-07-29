package org.cryptomator.linux.quickaccess;

import org.cryptomator.integrations.quickaccess.QuickAccessServiceException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

public class DolphinPlacesIT {

	@Test
	@DisplayName("Adds for 20s an entry to the Dolphin sidebar")
	@Disabled
	public void testSidebarIntegration(@TempDir Path tmpdir) throws QuickAccessServiceException, InterruptedException {
		var entry = new DolphinPlaces().add(tmpdir, "integrations-linux");
		Thread.sleep(Duration.ofSeconds(20));
		entry.remove();
	}
}
