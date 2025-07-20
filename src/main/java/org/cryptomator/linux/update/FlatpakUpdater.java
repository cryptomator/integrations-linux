package org.cryptomator.linux.update;

import org.cryptomator.integrations.common.CheckAvailability;
import org.cryptomator.integrations.common.DistributionChannel;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.update.UpdateFailedException;
import org.cryptomator.integrations.update.UpdateService;
import org.freedesktop.dbus.FileDescriptor;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.purejava.portal.Flatpak;
import org.purejava.portal.UpdatePortal;
import org.purejava.portal.Util;
import org.purejava.portal.rest.UpdateCheckerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Priority(1000)
@CheckAvailability
@DistributionChannel(DistributionChannel.Value.LINUX_FLATPAK)
@OperatingSystem(OperatingSystem.Value.LINUX)
public class FlatpakUpdater implements UpdateService, AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(FlatpakUpdater.class);
	private static final String APP_NAME = "org.cryptomator.Cryptomator";

	private final UpdatePortal portal;
	private Flatpak.UpdateMonitor updateMonitor;

	public FlatpakUpdater() {
		this.portal = new UpdatePortal();
		portal.CreateUpdateMonitor(UpdatePortal.OPTIONS_DUMMY);
	}

	@Override
	public boolean isSupported() {
		return portal.isAvailable();
	}

	@Override
	public UpdateCheckerTask getLatestReleaseChecker(DistributionChannel.Value channel) {
		if (channel != DistributionChannel.Value.LINUX_FLATPAK) {
			LOG.error("Wrong channel provided: {}", channel);
			return null;
		}
		portal.setUpdateCheckerTaskFor(APP_NAME);
		return portal.getUpdateCheckerTaskFor(APP_NAME);
	}

	@Override
	public void triggerUpdate() throws UpdateFailedException {
		var cwdPath = Util.stringToByteList(System.getProperty("user.dir"));
		List<List<Byte>> argv = List.of(
				Util.stringToByteList("org.cryptomator.Cryptomator"));
		Map<UInt32, FileDescriptor> fds = Collections.emptyMap();
		Map<String, String> envs = Map.of();
		var flags = new UInt32(0);
		Map<String, Variant<?>> options = UpdatePortal.OPTIONS_DUMMY;

		spawnApp(cwdPath, argv, fds, envs, flags, options);

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
				portal.cancelUpdateMonitor(updateMonitor);
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

	private UInt32 spawnApp(List<Byte> cwdPath, List<List<Byte>> argv, Map<UInt32, FileDescriptor> fds, Map<String, String> envs, UInt32 flags, Map<String, Variant<?>> options) {
		return portal.Spawn(cwdPath, argv, fds, envs, flags, options);
	}
}
