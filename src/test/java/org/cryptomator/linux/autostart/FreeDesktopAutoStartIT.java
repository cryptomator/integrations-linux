package org.cryptomator.linux.autostart;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FreeDesktopAutoStartIT {

	FreedesktopAutoStartService inTest = new FreedesktopAutoStartService();

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
