package org.cryptomator.linux.quickaccess;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DolphinPlacesTest {

	@Test
	@DisplayName("Class can be loaded and object instantiated")
	public void testInit() {
		Assertions.assertDoesNotThrow(DolphinPlaces::new);
	}
}
