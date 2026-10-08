package com.mephys.attic.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SignInLogTests {

	private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");

	@TempDir
	Path tempDir;

	private SignInLog logAt(String instant) {
		return new SignInLog(tempDir.resolve("logs"), Clock.fixed(Instant.parse(instant), LISBON));
	}

	@Test
	void anEventIsOneLineInTheFileOfItsMonth() throws Exception {
		logAt("2026-10-08T17:30:05Z").record(SignInLog.Event.SIGNED_IN, "david", "100.64.0.7", "Mozilla/5.0 (Android 15)");
		logAt("2026-10-09T08:00:00Z").record(SignInLog.Event.WRONG_PASSWORD, "ana", "100.64.0.9", null);

		Path file = tempDir.resolve("logs/sign-ins-2026-10.log");
		assertThat(Files.readAllLines(file)).containsExactly(
				"2026-10-08T18:30:05+01:00\tSIGNED_IN\tdavid\t100.64.0.7\tMozilla/5.0 (Android 15)",
				"2026-10-09T09:00:00+01:00\tWRONG_PASSWORD\tana\t100.64.0.9\t-");
	}

	@Test
	void whatSomebodyTypedCannotAddLinesOrColumns() throws Exception {
		logAt("2026-10-08T17:30:05Z").record(SignInLog.Event.UNKNOWN_USER, "eve\n2026-10-08T18:30:06+01:00\tSIGNED_IN\tdavid",
				"100.64.0.7", "x\ty\r\n" + "z".repeat(400));

		List<String> lines = Files.readAllLines(tempDir.resolve("logs/sign-ins-2026-10.log"));
		assertThat(lines).hasSize(1);
		String[] columns = lines.get(0).split("\t");
		assertThat(columns).hasSize(5);
		assertThat(columns[2]).isEqualTo("eve 2026-10-08T18:30:06+01:00 SIGNED_IN david");
		assertThat(columns[4]).startsWith("x y  zzz").hasSize(SignInLog.MAX_TEXT);
	}

	@Test
	void aBlankUsernameIsADash() throws Exception {
		logAt("2026-10-08T17:30:05Z").record(SignInLog.Event.UNKNOWN_USER, "  ", null, null);

		assertThat(Files.readAllLines(tempDir.resolve("logs/sign-ins-2026-10.log")))
			.containsExactly("2026-10-08T18:30:05+01:00\tUNKNOWN_USER\t-\t-\t-");
	}

	@Test
	void theFilesOfPastMonthsAreCompressed() throws Exception {
		logAt("2026-09-30T10:00:00Z").record(SignInLog.Event.SIGNED_IN, "david", "100.64.0.7", "Firefox");
		logAt("2026-09-30T11:00:00Z").record(SignInLog.Event.SIGNED_OUT, "david", "100.64.0.7", "Firefox");
		assertThat(tempDir.resolve("logs/sign-ins-2026-09.log")).exists();

		// The first event of October puts September away
		logAt("2026-10-01T09:00:00Z").record(SignInLog.Event.SIGNED_IN, "ana", "100.64.0.9", "Safari");

		assertThat(tempDir.resolve("logs/sign-ins-2026-09.log")).doesNotExist();
		Path compressed = tempDir.resolve("logs/sign-ins-2026-09.log.gz");
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new GZIPInputStream(Files.newInputStream(compressed)), StandardCharsets.UTF_8))) {
			assertThat(reader.lines().toList()).containsExactly(
					"2026-09-30T11:00:00+01:00\tSIGNED_IN\tdavid\t100.64.0.7\tFirefox",
					"2026-09-30T12:00:00+01:00\tSIGNED_OUT\tdavid\t100.64.0.7\tFirefox");
		}
		assertThat(Files.readAllLines(tempDir.resolve("logs/sign-ins-2026-10.log"))).hasSize(1);
	}

	@Test
	void theLogIsReadableByItsOwnerOnly() throws Exception {
		logAt("2026-09-30T10:00:00Z").record(SignInLog.Event.SIGNED_IN, "david", null, null);
		logAt("2026-10-01T09:00:00Z").record(SignInLog.Event.SIGNED_IN, "david", null, null);

		assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(tempDir.resolve("logs")))).isEqualTo("rwx------");
		assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(tempDir.resolve("logs/sign-ins-2026-10.log"))))
			.isEqualTo("rw-------");
		assertThat(PosixFilePermissions
			.toString(Files.getPosixFilePermissions(tempDir.resolve("logs/sign-ins-2026-09.log.gz")))).isEqualTo("rw-------");
	}

}
