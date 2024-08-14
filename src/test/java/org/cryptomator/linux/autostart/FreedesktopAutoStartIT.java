package org.cryptomator.linux.autostart;

import org.cryptomator.integrations.autostart.AutoStartProvider;
import org.cryptomator.integrations.common.IntegrationsLoader;
import org.cryptomator.integrations.quickaccess.QuickAccessService;
import org.cryptomator.linux.quickaccess.DolphinPlaces;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FreedesktopAutoStartIT {

	FreedesktopAutoStartService inTest = new FreedesktopAutoStartService();

	@Test
	@DisplayName("If freedesktop dirs are present, isSupported returns true and service is contained in the service provider stream")
	@Disabled
	public void testSupport() {
		Assertions.assertTrue(inTest.isSupported());

		var optionalService = IntegrationsLoader.loadAll(AutoStartProvider.class).filter(s -> s.getClass().getName().equals("org.cryptomator.linux.autostart.FreedesktopAutoStartService")).findAny();
		Assertions.assertTrue(optionalService.isPresent());
	}

	@Test
	@Order(1)
	public void testAutostartEnable() {
		Assertions.assertDoesNotThrow(() -> inTest.enable());
		Assertions.assertTrue(inTest.isEnabled());
	}


	@Test
	@Order(2)
	public void testAutostartDisable() {
		Assertions.assertDoesNotThrow(() -> inTest.disable());
		Assertions.assertFalse(inTest.isEnabled());
	}
}
