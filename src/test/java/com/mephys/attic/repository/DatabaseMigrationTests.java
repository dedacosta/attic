package com.mephys.attic.repository;

import com.mephys.attic.model.CatalogItem;

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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A database that already has the schema but no Flyway history (one from before the migrations
 * were squashed, with its history dropped) keeps its data and is marked as V1, which is not run
 * again.
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
	static void createDatabaseWithoutHistory() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("attic.db"));
				Statement statement = connection.createStatement()) {
			ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__schema.sql"));
			statement.execute("INSERT INTO catalog_item (id, name, value_cents) VALUES ('" + EXISTING_ID
					+ "', 'Sofa', 25000)");
		}
	}

	@Autowired
	private CatalogRepository repository;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void keepsExistingDataAndIsMarkedAsV1() {
		CatalogItem sofa = repository.findById(EXISTING_ID).orElseThrow();
		assertThat(sofa.name()).isEqualTo("Sofa");

		assertThat(jdbc.sql("SELECT version || ' ' || type FROM flyway_schema_history ORDER BY installed_rank")
			.query(String.class)
			.list()).containsExactly("1 BASELINE");
	}

}
