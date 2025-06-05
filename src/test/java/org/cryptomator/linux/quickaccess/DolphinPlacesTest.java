package org.cryptomator.linux.quickaccess;

import org.cryptomator.integrations.quickaccess.QuickAccessServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

public class DolphinPlacesTest {

	private static final String UUID_FOLDER_1 = "c4b72799-ca67-4c2e-b727-99ca67dc2e5d";
	private static final String UUID_FOLDER_1_IDENTICAL = "43c6fdb9-626d-468e-86fd-b9626dc68e04d";
	private static final String CAPTION_FOLDER_1 = "folder 1";
	private static final String PATH_FOLDER_1 = "/home/someuser/folder1";

	private static final String RESOURCE_USER_PLACES = "quickaccess/dolphin/user-places.xbel";
	private static final String RESOURCE_USER_PLACES_MULTIPLE_IDENTICAL = "quickaccess/dolphin/user-places-multiple-identical.xbel";
	private static final String RESOURCE_USER_PLACES_NOT_WELL_FORMED = "quickaccess/dolphin/user-places-not-well-formed.xbel";
	private static final String RESOURCE_USER_PLACES_NOT_VALID = "quickaccess/dolphin/user-places-not-valid.xbel";

	@Test
	@DisplayName("Class can be loaded and object instantiated")
	public void testInit() {
		assertDoesNotThrow(() -> { new DolphinPlaces(); });
	}

	@Test
	@DisplayName("Adding an identical entry should lead to a replacement of the existing entry")
	public void addingAnIdenticalEntryShouldLeadToReplacementOfExistingEntry(@TempDir Path tmpdir)  {

		var pathToDoc = loadResourceToDir(RESOURCE_USER_PLACES, tmpdir);

		assertTrue(loadFile(pathToDoc).contains(UUID_FOLDER_1));
		assertTrue(loadFile(pathToDoc).contains(CAPTION_FOLDER_1));

		assertDoesNotThrow(() -> {

			var entry = new DolphinPlaces(tmpdir).add(Path.of(PATH_FOLDER_1), CAPTION_FOLDER_1);

			assertFalse(loadFile(pathToDoc).contains(UUID_FOLDER_1));
			assertTrue(loadFile(pathToDoc).contains(CAPTION_FOLDER_1));

			entry.remove();
		});
	}

	@Test
	@DisplayName("Adding an identical entry should lead to a replacement of multiple existing entries")
	public void addingAnIdenticalEntryShouldLeadToReplacementOfMultipleExistingEntry(@TempDir Path tmpdir)  {

		var pathToDoc = loadResourceToDir(RESOURCE_USER_PLACES_MULTIPLE_IDENTICAL, tmpdir);

		assertEquals(1, countOccurrences(loadFile(pathToDoc),UUID_FOLDER_1));
		assertEquals(1, countOccurrences(loadFile(pathToDoc),UUID_FOLDER_1_IDENTICAL));

		assertEquals(2, countOccurrences(loadFile(pathToDoc), CAPTION_FOLDER_1));

		assertDoesNotThrow(() -> {

			var entry = new DolphinPlaces(tmpdir).add(Path.of(PATH_FOLDER_1), CAPTION_FOLDER_1);

			assertEquals(0, countOccurrences(loadFile(pathToDoc),UUID_FOLDER_1));
			assertEquals(0, countOccurrences(loadFile(pathToDoc),UUID_FOLDER_1_IDENTICAL));

			assertEquals(1, countOccurrences(loadFile(pathToDoc), CAPTION_FOLDER_1));

			entry.remove();
		});

		assertEquals(0, countOccurrences(loadFile(pathToDoc), CAPTION_FOLDER_1));
	}

	@Test
	@DisplayName("Adding should not replace if file is not valid")
	public void addingShouldNotReplaceIfFileIsNotValid(@TempDir Path tmpdir) {

		var pathToDoc = loadResourceToDir(RESOURCE_USER_PLACES_NOT_VALID, tmpdir);

		assertEquals(1, countOccurrences(loadFile(pathToDoc), UUID_FOLDER_1));
		assertEquals(1, countOccurrences(loadFile(pathToDoc), CAPTION_FOLDER_1));

		assertThrows(QuickAccessServiceException.class, () -> {

			new DolphinPlaces(tmpdir).add(Path.of(PATH_FOLDER_1), CAPTION_FOLDER_1);

		});

		assertEquals(1, countOccurrences(loadFile(pathToDoc), UUID_FOLDER_1));
		assertEquals(1, countOccurrences(loadFile(pathToDoc), CAPTION_FOLDER_1));
	}

	@Test
	@DisplayName("Adding should not replace if file is not well formed")
	public void addingShouldNotReplaceIfFileIsNotWellFormed(@TempDir Path tmpdir) {

		var pathToDoc = loadResourceToDir(RESOURCE_USER_PLACES_NOT_WELL_FORMED, tmpdir);

		assertEquals(1, countOccurrences(loadFile(pathToDoc), UUID_FOLDER_1));
		assertEquals(1, countOccurrences(loadFile(pathToDoc), CAPTION_FOLDER_1));

		assertThrows(QuickAccessServiceException.class, () -> {

			new DolphinPlaces(tmpdir).add(Path.of(PATH_FOLDER_1), CAPTION_FOLDER_1);

		});

		assertEquals(1, countOccurrences(loadFile(pathToDoc), UUID_FOLDER_1));
		assertEquals(1, countOccurrences(loadFile(pathToDoc), CAPTION_FOLDER_1));
	}

	@Test
	@DisplayName("Invalid characters in caption should be escaped")
	public void invalidCharactersInCaptionShouldBeEscaped(@TempDir Path tmpdir) {

		var pathToDoc = loadResourceToDir(RESOURCE_USER_PLACES, tmpdir);

		assertEquals(0, countOccurrences(loadFile(pathToDoc), "&lt; &amp; &gt;"));

		assertDoesNotThrow(() -> {

			new DolphinPlaces(tmpdir).add(Path.of(PATH_FOLDER_1), "< & >");

		});

		assertEquals(1, countOccurrences(loadFile(pathToDoc), "&lt; &amp; &gt;"));
	}

	private Path loadResourceToDir(String source, Path targetDir)  {

		try (var stream = this.getClass().getClassLoader().getResourceAsStream(source)) {

			if (stream == null) {
				throw new IOException("Resource not found: " + source);
			}

			Files.copy(stream, targetDir.resolve("user-places.xbel"), StandardCopyOption.REPLACE_EXISTING);

			return targetDir.resolve("user-places.xbel");

		} catch (IOException e) {
			throw new RuntimeException("Failed to load resource: " + source, e);
		}
	}

	private String loadFile(Path file) {

		if (!Files.exists(file)) {
			throw new RuntimeException("File does not exist: " + file);
		}

		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private int countOccurrences(String content, String searchString) {
		int count = 0;
		int index = 0;

		while ((index = content.indexOf(searchString, index)) != -1) {
			count++;
			index += searchString.length();
		}

		return count;
	}
}
