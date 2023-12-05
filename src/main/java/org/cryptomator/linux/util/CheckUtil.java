package org.cryptomator.linux.util;

public class CheckUtil {


	/**
	 * Ensures the truth of an expression involving the state of the calling instance, but not
	 * involving any parameters to the calling method.
	 *
	 * @param expression a boolean expression
	 * @param errorMessage the exception message to use if the check fails; will be converted to a
	 *     string using {@link String#valueOf(Object)}
	 * @throws IllegalStateException if {@code expression} is false
	 */
	public static void checkState(boolean expression, String errorMessage) {
		if (!expression) {
			throw new IllegalStateException(errorMessage);
		}
	}

	public static void checkState(boolean expression) {
		if (!expression) {
			throw new IllegalStateException();
		}
	}
}
