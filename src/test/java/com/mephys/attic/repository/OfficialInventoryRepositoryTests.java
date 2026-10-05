package com.mephys.attic.repository;

import com.mephys.attic.model.OfficialInventoryItem;
import com.mephys.attic.model.Location;
import com.mephys.attic.model.Picture;
import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.support.TestImages;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@SpringBootTest(properties = "spring.datasource.hikari.connection-timeout=1000")
class OfficialInventoryRepositoryTests {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@Autowired
	private OfficialInventoryRepository repository;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void newItemHasDefaults() {
		OfficialInventoryItem item = OfficialInventoryItem.of("Lamp");

		assertThat(item.id()).isNotNull();
		assertThat(item.quantity()).isEqualTo(1);
		assertThat(item.date()).isNull();
		assertThat(item.location()).isNull();
		assertThat(item.existent()).isTrue();
		assertThat(item.valueEur()).isEqualTo(new BigDecimal("0.00"));
		assertThat(item.owner()).isEqualTo("Heritage");
		assertThat(item.comments()).isNull();
	}

	@Test
	void blankCommentsBecomeNull() {
		assertThat(new OfficialInventoryItem(null, "Box", 1, null, null, true, null, null, "  \n ").comments()).isNull();
		assertThat(new OfficialInventoryItem(null, "Box", 1, null, null, true, null, null, " Fragile ").comments())
			.isEqualTo("Fragile");
	}

	@Test
	void savesAndLoadsAllFields() {
		OfficialInventoryItem item = new OfficialInventoryItem(null, "Grandfather clock", 2, LocalDate.of(1985, 5, 17), Location.ATTIC,
				false, new BigDecimal("1234.5"), "David", "Inherited from grandmother.\nNeeds a new key.");

		repository.save(item);

		assertThat(repository.findById(item.id())).contains(item);
		assertThat(repository.findById(item.id()).orElseThrow().valueEur()).isEqualTo(new BigDecimal("1234.50"));
	}

	@Test
	void saveUpdatesExistingItem() {
		OfficialInventoryItem item = repository.save(OfficialInventoryItem.of("Chair"));
		OfficialInventoryItem updated = new OfficialInventoryItem(item.id(), "Chair", 4, null, Location.KITCHEN, true, null, null,
				"Wobbly leg");

		repository.save(updated);

		assertThat(repository.findById(item.id())).contains(updated);
	}

	@Test
	void recordsWhenItemsAreAddedAndChanged() throws Exception {
		OfficialInventoryItem item = repository.save(OfficialInventoryItem.of("Lamp"));
		String created = timestamp(item, "created_at");
		assertThat(created).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
		assertThat(timestamp(item, "updated_at")).isEqualTo(created);

		Thread.sleep(5);
		repository.save(new OfficialInventoryItem(item.id(), "Desk lamp", 1, null, null, true, null, null, null));
		assertThat(timestamp(item, "created_at")).isEqualTo(created);
		assertThat(timestamp(item, "updated_at")).isGreaterThan(created);
	}

	private String timestamp(OfficialInventoryItem item, String column) {
		return jdbc.sql("SELECT " + column + " FROM official_inventory_item WHERE id = ?")
			.param(item.id().toString())
			.query(String.class)
			.single();
	}

	@Test
	void deletesItem() {
		OfficialInventoryItem item = repository.save(OfficialInventoryItem.of("Vase"));

		assertThat(repository.deleteById(item.id())).isTrue();
		assertThat(repository.findById(item.id())).isEmpty();
	}

	@Test
	void rejectsInvalidValues() {
		assertThatIllegalArgumentException().isThrownBy(() -> OfficialInventoryItem.of(" "));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new OfficialInventoryItem(null, "Box", -1, null, null, true, null, null, null));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new OfficialInventoryItem(null, "Box", 1, null, null, true, new BigDecimal("-1"), null, null));
	}

	@Test
	void savesPictureAsFileNamedByPictureId() throws Exception {
		OfficialInventoryItem item = repository.save(OfficialInventoryItem.of("Painting"));
		byte[] png = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 };

		PictureInfo info = repository.addPicture(item.id(), new Picture("image/png", png)).orElseThrow();

		String fileName = pictureFileName(item.id());
		assertThat(fileName).isEqualTo(info.id() + ".png");
		assertThat(tempDir.resolve("pictures").resolve(fileName)).hasBinaryContent(png);
		Picture loaded = repository.findPicture(item.id(), info.id()).orElseThrow();
		assertThat(loaded.contentType()).isEqualTo("image/png");
		assertThat(loaded.data()).isEqualTo(png);
	}

	@Test
	void storesThumbnailInDatabase() {
		OfficialInventoryItem item = repository.save(OfficialInventoryItem.of("Table"));

		PictureInfo info = repository.addPicture(item.id(), new Picture("image/jpeg", TestImages.jpeg(1200, 800)))
			.orElseThrow();

		assertThat(info.hasThumbnail()).isTrue();
		assertThat(repository.findThumbnail(item.id(), info.id())).hasValueSatisfying(
				(thumbnail) -> assertThat(thumbnail).startsWith((byte) 0xFF, (byte) 0xD8));
		assertThat(repository.listPictures(item.id())).containsExactly(info);
	}

	@Test
	void storesPictureWithoutThumbnailWhenUndecodable() {
		OfficialInventoryItem item = repository.save(OfficialInventoryItem.of("Poster"));

		PictureInfo info = repository.addPicture(item.id(), new Picture("image/webp", new byte[] { 1, 2 }))
			.orElseThrow();

		assertThat(repository.findPicture(item.id(), info.id())).isPresent();
		assertThat(repository.findThumbnail(item.id(), info.id())).isEmpty();
		assertThat(info.hasThumbnail()).isFalse();
		assertThat(repository.listAllPictures().get(item.id())).containsExactly(info);
	}

	@Test
	void pictureRequiresExistingItem() throws Exception {
		long filesBefore = countPictureFiles();

		assertThat(repository.addPicture(OfficialInventoryItem.of("Ghost").id(), new Picture("image/png", new byte[] { 1 })))
			.isEmpty();
		assertThat(countPictureFiles()).isEqualTo(filesBefore);
	}

	private static long countPictureFiles() throws Exception {
		Path directory = tempDir.resolve("pictures");
		if (!Files.exists(directory)) {
			return 0;
		}
		try (Stream<Path> files = Files.list(directory)) {
			return files.count();
		}
	}

	@Test
	void deletingItemDeletesPictureFile() {
		OfficialInventoryItem item = repository.save(OfficialInventoryItem.of("Mirror"));
		PictureInfo info = repository.addPicture(item.id(), new Picture("image/png", new byte[] { 1 })).orElseThrow();
		String file = pictureFileName(item.id());

		repository.deleteById(item.id());

		assertThat(repository.findPicture(item.id(), info.id())).isEmpty();
		assertThat(tempDir.resolve("pictures").resolve(file)).doesNotExist();
	}

	@Test
	void deletesPicture() {
		OfficialInventoryItem item = repository.save(OfficialInventoryItem.of("Rug"));
		PictureInfo info = repository.addPicture(item.id(), new Picture("image/png", new byte[] { 1 })).orElseThrow();
		String file = pictureFileName(item.id());

		assertThat(repository.deletePicture(item.id(), info.id())).isTrue();
		assertThat(repository.findPicture(item.id(), info.id())).isEmpty();
		assertThat(tempDir.resolve("pictures").resolve(file)).doesNotExist();
		assertThat(repository.findById(item.id())).isPresent();
	}

	@Test
	void listAllPicturesReleasesConnections() {
		// More calls than the pool has connections: a leaked connection per call would time out
		for (int i = 0; i < 25; i++) {
			repository.listAllPictures();
		}
	}

	@Test
	void rejectsInvalidPicture() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Picture("text/plain", new byte[] { 1 }));
		assertThatIllegalArgumentException().isThrownBy(() -> new Picture("image/svg+xml", new byte[] { 1 }));
		assertThatIllegalArgumentException().isThrownBy(() -> new Picture("image/png", new byte[0]));
	}

	private String pictureFileName(UUID itemId) {
		return jdbc.sql("SELECT file_name FROM official_inventory_picture WHERE item_id = ?")
			.param(itemId.toString())
			.query(String.class)
			.single();
	}

}
