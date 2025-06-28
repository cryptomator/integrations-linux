package org.cryptomator.linux.update;

import org.cryptomator.integrations.common.CheckAvailability;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.update.UpdateFailedException;
import org.cryptomator.integrations.update.UpdateService;
import org.freedesktop.dbus.exceptions.DBusException;
import org.purejava.portal.Flatpak;
import org.purejava.portal.UpdatePortal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Priority(1000)
@CheckAvailability
@OperatingSystem(OperatingSystem.Value.LINUX)
public class FlatpakUpdater implements UpdateService, AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(FlatpakUpdater.class);

	private final UpdatePortal portal;
	private Flatpak.UpdateMonitor updateMonitor;

	public FlatpakUpdater() {
		this.portal = new UpdatePortal();
	}

	@Override
	public boolean isSupported() {
		return portal.isAvailable();
	}

	@Override
	public String isUpdateAvailable(DistributionChannel channel) {
		return "";
	}

	@Override
	public void triggerUpdate() throws UpdateFailedException {
		getUpdateMonitor();
		//var monitor = getUpdateMonitor();
		//portal.updateApp("x11:0", monitor, UpdatePortal.OPTIONS_DUMMY);
	}

	@Override
	public boolean doesRequireElevatedPermissions() {
		return false;
	}

	@Override
	public String getDisplayName() {
		return "Update via Flatpak update";
	}

	@Override
	public void close() throws Exception {
		try {
			if (null != updateMonitor) {
				updateMonitor.Close();
			}
			portal.close();
		} catch (Exception e) {
			LOG.error(e.toString(), e.getCause());
		}
	}

	private synchronized Flatpak.UpdateMonitor getUpdateMonitor() {
		if (updateMonitor == null) {
			var updateMonitorPath = portal.CreateUpdateMonitor(UpdatePortal.OPTIONS_DUMMY);
			if (updateMonitorPath != null) {
				LOG.debug("UpdateMonitor successful created at {}", updateMonitorPath);
				updateMonitor = portal.getUpdateMonitor(updateMonitorPath.toString());
				try {
					portal.getDBusConnection().addSigHandler(Flatpak.UpdateMonitor.UpdateAvailable.class, signal -> {
						notifyOnUpdateAvailable(signal);
					});
				} catch (DBusException e) {
					LOG.error(e.toString(), e.getCause());
				}
			} else {
				LOG.error("Failed to create UpdateMonitor on DBus");
			}
		}
		return updateMonitor;
	}

	public void notifyOnUpdateAvailable(Flatpak.UpdateMonitor.UpdateAvailable signal) {
		LOG.info("Update available to remote-commit {}", signal.update_info.get("remote-commit").getValue());
	}
}
