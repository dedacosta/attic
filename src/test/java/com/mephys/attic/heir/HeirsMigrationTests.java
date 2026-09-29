package com.mephys.attic.heir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

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
 * Documents created before heirs existed name their heir; the migration turns each name
 * into an heir and links the documents, keeping their pictures.
 */
@SpringBootTest
class HeirsMigrationTests {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@BeforeAll
	static void createVersion3Database() throws Exception {
		String url = "jdbc:sqlite:" + tempDir.resolve("attic.db") + "?foreign_keys=true";
		Flyway.configure().dataSource(url, null, null).target("3").load().migrate();
		try (Connection connection = DriverManager.getConnection(url);
				Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO person_document (id, person, type) VALUES "
					+ "('11111111-1111-4111-8111-111111111111', 'David', 'PASSPORT'), "
					+ "('22222222-2222-4222-8222-222222222222', 'David', 'ID_CARD'), "
					+ "('33333333-3333-4333-8333-333333333333', 'Ana', 'HEALTH_CARD')");
			statement.execute("INSERT INTO document_picture (id, document_id, file_name, content_type) VALUES "
					+ "('44444444-4444-4444-8444-444444444444', '11111111-1111-4111-8111-111111111111', "
					+ "'44444444-4444-4444-8444-444444444444.jpg', 'image/jpeg')");
		}
	}

	@Autowired
	private JdbcClient jdbc;

	@Test
	void turnsDocumentNamesIntoHeirs() {
		List<Map<String, Object>> heirs = jdbc.sql("SELECT id, name FROM heir ORDER BY name").query().listOfRows();
		assertThat(heirs).extracting((row) -> row.get("name")).containsExactly("Ana", "David");
		assertThat(heirs).extracting((row) -> (String) row.get("id"))
			.allMatch((id) -> id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));

		List<String> davidsDocuments = jdbc.sql("""
				SELECT d.type FROM heir_document d JOIN heir p ON p.id = d.heir_id
				WHERE p.name = 'David' ORDER BY d.type
				""").query(String.class).list();
		assertThat(davidsDocuments).containsExactly("ID_CARD", "PASSPORT");
		assertThat(jdbc.sql("SELECT count(*) FROM heir_document WHERE heir_id IS NULL").query(Integer.class).single())
			.isZero();
	}

	@Test
	void renamesPeopleToHeirsKeepingEveryLink() {
		// No table, column or index is called person any more
		List<String> schema = jdbc.sql("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL").query(String.class).list();
		assertThat(schema).noneMatch((definition) -> definition.toLowerCase().contains("person"));
		// Every foreign key still points to an existing row
		assertThat(jdbc.sql("PRAGMA foreign_key_check").query().listOfRows()).isEmpty();
		// The picture of a document still belongs to it, through the renamed table
		assertThat(jdbc.sql("""
				SELECT count(*) FROM document_picture dp JOIN heir_document d ON d.id = dp.document_id
				JOIN heir h ON h.id = d.heir_id WHERE h.name = 'David'
				""").query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void keepsDocumentPictures() {
		assertThat(jdbc.sql("SELECT count(*) FROM document_picture").query(Integer.class).single()).isEqualTo(1);
	}

}
