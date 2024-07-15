package org.cryptomator.linux.quickaccess;

import org.cryptomator.integrations.quickaccess.QuickAccessServiceException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

public class NautilusBookmarksIT {

	@Test
	@DisplayName("Adds for 20s an entryto the Nautilus sidebar")
	@Disabled
	public void testSidebarIntegration(@TempDir Path tmpdir) throws QuickAccessServiceException, InterruptedException {
		var entry = new NautilusBookmarks().add(tmpdir, "integrations-linux");
		Thread.sleep(Duration.ofSeconds(20));
		entry.remove();
	}
}
