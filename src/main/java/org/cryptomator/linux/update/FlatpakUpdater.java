package org.cryptomator.linux.update;

import org.cryptomator.integrations.common.CheckAvailability;
import org.cryptomator.integrations.common.DisplayName;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.update.Progress;
import org.cryptomator.integrations.update.ProgressListener;
import org.cryptomator.integrations.update.UpdateFailedException;
import org.cryptomator.integrations.update.UpdateService;
import org.freedesktop.dbus.FileDescriptor;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.purejava.portal.Flatpak;
import org.purejava.portal.FlatpakSpawnFlag;
import org.purejava.portal.UpdatePortal;
import org.purejava.portal.Util;
import org.purejava.portal.rest.UpdateCheckerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Priority(1000)
@CheckAvailability
@DisplayName("Update via Flatpak update")
@OperatingSystem(OperatingSystem.Value.LINUX)
public class FlatpakUpdater implements UpdateService, AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(FlatpakUpdater.class);
	private static final String APP_NAME = "org.cryptomator.Cryptomator";

	private final List<ProgressListener> progressListeners = new CopyOnWriteArrayList<>();

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
	public UpdateCheckerTask getLatestReleaseChecker() {
		portal.setUpdateCheckerTaskFor(APP_NAME);
		return portal.getUpdateCheckerTaskFor(APP_NAME);
	}

	@Override
	public void triggerUpdate() throws UpdateFailedException {
		var monitor = getUpdateMonitor();
		portal.updateApp("x11:0", monitor, UpdatePortal.OPTIONS_DUMMY);
	}

	@Override
	public long spawnApp() {
		var cwdPath = Util.stringToByteList(System.getProperty("user.dir"));
		List<List<Byte>> argv = List.of(
				Util.stringToByteList(APP_NAME));
		Map<UInt32, FileDescriptor> fds = Collections.emptyMap();
		Map<String, String> envs = Map.of();
		UInt32 flags = new UInt32(FlatpakSpawnFlag.LATEST_VERSION.getValue());
		Map<String, Variant<?>> options = UpdatePortal.OPTIONS_DUMMY;

		return spawnApp(cwdPath, argv, fds, envs, flags, options).longValue();
	}

	@Override
	public boolean doesRequireElevatedPermissions() {
		return false;
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
					portal.getDBusConnection().addSigHandler(Flatpak.UpdateMonitor.Progress.class, signal -> {
						notifyOnUpdateProceeds(signal);
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

	@Override
	public void addProgressListener(ProgressListener listener) {
		progressListeners.add(listener);
	}

	@Override
	public void removeProgressListener(ProgressListener listener) {
		progressListeners.remove(listener);
	}

	private void notifyOnUpdateProceeds(Flatpak.UpdateMonitor.Progress signal) {
		long status = ((UInt32) signal.info.get("status").getValue()).longValue();
		long progress = 0;
		Variant<?> progressVariant = signal.info.get("progress");
		if (null != progressVariant) {
			progress = ((UInt32) progressVariant.getValue()).longValue();
		}
		Progress p = new Progress(status, progress);
		for (ProgressListener listener : progressListeners) {
			listener.onProgress(p);
		}
	}

	private UInt32 spawnApp(List<Byte> cwdPath, List<List<Byte>> argv, Map<UInt32, FileDescriptor> fds, Map<String, String> envs, UInt32 flags, Map<String, Variant<?>> options) {
		return portal.Spawn(cwdPath, argv, fds, envs, flags, options);
	}
}
