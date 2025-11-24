package org.cryptomator.linux.update;

import org.cryptomator.integrations.update.UpdateInfo;
import org.cryptomator.integrations.update.UpdateMechanism;

public record FlatpakUpdateInfo(String version, UpdateMechanism<FlatpakUpdateInfo> updateMechanism) implements UpdateInfo<FlatpakUpdateInfo> {
}
