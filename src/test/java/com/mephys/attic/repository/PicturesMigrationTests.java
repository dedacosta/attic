package com.mephys.attic.repository;

import com.mephys.attic.model.Picture;
import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.service.PictureStorage;
import com.mephys.attic.support.TestImages;

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
 * The single picture an item or document had before becomes its cover.
 */
@SpringBootTest
class PicturesMigrationTests {

	private static final UUID ITEM = UUID.fromString("11111111-1111-4111-8111-111111111111");

	private static final UUID ITEM_PICTURE = UUID.fromString("22222222-2222-4222-8222-222222222222");

	private static final UUID DOCUMENT = UUID.fromString("33333333-3333-4333-8333-333333333333");

	private static final UUID DOCUMENT_PICTURE = UUID.fromString("44444444-4444-4444-8444-444444444444");

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@BeforeAll
	static void createVersion17Database() throws Exception {
		String url = "jdbc:sqlite:" + tempDir.resolve("attic.db") + "?foreign_keys=true";
		Flyway.configure().dataSource(url, null, null).target("17").load().migrate();
		try (Connection connection = DriverManager.getConnection(url);
				Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO inventory_item (id, name) VALUES ('" + ITEM + "', 'Sofa')");
			statement.execute("INSERT INTO inventory_picture (id, item_id, file_name, content_type) VALUES ('"
					+ ITEM_PICTURE + "', '" + ITEM + "', '" + ITEM_PICTURE + ".png', 'image/png')");
			statement.execute("INSERT INTO heir_document (id, type) VALUES ('" + DOCUMENT + "', 'PASSPORT')");
			statement.execute("INSERT INTO document_picture (id, document_id, file_name, content_type) VALUES ('"
					+ DOCUMENT_PICTURE + "', '" + DOCUMENT + "', '" + DOCUMENT_PICTURE + ".png', 'image/png')");
		}
	}

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private PictureStorage storage;

	@Test
	void existingPicturesBecomeCovers() {
		PictureRepository items = new PictureRepository(jdbc, storage, "inventory_picture", "item_id", "inventory_item");
		PictureRepository documents = new PictureRepository(jdbc, storage, "document_picture", "document_id",
				"heir_document");

		assertThat(items.list(ITEM)).extracting(PictureInfo::id).containsExactly(ITEM_PICTURE);
		assertThat(documents.list(DOCUMENT)).extracting(PictureInfo::id).containsExactly(DOCUMENT_PICTURE);
		assertThat(jdbc.sql("SELECT position FROM inventory_picture").query(Integer.class).single()).isZero();
		assertThat(jdbc.sql("SELECT position FROM document_picture").query(Integer.class).single()).isZero();
		// Owners can have several pictures now
		assertThat(items.add(ITEM, new Picture("image/png", TestImages.png(10, 10)))).isPresent();
		assertThat(items.list(ITEM)).hasSize(2).first().extracting(PictureInfo::id).isEqualTo(ITEM_PICTURE);
	}

}
