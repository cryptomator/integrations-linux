package org.cryptomator.linux.tray;

import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.tray.TrayIntegrationProvider;

@Priority(1000)
@OperatingSystem(OperatingSystem.Value.LINUX)
public class LinuxTrayIntegrationsProvider implements TrayIntegrationProvider {
	@Override
	public void minimizedToTray() {

	}

	@Override
	public void restoredFromTray() {

	}
}
