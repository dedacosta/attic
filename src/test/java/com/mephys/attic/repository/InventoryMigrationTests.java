package com.mephys.attic.repository;

import com.mephys.attic.model.InventoryItem;
import com.mephys.attic.model.PictureInfo;

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
 * The items and photos of the official inventory are still there when it has become the inventory.
 */
@SpringBootTest
class InventoryMigrationTests {

	private static final UUID ITEM = UUID.fromString("11111111-1111-4111-8111-111111111111");

	private static final UUID PICTURE = UUID.fromString("22222222-2222-4222-8222-222222222222");

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@BeforeAll
	static void createVersion21Database() throws Exception {
		String url = "jdbc:sqlite:" + tempDir.resolve("attic.db") + "?foreign_keys=true";
		Flyway.configure().dataSource(url, null, null).target("21").load().migrate();
		try (Connection connection = DriverManager.getConnection(url);
				Statement statement = connection.createStatement()) {
			// At version 21 the inventory is still called the official inventory
			statement.execute("INSERT INTO official_inventory_item (id, name, quantity) VALUES ('" + ITEM + "', 'Sofa', 2)");
			statement.execute("INSERT INTO official_inventory_picture (id, item_id, position, file_name, content_type) VALUES ('"
					+ PICTURE + "', '" + ITEM + "', 0, '" + PICTURE + ".png', 'image/png')");
		}
	}

	@Autowired
	private InventoryRepository repository;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void itemsAndPhotosAreKept() {
		InventoryItem sofa = repository.findById(ITEM).orElseThrow();
		assertThat(sofa.name()).isEqualTo("Sofa");
		assertThat(sofa.quantity()).isEqualTo(2);
		assertThat(repository.listPictures(ITEM)).extracting(PictureInfo::id).containsExactly(PICTURE);
	}

	@Test
	void deletingAnItemStillDeletesItsPhotoRows() {
		UUID id = repository.save(InventoryItem.of("Chair")).id();
		jdbc.sql("INSERT INTO inventory_picture (id, item_id, position, file_name, content_type) VALUES (?, ?, 0, 'x.png', 'image/png')")
			.params(UUID.randomUUID().toString(), id.toString())
			.update();

		repository.deleteById(id);

		// The foreign key followed the renamed table, so the cascade still works
		assertThat(jdbc.sql("SELECT count(*) FROM inventory_picture WHERE item_id = ?").param(id.toString())
			.query(Integer.class)
			.single()).isZero();
	}

	@Test
	void theOfficialInventoryTablesAreGone() {
		assertThat(jdbc.sql("SELECT count(*) FROM sqlite_master WHERE name LIKE 'official%'").query(Integer.class).single())
			.isZero();
	}

}
