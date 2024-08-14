package org.cryptomator.linux.autostart;

import org.cryptomator.integrations.autostart.AutoStartProvider;
import org.cryptomator.integrations.autostart.ToggleAutoStartFailedException;
import org.cryptomator.integrations.common.CheckAvailability;
import org.cryptomator.integrations.common.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Enables autostart for Linux desktop environments following the freedesktop standard.
 * <p>
 * This service is based on <a href=https://specifications.freedesktop.org/autostart-spec/autostart-spec-0.5.html>version 0.5 of the freedesktop autostart-spec</a>.
 */
@CheckAvailability
@OperatingSystem(OperatingSystem.Value.LINUX)
public class FreedesktopAutoStartService implements AutoStartProvider {

	private static final Logger LOG = LoggerFactory.getLogger(FreedesktopAutoStartService.class);
	private static final String CMD_PROPERTY = "cryptomator.integrationsLinux.autoStartCmd";
	private static final String AUTOSTART_FILENAME = "Cryptomator.desktop";
	private static final String CONTENT_TEMPLATE = """
			[Desktop Entry]
			Type=Application
			Exec=%s
			Hidden=false
			NoDisplay=false
			X-GNOME-Autostart-enabled=true
			Name=Cryptomator
			Comment=Created with %s
			""";

	private final Path autostartFile;
	private final String content;
	private final boolean hasExecValue;

	public FreedesktopAutoStartService() {
		var xdgConfigDirString = Objects.requireNonNullElse(System.getenv("XDG_CONFIG_HOME"), System.getProperty("user.home") + "/.config");
		this.autostartFile = Path.of(xdgConfigDirString, "autostart", AUTOSTART_FILENAME);

		var execValue = System.getProperty(CMD_PROPERTY);
		if (execValue == null) {
			LOG.debug("JVM property {} not set, using command path", CMD_PROPERTY);
			execValue = ProcessHandle.current().info().command().orElse("");
		}
		this.hasExecValue = !execValue.isBlank();
		this.content = CONTENT_TEMPLATE.formatted(execValue, this.getClass().getName());
	}

	@Override
	public synchronized void enable() throws ToggleAutoStartFailedException {
		try {
			Files.writeString(autostartFile, content, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			throw new ToggleAutoStartFailedException("Failed to activate Cryptomator autostart for GNOME desktop environment.", e);
		}
	}

	@Override
	public synchronized void disable() throws ToggleAutoStartFailedException {
		try {
			Files.deleteIfExists(autostartFile);
		} catch (IOException e) {
			throw new ToggleAutoStartFailedException("Failed to deactivate Cryptomator autostart for GNOME desktop environment.", e);
		}
	}

	@Override
	public synchronized boolean isEnabled() {
		return Files.exists(autostartFile);
	}

	@CheckAvailability
	public boolean isSupported() {
		//TODO: might need to research which Desktop Environments support this
		return hasExecValue && Files.exists(autostartFile.getParent());
	}

}
