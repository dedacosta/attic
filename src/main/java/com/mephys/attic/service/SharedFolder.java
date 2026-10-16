package com.mephys.attic.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A directory whose files and subdirectories are put there by hand and shown in the Folder tab as
 * they are on disk: it is read anew on each request, so added, renamed and removed files show up
 * without a restart. Defaults to a {@code folder} directory next to the database file.
 * <p>
 * Hidden entries (starting with a dot) and symbolic links are left out, and no path may lead
 * outside the directory.
 */
@Component
public class SharedFolder {

	private final Path directory;

	public SharedFolder(@Value("${attic.database.file}") Path databaseFile, @Value("${attic.folder.dir:}") String directory) {
		this.directory = directory.isBlank() ? databaseFile.toAbsolutePath().resolveSibling("folder")
				: Path.of(directory).toAbsolutePath().normalize();
	}

	public record Entry(String name, String path, boolean folder, long size, Instant modified, List<Entry> children) {
	}

	/** The whole tree, folders first, each level sorted by name; the directory is created when missing */
	public List<Entry> list() {
		try {
			Files.createDirectories(directory);
			return children(directory);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to list " + directory, ex);
		}
	}

	/** A file of the folder, given by its path relative to it with "/" between the parts */
	public Optional<Path> file(String relativePath) {
		if (relativePath.isBlank() || relativePath.indexOf('\0') >= 0 || relativePath.contains("\\")) {
			return Optional.empty();
		}
		Path file = directory.resolve(relativePath).normalize();
		if (!file.startsWith(directory) || file.equals(directory)) {
			return Optional.empty();
		}
		for (Path part = file; !part.equals(directory); part = part.getParent()) {
			if (hidden(part) || Files.isSymbolicLink(part)) {
				return Optional.empty();
			}
		}
		return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? Optional.of(file) : Optional.empty();
	}

	private List<Entry> children(Path folder) throws IOException {
		List<Entry> entries = new ArrayList<>();
		try (Stream<Path> paths = Files.list(folder)) {
			for (Path path : paths.toList()) {
				if (hidden(path)) {
					continue;
				}
				BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
						LinkOption.NOFOLLOW_LINKS);
				String relative = directory.relativize(path).toString().replace('\\', '/');
				Instant modified = attributes.lastModifiedTime().toInstant();
				if (attributes.isDirectory()) {
					entries.add(new Entry(name(path), relative, true, 0, modified, children(path)));
				}
				else if (attributes.isRegularFile()) {
					entries.add(new Entry(name(path), relative, false, attributes.size(), modified, List.of()));
				}
			}
		}
		entries.sort(Comparator.comparing(Entry::folder)
			.reversed()
			.thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
		return entries;
	}

	private static String name(Path path) {
		return path.getFileName().toString();
	}

	private static boolean hidden(Path path) {
		return name(path).startsWith(".");
	}

}
