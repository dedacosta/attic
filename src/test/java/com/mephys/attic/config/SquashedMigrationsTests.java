package com.mephys.attic.config;

import com.mephys.attic.model.CatalogItem;
import com.mephys.attic.repository.CatalogRepository;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.sqlite.SQLiteDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * A database brought up to date with the migrations that V1 replaces keeps its data and gets a
 * history as if it had been marked as V1, and gets the migrations after V1. One that stopped before
 * the last of them is refused.
 */
@SpringBootTest
@SuppressWarnings("SqlResolve") // Flyway's history is not in the migrations
class SquashedMigrationsTests {

	private static final UUID EXISTING_ID = UUID.fromString("0f4b7a2e-6d1c-4e8a-9b35-2c7d1e9a4f60");

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@BeforeAll
	static void createDatabaseWithTheReplacedMigrations() throws Exception {
		createDatabase(tempDir.resolve("attic.db"), SquashedMigrations.REPLACED);
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("attic.db"));
				Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO catalog_item (id, name) VALUES ('" + EXISTING_ID + "', 'Clock')");
		}
	}

	/** A database with the schema and a history of V1 and the given migrations */
	private static void createDatabase(Path file, List<String> migrations) throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
				Statement statement = connection.createStatement()) {
			ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__schema.sql"));
			statement.execute("""
					CREATE TABLE flyway_schema_history (installed_rank INT NOT NULL PRIMARY KEY, version VARCHAR(50),
						description VARCHAR(200) NOT NULL, type VARCHAR(20) NOT NULL, script VARCHAR(1000) NOT NULL,
						checksum INT, installed_by VARCHAR(100) NOT NULL, installed_on TEXT NOT NULL,
						execution_time INT NOT NULL, success BOOLEAN NOT NULL)
					""");
			statement.execute("INSERT INTO flyway_schema_history VALUES (1, '1', 'schema', 'SQL', 'V1__schema.sql', "
					+ "1, 'attic', '2026-10-01', 1, 1)");
			for (int i = 0; i < migrations.size(); i++) {
				statement.execute("INSERT INTO flyway_schema_history VALUES (" + (i + 2) + ", '" + (i + 2)
						+ "', 'migration', 'SQL', '" + migrations.get(i) + "', 1, 'attic', '2026-10-01', 1, 1)");
			}
		}
	}

	@Autowired
	private CatalogRepository repository;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void keepsTheDataAndIsMarkedAsV1() {
		CatalogItem clock = repository.findById(EXISTING_ID).orElseThrow();
		assertThat(clock.name()).isEqualTo("Clock");

		assertThat(jdbc.sql("SELECT version || ' ' || type FROM flyway_schema_history ORDER BY installed_rank")
			.query(String.class)
			.list()).startsWith("1 BASELINE", "2 SQL").doesNotContain("1 SQL");
	}

	@Test
	void refusesADatabaseThatStoppedBeforeTheLastReplacedMigration() throws Exception {
		Path file = tempDir.resolve("older.db");
		createDatabase(file, SquashedMigrations.REPLACED.subList(0, 6));
		SQLiteDataSource dataSource = new SQLiteDataSource();
		dataSource.setUrl("jdbc:sqlite:" + file);

		assertThatIllegalStateException().isThrownBy(() -> SquashedMigrations.convert(dataSource))
			.withMessageContaining("V7__inventory_room_description.sql");
		// Its history is left as it was: V1 and the six migrations it ran
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT count(*) FROM flyway_schema_history")) {
			assertThat(rows.getInt(1)).isEqualTo(7);
		}
	}

	@Test
	void leavesADatabaseCreatedWithV1Alone() throws Exception {
		Path file = tempDir.resolve("new.db");
		createDatabase(file, List.of());
		SQLiteDataSource dataSource = new SQLiteDataSource();
		dataSource.setUrl("jdbc:sqlite:" + file);

		SquashedMigrations.convert(dataSource);

		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT count(*) FROM flyway_schema_history")) {
			assertThat(rows.getInt(1)).isOne();
		}
	}

}
