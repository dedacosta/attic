package com.mephys.attic.picture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stores picture files in a directory, named by picture id. Defaults to a
 * {@code pictures} directory next to the database file.
 */
@Component
public class PictureStorage {

	private final Path directory;

	PictureStorage(@Value("${attic.database.file}") Path databaseFile, @Value("${attic.picture.dir:}") String directory) {
		this.directory = directory.isBlank() ? databaseFile.toAbsolutePath().resolveSibling("pictures")
				: Path.of(directory).toAbsolutePath();
	}

	/**
	 * Write the picture to a new file.
	 * @return the file name, relative to the picture directory
	 */
	public String store(UUID pictureId, Picture picture) {
		String fileName = pictureId + "." + picture.fileExtension();
		try {
			Files.createDirectories(directory);
			Path temp = Files.createTempFile(directory, ".upload-", ".tmp");
			try {
				Files.write(temp, picture.data());
				Files.move(temp, directory.resolve(fileName), StandardCopyOption.ATOMIC_MOVE);
			}
			finally {
				Files.deleteIfExists(temp);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to store picture " + fileName, ex);
		}
		return fileName;
	}

	public Optional<byte[]> read(String fileName) {
		try {
			return Optional.of(Files.readAllBytes(directory.resolve(fileName)));
		}
		catch (NoSuchFileException ex) {
			return Optional.empty();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to read picture " + fileName, ex);
		}
	}

	public void delete(String fileName) {
		try {
			Files.deleteIfExists(directory.resolve(fileName));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to delete picture " + fileName, ex);
		}
	}

}
