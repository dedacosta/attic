package com.mephys.attic.repository;

import com.mephys.attic.model.InventoryItem;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

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
 * A database created before Flyway (by the old schema.sql) keeps its data and gets new
 * migrations applied.
 */
@SpringBootTest
class DatabaseMigrationTests {

	private static final UUID EXISTING_ID = UUID.fromString("3c706de5-5a37-4ff0-a19a-dcd7050bd246");

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@BeforeAll
	static void createPreFlywayDatabase() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("attic.db"));
				Statement statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE inventory_item (
						id TEXT PRIMARY KEY, name TEXT NOT NULL, quantity INTEGER NOT NULL DEFAULT 1,
						date TEXT, location TEXT, existent INTEGER NOT NULL DEFAULT 1,
						value_cents INTEGER NOT NULL DEFAULT 0, owner TEXT NOT NULL DEFAULT 'Heritage')
					""");
			statement.execute("""
					CREATE TABLE inventory_picture (
						id TEXT PRIMARY KEY, item_id TEXT NOT NULL UNIQUE REFERENCES inventory_item (id) ON DELETE CASCADE,
						file_name TEXT NOT NULL, content_type TEXT NOT NULL, thumbnail BLOB)
					""");
			statement.execute("INSERT INTO inventory_item (id, name, value_cents) VALUES ('" + EXISTING_ID
					+ "', 'Sofa', 25000)");
		}
	}

	@Autowired
	private InventoryRepository repository;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void keepsExistingDataAndAddsComments() {
		InventoryItem sofa = repository.findById(EXISTING_ID).orElseThrow();
		assertThat(sofa.name()).isEqualTo("Sofa");
		assertThat(sofa.comments()).isNull();

		repository.save(new InventoryItem(sofa.id(), sofa.name(), sofa.quantity(), sofa.date(), sofa.location(),
				sofa.existent(), sofa.valueEur(), sofa.owner(), "Blue velvet"));
		assertThat(repository.findById(EXISTING_ID).orElseThrow().comments()).isEqualTo("Blue velvet");

		assertThat(jdbc.sql("SELECT max(CAST(version AS INTEGER)) FROM flyway_schema_history").query(String.class).single())
			.isEqualTo("12");
	}

}
