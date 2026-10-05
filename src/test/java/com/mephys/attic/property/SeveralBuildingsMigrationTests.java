package com.mephys.attic.property;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

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
 * The building that was the only one is kept, and more can be added.
 */
@SpringBootTest
class SeveralBuildingsMigrationTests {

	private static final UUID BUILDING = UUID.fromString("11111111-1111-4111-8111-111111111111");

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@BeforeAll
	static void createVersion22Database() throws Exception {
		String url = "jdbc:sqlite:" + tempDir.resolve("attic.db") + "?foreign_keys=true";
		Flyway.configure().dataSource(url, null, null).target("22").load().migrate();
		try (Connection connection = DriverManager.getConnection(url);
				Statement statement = connection.createStatement()) {
			// At version 22 a building is still stored as a house
			statement.execute("INSERT INTO property (id, kind, name) VALUES ('" + BUILDING + "', 'HOUSE', 'Casa de Viseu')");
		}
	}

	@Autowired
	private PropertyRepository repository;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void theBuildingIsKeptAndASecondOneIsAccepted() {
		assertThat(repository.findById(BUILDING)).get().extracting(Property::name).isEqualTo("Casa de Viseu");

		jdbc.sql("INSERT INTO property (id, kind, name) VALUES (?, 'BUILDING', 'Casa da praia')")
			.param(UUID.randomUUID().toString())
			.update();

		assertThat(repository.findAll(PropertyKind.BUILDING)).extracting(Property::name)
			.containsExactly("Casa da praia", "Casa de Viseu");
	}

}
