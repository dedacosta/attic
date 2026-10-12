package com.mephys.attic.repository;

import com.mephys.attic.model.InventoryItem;
import com.mephys.attic.model.InventoryRoom;
import com.mephys.attic.model.Picture;
import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.model.Property;
import com.mephys.attic.model.PropertyKind;
import com.mephys.attic.support.TestImages;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
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
class InventoryRepositoryTests {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@Autowired
	private InventoryRepository repository;

	@Autowired
	private JdbcClient jdbc;

	/** The building whose inventory the tests fill */
	private static UUID house;

	@Autowired
	private PropertyRepository properties;

	@BeforeEach
	void createHouse() {
		if (house == null) {
			house = properties.save(Property.of(null, PropertyKind.BUILDING, "House", null, null, null, null)).id();
		}
	}

	private static InventoryRoom room(String title, InventoryItem... items) {
		return new InventoryRoom(null, house, title, null, null, null, List.of(items));
	}

	@Test
	void newItemHasDefaults() {
		InventoryItem item = InventoryItem.of(" Lamp ", null, null);

		assertThat(item.description()).isEqualTo("Lamp");
		assertThat(item.quantity()).isEqualTo(1);
		assertThat(item.valueEur()).isEqualTo(new BigDecimal("0.00"));
	}

	@Test
	void roomIsTidiedUp() {
		InventoryRoom room = new InventoryRoom(null, house, " Kitchen ", null, null, "  \n ", null);

		assertThat(room.id()).isNotNull();
		assertThat(room.title()).isEqualTo("Kitchen");
		assertThat(room.comments()).isNull();
		assertThat(room.items()).isEmpty();
		assertThat(new InventoryRoom(null, house, "Hall", null, null, " Ground floor ", null).comments()).isEqualTo("Ground floor");
	}

	@Test
	void savesAndLoadsARoomWithItsItemsInOrder() {
		InventoryRoom room = new InventoryRoom(null, house, "Living room", null, " Salon ", "Ground floor.\nFaces the garden.",
				List.of(new InventoryItem("Sofa", 1, new BigDecimal("450")), new InventoryItem("Chairs", 6, new BigDecimal("120.5")),
						new InventoryItem("Armchair", 2, null)));

		repository.save(room);

		assertThat(repository.findById(room.id())).contains(room);
		assertThat(repository.findById(room.id()).orElseThrow().pdfTitle()).isEqualTo("Salon");
		assertThat(repository.findById(room.id()).orElseThrow().items().get(1).valueEur())
			.isEqualTo(new BigDecimal("120.50"));
		assertThat(repository.findAll(house)).contains(room);
	}

	@Test
	void saveReplacesTheItemsOfAnExistingRoom() {
		InventoryRoom room = repository.save(room("Office", new InventoryItem("Desk", 1, null),
				new InventoryItem("Lamp", 2, null)));
		InventoryRoom updated = new InventoryRoom(room.id(), house, "Study", null, null, "Upstairs",
				List.of(new InventoryItem("Bookcase", 3, null)));

		repository.save(updated);

		assertThat(repository.findById(room.id())).contains(updated);
	}

	@Test
	void listsRoomsInTheOrderTheyWereAdded() throws Exception {
		InventoryRoom first = repository.save(room("Zebra room"));
		Thread.sleep(5);
		InventoryRoom second = repository.save(room("Attic"));
		Thread.sleep(5);
		repository.save(new InventoryRoom(first.id(), house, "Zebra room, renamed", null, null, null, List.of()));

		assertThat(repository.findAll(house).stream().map(InventoryRoom::id).filter(List.of(first.id(), second.id())::contains))
			.containsExactly(first.id(), second.id());
	}

	@Test
	void recordsWhenRoomsAreAddedAndChanged() throws Exception {
		InventoryRoom room = repository.save(room("Hall"));
		String created = timestamp(room, "created_at");
		assertThat(created).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
		assertThat(timestamp(room, "updated_at")).isEqualTo(created);

		Thread.sleep(5);
		repository.save(new InventoryRoom(room.id(), house, "Entrance hall", null, null, null, List.of()));
		assertThat(timestamp(room, "created_at")).isEqualTo(created);
		assertThat(timestamp(room, "updated_at")).isGreaterThan(created);
	}

	private String timestamp(InventoryRoom room, String column) {
		return jdbc.sql("SELECT " + column + " FROM inventory_room WHERE id = ?")
			.param(room.id().toString())
			.query(String.class)
			.single();
	}

	@Test
	void deletesRoomWithItsItems() {
		InventoryRoom room = repository.save(room("Pantry", new InventoryItem("Jars", 20, null)));

		assertThat(repository.deleteById(room.id())).isTrue();
		assertThat(repository.findById(room.id())).isEmpty();
		assertThat(jdbc.sql("SELECT count(*) FROM inventory_item WHERE room_id = ?")
			.param(room.id().toString())
			.query(Integer.class)
			.single()).isZero();
		assertThat(repository.deleteById(room.id())).isFalse();
	}

	@Test
	void otherItemsAreKeptApartFromTheRooms() {
		InventoryRoom room = repository.save(room("Cellar", new InventoryItem("Barrel", 1, null)));
		List<InventoryItem> others = List.of(new InventoryItem("Ladder", 1, null), new InventoryItem("Tools", 12, null));

		repository.replaceOtherItems(house, others);

		assertThat(repository.findOtherItems(house)).isEqualTo(others);
		assertThat(repository.findById(room.id())).contains(room);

		repository.replaceOtherItems(house, List.of(new InventoryItem("Ladder", 2, null)));
		assertThat(repository.findOtherItems(house)).containsExactly(new InventoryItem("Ladder", 2, null));
		assertThat(repository.findById(room.id())).contains(room);
		repository.replaceOtherItems(house, List.of());
	}

	@Test
	void missingItemsAreAListOfTheirOwn() {
		List<InventoryItem> others = List.of(new InventoryItem("Wheelbarrow", 1, new BigDecimal("40")));
		List<InventoryItem> missing = List.of(new InventoryItem("Silver tray", 1, new BigDecimal("300")),
				new InventoryItem("Candlesticks", 2, null));

		repository.replaceOtherItems(house, others);
		repository.replaceMissingItems(house, missing);

		assertThat(repository.findMissingItems(house)).isEqualTo(missing);
		assertThat(repository.findOtherItems(house)).isEqualTo(others);

		repository.replaceMissingItems(house, List.of());
		assertThat(repository.findMissingItems(house)).isEmpty();
		assertThat(repository.findOtherItems(house)).isEqualTo(others);
		repository.replaceOtherItems(house, List.of());
	}

	@Test
	void rejectsInvalidValues() {
		assertThatIllegalArgumentException().isThrownBy(() -> room(" "));
		assertThatIllegalArgumentException().isThrownBy(() -> new InventoryRoom(null, null, "Hall", null, null, null, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new InventoryItem(" ", 1, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new InventoryItem("Box", -1, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new InventoryItem("Box", 1, new BigDecimal("-1")));
	}

	@Test
	void savesPictureAsFileNamedByPictureId() throws Exception {
		InventoryRoom room = repository.save(room("Painting"));
		byte[] png = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 };

		PictureInfo info = repository.addPicture(room.id(), new Picture("image/png", png)).orElseThrow();

		String fileName = pictureFileName(room.id());
		assertThat(fileName).isEqualTo(info.id() + ".png");
		assertThat(tempDir.resolve("pictures").resolve(fileName)).hasBinaryContent(png);
		Picture loaded = repository.findPicture(room.id(), info.id()).orElseThrow();
		assertThat(loaded.contentType()).isEqualTo("image/png");
		assertThat(loaded.data()).isEqualTo(png);
	}

	@Test
	void storesThumbnailInDatabase() {
		InventoryRoom room = repository.save(room("Table"));

		PictureInfo info = repository.addPicture(room.id(), new Picture("image/jpeg", TestImages.jpeg(1200, 800)))
			.orElseThrow();

		assertThat(info.hasThumbnail()).isTrue();
		assertThat(repository.findThumbnail(room.id(), info.id())).hasValueSatisfying(
				(thumbnail) -> assertThat(thumbnail).startsWith((byte) 0xFF, (byte) 0xD8));
		assertThat(repository.listPictures(room.id())).containsExactly(info);
	}

	@Test
	void storesPictureWithoutThumbnailWhenUndecodable() {
		InventoryRoom room = repository.save(room("Poster"));

		PictureInfo info = repository.addPicture(room.id(), new Picture("image/webp", new byte[] { 1, 2 }))
			.orElseThrow();

		assertThat(repository.findPicture(room.id(), info.id())).isPresent();
		assertThat(repository.findThumbnail(room.id(), info.id())).isEmpty();
		assertThat(info.hasThumbnail()).isFalse();
		assertThat(repository.listAllPictures().get(room.id())).containsExactly(info);
	}

	@Test
	void pictureRequiresExistingRoom() throws Exception {
		long filesBefore = countPictureFiles();

		assertThat(repository.addPicture(room("Ghost").id(), new Picture("image/png", new byte[] { 1 })))
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
	void deletingRoomDeletesPictureFile() {
		InventoryRoom room = repository.save(room("Mirror"));
		PictureInfo info = repository.addPicture(room.id(), new Picture("image/png", new byte[] { 1 })).orElseThrow();
		String file = pictureFileName(room.id());

		repository.deleteById(room.id());

		assertThat(repository.findPicture(room.id(), info.id())).isEmpty();
		assertThat(tempDir.resolve("pictures").resolve(file)).doesNotExist();
	}

	@Test
	void deletesPicture() {
		InventoryRoom room = repository.save(room("Rug"));
		PictureInfo info = repository.addPicture(room.id(), new Picture("image/png", new byte[] { 1 })).orElseThrow();
		String file = pictureFileName(room.id());

		assertThat(repository.deletePicture(room.id(), info.id())).isTrue();
		assertThat(repository.findPicture(room.id(), info.id())).isEmpty();
		assertThat(tempDir.resolve("pictures").resolve(file)).doesNotExist();
		assertThat(repository.findById(room.id())).isPresent();
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

	private String pictureFileName(UUID roomId) {
		return jdbc.sql("SELECT file_name FROM inventory_picture WHERE room_id = ?")
			.param(roomId.toString())
			.query(String.class)
			.single();
	}

}
