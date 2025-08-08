package org.cryptomator.linux.update;

import org.cryptomator.integrations.common.CheckAvailability;
import org.cryptomator.integrations.common.DisplayName;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.update.UpdateFailedException;
import org.cryptomator.integrations.update.UpdateMechanism;
import org.cryptomator.integrations.update.UpdateProcess;
import org.freedesktop.dbus.FileDescriptor;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.purejava.portal.Flatpak;
import org.purejava.portal.FlatpakSpawnFlag;
import org.purejava.portal.UpdatePortal;
import org.purejava.portal.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Priority(1000)
@CheckAvailability
@DisplayName("Update via Flatpak update")
@OperatingSystem(OperatingSystem.Value.LINUX)
public class FlatpakUpdater implements UpdateMechanism {

	private static final Logger LOG = LoggerFactory.getLogger(FlatpakUpdater.class);
	private static final String APP_NAME = "org.cryptomator.Cryptomator";

	private final UpdatePortal portal;

	public FlatpakUpdater() {
		this.portal = new UpdatePortal();
		portal.CreateUpdateMonitor(UpdatePortal.OPTIONS_DUMMY);
	}

	@CheckAvailability
	public boolean isSupported() {
		return portal.isAvailable();
	}

	@Override
	public boolean isUpdateAvailable() {
		var cdl = new CountDownLatch(1);
		portal.setUpdateCheckerTaskFor(APP_NAME);
		var checkTask = portal.getUpdateCheckerTaskFor(APP_NAME);
		var updateAvailable = new AtomicBoolean(false);
		checkTask.setOnSucceeded(latestVersion -> {
			updateAvailable.set(true); // TODO: compare version strings before setting this to true
			cdl.countDown();
		});
		checkTask.setOnFailed(error -> {
			LOG.warn("Error while checking for updates.", error);
			cdl.countDown();
		});
		try {
			cdl.await();
			return updateAvailable.get();
		} catch (InterruptedException e) {
			checkTask.cancel();
			Thread.currentThread().interrupt();
			return false;
		}
	}

	@Override
	public UpdateProcess prepareUpdate() throws UpdateFailedException {
		var monitorPath = portal.CreateUpdateMonitor(UpdatePortal.OPTIONS_DUMMY);
		if (monitorPath == null) {
			throw new UpdateFailedException("Failed to create UpdateMonitor on DBus");
		}

		return new FlatpakUpdateProcess(portal.getUpdateMonitor(monitorPath.toString()));
	}

	private class FlatpakUpdateProcess implements UpdateProcess {

		private final CountDownLatch latch = new CountDownLatch(1);
		private final Flatpak.UpdateMonitor monitor;
		private volatile double progress = 0.0;
		private volatile UpdateFailedException error;
		private AutoCloseable signalHandler;

		private FlatpakUpdateProcess(Flatpak.UpdateMonitor monitor) {
			this.monitor = monitor;
			startUpdate();
		}

		private void startUpdate() {
			try {
				this.signalHandler = portal.getDBusConnection().addSigHandler(Flatpak.UpdateMonitor.Progress.class, this::handleProgressSignal);
			} catch (DBusException e) {
				LOG.error("DBus error", e);
				latch.countDown();
			}
			portal.updateApp("x11:0", monitor, UpdatePortal.OPTIONS_DUMMY);
		}

		private void handleProgressSignal(Flatpak.UpdateMonitor.Progress signal) {
			int status = ((UInt32) signal.info.get("status").getValue()).intValue();
			switch (status) {
				case 0 -> { // In progress
					Variant<?> progressVariant = signal.info.get("progress");
					if (progressVariant != null) {
						progress = ((UInt32) progressVariant.getValue()).doubleValue() / 100.0; // progress reported as int in range [0, 100]
					}
				}
				case 1 -> { // No update available
					error = new UpdateFailedException("No update available");
					latch.countDown();
				}
				case 2 -> { // Update complete
					progress = 1.0;
					latch.countDown();
				}
				case 3 -> { // Update failed
					error = new UpdateFailedException("Update preparation failed");
					latch.countDown();
				}
				default -> {
					error = new UpdateFailedException("Unknown update status " + status);
					latch.countDown();
				}
			}
		}

		private void stopReceivingSignals() {
			if (signalHandler != null) {
				try {
					signalHandler.close();
				} catch (Exception e) {
					LOG.error("Failed to close signal handler", e);
				}
				signalHandler = null;
			}
		}

		@Override
		public double preparationProgress() {
			return progress;
		}

		@Override
		public void cancel() {
			portal.cancelUpdateMonitor(monitor);
			stopReceivingSignals();
			portal.close(); // TODO: is this right? belongs to parent class. update can not be retried afterwards. or should each process have its own portal instance?
			error = new UpdateFailedException("Update cancelled by user");
		}

		@Override
		public void await() throws InterruptedException {
			latch.await();
		}

		@Override
		public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
			return latch.await(timeout, unit);
		}

		private boolean isDone() {
			try {
				return latch.await(0, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}

		@Override
		public ProcessHandle applyUpdate() throws IllegalStateException, IOException {
			if (!isDone()) {
				throw new IllegalStateException("Update preparation is not complete");
			}
			stopReceivingSignals();
			if (error != null) {
				throw error;
			}

			// spawn new Cryptomator process:
			var cwdPath = Util.stringToByteList(System.getProperty("user.dir"));
			List<List<Byte>> argv = List.of(
					Util.stringToByteList(APP_NAME));
			Map<UInt32, FileDescriptor> fds = Collections.emptyMap();
			Map<String, String> envs = Map.of();
			UInt32 flags = new UInt32(FlatpakSpawnFlag.LATEST_VERSION.getValue());
			Map<String, Variant<?>> options = UpdatePortal.OPTIONS_DUMMY;
			var pid = portal.Spawn(cwdPath, argv, fds, envs, flags, options).longValue();
			return ProcessHandle.of(pid).orElseThrow();
		}
	}

}
