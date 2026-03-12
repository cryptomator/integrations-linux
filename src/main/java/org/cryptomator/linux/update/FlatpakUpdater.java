package org.cryptomator.linux.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cryptomator.integrations.common.CheckAvailability;
import org.cryptomator.integrations.common.DisplayName;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.update.UpdateFailedException;
import org.cryptomator.integrations.update.UpdateMechanism;
import org.cryptomator.integrations.update.UpdateStep;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@CheckAvailability
@DisplayName("Update via Flatpak update")
@OperatingSystem(OperatingSystem.Value.LINUX)
public class FlatpakUpdater implements UpdateMechanism<FlatpakUpdateInfo> {

	private static final Logger LOG = LoggerFactory.getLogger(FlatpakUpdater.class);
	private static final String FLATHUB_API_BASE_URL = "https://flathub.org/api/v2/appstream/";
	private static final String APP_NAME = "org.cryptomator.Cryptomator";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
	public FlatpakUpdateInfo checkForUpdate(String currentVersion, HttpClient httpClient) throws UpdateFailedException {
		var uri = URI.create(FLATHUB_API_BASE_URL + APP_NAME);
		var request = HttpRequest.newBuilder(uri).GET().build();
		try {
			var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				LOG.warn("GET {} resulted in status {}", uri, response.statusCode());
				return null;
			} else {
				var appstream = OBJECT_MAPPER.reader().readValue(response.body(), AppstreamResponse.class);
				var updateVersion = appstream.releases().stream()
						.filter(release -> "stable".equalsIgnoreCase(release.type))
						.max(Comparator.comparing(AppstreamReleases::timestamp)) // we're interested in the newest stable release
						.map(AppstreamReleases::version)
						.orElse("0.0.0"); // fallback should always be smaller than current version

				if (UpdateMechanism.isUpdateAvailable(updateVersion, currentVersion)) {
					return new FlatpakUpdateInfo(updateVersion, this);
				} else {
					return null;
				}
			}
		} catch (IOException e) {
			throw new UpdateFailedException("Check for updates failed.", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.warn("Update check interrupted", e);
			return null;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AppstreamResponse(
			@JsonProperty("releases") List<AppstreamReleases> releases
	) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AppstreamReleases(
			@JsonProperty("timestamp") long timestamp,
			@JsonProperty("version") String version,
			@JsonProperty("type") String type
	) {}

	@Override
	public UpdateStep firstStep(FlatpakUpdateInfo updateInfo) throws UpdateFailedException {
		var monitorPath = portal.CreateUpdateMonitor(UpdatePortal.OPTIONS_DUMMY);
		if (monitorPath == null) {
			throw new UpdateFailedException("Failed to create UpdateMonitor on DBus");
		}

		return new FlatpakUpdateStep(portal.getUpdateMonitor(monitorPath.toString()));
	}

	private class FlatpakUpdateStep implements UpdateStep {

		private final CountDownLatch latch = new CountDownLatch(1);
		private final Flatpak.UpdateMonitor monitor;
		private volatile double progress = 0.0;
		private volatile UpdateFailedException error;
		private AutoCloseable signalHandler;

		private FlatpakUpdateStep(Flatpak.UpdateMonitor monitor) {
			this.monitor = monitor;
		}

		@Override
		public String description() {
			return "Updating via Flatpak... %1.0f%%".formatted(preparationProgress() * 100);
		}

		@Override
		public void start() {
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

		@Override
		public boolean isDone() {
			try {
				return latch.await(0, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}

		@Override
		public UpdateStep nextStep() throws IllegalStateException, IOException {
			return UpdateStep.of("Restarting application", this::applyUpdate);
		}

		public UpdateStep applyUpdate() throws IllegalStateException, IOException {
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
			LOG.info("Spawned updated Cryptomator process with PID {}", pid);
			return UpdateStep.EXIT;
		}
	}

}
