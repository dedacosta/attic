package com.mephys.attic.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.zip.GZIPOutputStream;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Keeps who got into the app, and who tried, in text files for the super-administrator to
 * read: by default in a {@code logs} directory next to the database file.
 * <p>
 * There is a file per month, {@code sign-ins-2026-10.log}, with a line per event: when, what,
 * the username, the address it came from and the browser, separated by tabs. A month that is
 * over is compressed to {@code sign-ins-2026-09.log.gz}. Passwords are never written.
 */
@Component
public class SignInLog {

	public enum Event {

		/** The first account was set up */
		ACCOUNT_CREATED,
		/** An account was created with an invitation */
		REGISTERED,
		/** Somebody tried to register without a valid invitation */
		REGISTRATION_REFUSED,
		/** Signed in with username and password */
		SIGNED_IN,
		/** Signed in again by the cookie of "stay signed in" */
		SIGNED_IN_AUTOMATICALLY,
		/** An existing username with a wrong password */
		WRONG_PASSWORD,
		/** A username that no account has */
		UNKNOWN_USER,
		/** Refused for another reason */
		SIGN_IN_REFUSED,
		SIGNED_OUT

	}

	/** Longest username or browser name written; what was typed is not trusted */
	static final int MAX_TEXT = 200;

	private static final Logger logger = LoggerFactory.getLogger(SignInLog.class);

	private static final String PREFIX = "sign-ins-";

	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

	private final Path directory;

	private final Clock clock;

	@Autowired
	public SignInLog(@Value("${attic.database.file}") Path databaseFile, @Value("${attic.log.dir:}") String directory) {
		this(directory.isBlank() ? databaseFile.toAbsolutePath().resolveSibling("logs")
				: Path.of(directory).toAbsolutePath(), Clock.systemDefaultZone());
	}

	SignInLog(Path directory, Clock clock) {
		this.directory = directory;
		this.clock = clock;
	}

	/**
	 * Write the event with the address and the browser of the request being answered.
	 */
	public void record(Event event, @Nullable String username) {
		HttpServletRequest request = (RequestContextHolder
			.getRequestAttributes() instanceof ServletRequestAttributes attributes) ? attributes.getRequest() : null;
		record(event, username, (request != null) ? request.getRemoteAddr() : null,
				(request != null) ? request.getHeader(HttpHeaders.USER_AGENT) : null);
	}

	/**
	 * Write the event. Signing in never fails because of the log: a problem with the file is
	 * reported in the application log instead.
	 */
	public synchronized void record(Event event, @Nullable String username, @Nullable String address,
			@Nullable String userAgent) {
		ZonedDateTime now = ZonedDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		String line = String.join("\t", now.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
				event.name(), clean(username), clean(address), clean(userAgent)) + "\n";
		Path file = directory.resolve(PREFIX + MONTH.format(now) + ".log");
		try {
			if (Files.notExists(directory)) {
				Files.createDirectories(directory);
				ownerOnly(directory, "rwx------");
			}
			boolean created = Files.notExists(file);
			Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			if (created) {
				ownerOnly(file, "rw-------");
				compressOthers(file);
			}
		}
		catch (IOException ex) {
			logger.warn("Could not write {} of {} to {}", event, clean(username), file, ex);
		}
	}

	/** The months before this one are over: compress their files */
	private void compressOthers(Path current) throws IOException {
		try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, PREFIX + "*.log")) {
			for (Path file : files) {
				if (!file.equals(current)) {
					compress(file);
				}
			}
		}
	}

	private void compress(Path file) throws IOException {
		Path compressed = file.resolveSibling(file.getFileName() + ".gz");
		Path temp = Files.createTempFile(directory, ".compress-", ".tmp");
		try {
			try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(temp))) {
				Files.copy(file, out);
			}
			ownerOnly(temp, "rw-------");
			Files.move(temp, compressed, StandardCopyOption.REPLACE_EXISTING);
			Files.delete(file);
		}
		finally {
			Files.deleteIfExists(temp);
		}
	}

	private static void ownerOnly(Path path, String permissions) throws IOException {
		try {
			Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
		}
		catch (UnsupportedOperationException ex) {
			// Not a POSIX file system: the directory's own protection applies
		}
	}

	/** One column of a line: no tabs or line breaks, not too long, a dash when empty */
	private static String clean(@Nullable String text) {
		if (text == null) {
			return "-";
		}
		String cleaned = text.replaceAll("\\p{Cntrl}", " ").strip();
		if (cleaned.length() > MAX_TEXT) {
			cleaned = cleaned.substring(0, MAX_TEXT);
		}
		return cleaned.isEmpty() ? "-" : cleaned;
	}

}
