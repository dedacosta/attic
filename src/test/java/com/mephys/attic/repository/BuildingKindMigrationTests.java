package com.mephys.attic.repository;

import com.mephys.attic.model.Building;
import com.mephys.attic.model.Land;
import com.mephys.attic.model.Property;
import com.mephys.attic.model.PropertyFact;

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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * What was stored as a house is a building, with everything that belongs to it.
 */
@SpringBootTest
class BuildingKindMigrationTests {

	private static final UUID HOUSE = UUID.fromString("11111111-1111-4111-8111-111111111111");

	private static final UUID LAND = UUID.fromString("22222222-2222-4222-8222-222222222222");

	private static final UUID PICTURE = UUID.fromString("33333333-3333-4333-8333-333333333333");

	private static final UUID DOCUMENT = UUID.fromString("44444444-4444-4444-8444-444444444444");

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@BeforeAll
	static void createVersion23Database() throws Exception {
		String url = "jdbc:sqlite:" + tempDir.resolve("attic.db") + "?foreign_keys=true";
		Flyway.configure().dataSource(url, null, null).target("23").load().migrate();
		try (Connection connection = DriverManager.getConnection(url);
				Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO property (id, kind, name, address, value_cents, comments) VALUES ('" + HOUSE
					+ "', 'HOUSE', 'Casa de Viseu', 'Rua Direita 1', 25000000, 'Roof redone')");
			statement.execute("INSERT INTO property (id, kind, name) VALUES ('" + LAND + "', 'LAND', 'Vinha')");
			statement.execute("INSERT INTO property_fact (property_id, position, label, value) VALUES ('" + HOUSE
					+ "', 0, 'Divisões', '7')");
			statement.execute("INSERT INTO property_picture (id, property_id, position, file_name, content_type) VALUES ('"
					+ PICTURE + "', '" + HOUSE + "', 0, '" + PICTURE + ".png', 'image/png')");
			statement.execute("INSERT INTO property_document (id, property_id, type) VALUES ('" + DOCUMENT + "', '"
					+ HOUSE + "', 'TITLE_DEED')");
		}
	}

	@Autowired
	private PropertyRepository repository;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void theHouseIsABuildingWithAllItHad() {
		Property building = repository.findById(HOUSE).orElseThrow();

		assertThat(building).isInstanceOf(Building.class);
		assertThat(building.name()).isEqualTo("Casa de Viseu");
		assertThat(building.address()).isEqualTo("Rua Direita 1");
		assertThat(building.valueEur()).isEqualByComparingTo("250000");
		assertThat(building.comments()).isEqualTo("Roof redone");
		assertThat(building.facts()).extracting(PropertyFact::label).containsExactly("Divisões");
		assertThat(repository.listPictures(HOUSE)).hasSize(1);
		assertThat(repository.findDocuments(HOUSE)).hasSize(1);
	}

	@Test
	void theLandIsStillLand() {
		assertThat(repository.findById(LAND)).get().isInstanceOf(Land.class);
	}

	@Test
	void theDatabaseNoLongerAcceptsAHouse() {
		assertThatExceptionOfType(DataAccessException.class).isThrownBy(
				() -> jdbc.sql("INSERT INTO property (id, kind, name) VALUES (?, 'HOUSE', 'Outra')")
					.param(UUID.randomUUID().toString())
					.update());
	}

}
