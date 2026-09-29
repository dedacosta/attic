package com.mephys.attic.security;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Accounts created before roles existed become administrators, and the oldest of them the
 * super-administrator.
 */
@SpringBootTest
class RoleMigrationTests {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@BeforeAll
	static void createVersion6Database() throws Exception {
		String url = "jdbc:sqlite:" + tempDir.resolve("attic.db");
		Flyway.configure().dataSource(url, null, null).target("6").load().migrate();
		try (Connection connection = DriverManager.getConnection(url);
				Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO app_user (username, password_hash, created_at) VALUES "
					+ "('ana', '{noop}x', '2026-09-29T08:00:00Z'), ('david', '{noop}x', '2026-09-28T23:56:45Z')");
		}
	}

	@Autowired
	private JdbcClient jdbc;

	@Test
	void oldestAccountBecomesSuperAdministrator() {
		assertThat(jdbc.sql("SELECT role FROM app_user WHERE username = 'david'").query(String.class).single())
			.isEqualTo("SUPER_ADMIN");
		assertThat(jdbc.sql("SELECT role FROM app_user WHERE username = 'ana'").query(String.class).single())
			.isEqualTo("ADMIN");
	}

}
