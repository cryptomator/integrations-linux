package org.cryptomator.linux.filemanagersidebar;

import org.cryptomator.integrations.sidebar.SidebarServiceException;
import org.cryptomator.linux.sidebar.NautilusSidebarService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

public class NautilusSidebarServiceIT {

	@Test
	@DisplayName("Adds for 20s an entryto the Nautilus sidebar")
	@Disabled
	public void testSidebarIntegration(@TempDir Path tmpdir) throws SidebarServiceException, InterruptedException {
		var entry = new NautilusSidebarService().add(tmpdir, "integrations-linux");
		Thread.sleep(Duration.ofSeconds(20));
		entry.remove();
	}
}
