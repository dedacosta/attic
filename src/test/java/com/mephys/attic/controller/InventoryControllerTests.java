package com.mephys.attic.controller;

import com.mephys.attic.support.TestImages;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "attic.picture.max-size=100KB")
@AutoConfigureMockMvc
@Import(SignedInMockMvc.class)
class InventoryControllerTests {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@Autowired
	private MockMvc mvc;

	/** The building whose inventory the tests fill */
	private static String house;

	@BeforeEach
	void createHouse() throws Exception {
		if (house == null) {
			house = createBuilding("House");
		}
	}

	private String createBuilding(String name) throws Exception {
		String body = mvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"BUILDING\",\"name\":\"" + name + "\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	/** The room of the JSON, in the house */
	private static String inHouse(String json) {
		return json.replaceFirst("\\{", "{\"propertyId\":\"" + house + "\",");
	}

	@Test
	void createAppliesDefaults() throws Exception {
		mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON)
			.content(inHouse("{\"title\":\" Kitchen \",\"items\":[{\"description\":\"Copper pan\"}]}")))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", startsWith("http://localhost/api/inventory/")))
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.propertyId").value(house))
			.andExpect(jsonPath("$.title").value("Kitchen"))
			.andExpect(jsonPath("$.comments").doesNotExist())
			.andExpect(jsonPath("$.items[0].description").value("Copper pan"))
			.andExpect(jsonPath("$.items[0].quantity").value(1))
			.andExpect(jsonPath("$.items[0].valueEur").value(0.0))
			.andExpect(jsonPath("$.pictures").isEmpty());
		mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content(inHouse("{\"title\":\"Hall\"}")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void createGetReplaceDelete() throws Exception {
		String id = create("""
				{"title":"Living room","comments":"Ground floor","items":[
				 {"description":"Sofa","quantity":1,"valueEur":450},
				 {"description":"Chairs","quantity":6,"valueEur":120.5}]}
				""");

		mvc.perform(get("/api/inventory/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Living room"))
			.andExpect(jsonPath("$.comments").value("Ground floor"))
			.andExpect(jsonPath("$.items[*].description").value(contains("Sofa", "Chairs")))
			.andExpect(jsonPath("$.items[1].quantity").value(6))
			.andExpect(jsonPath("$.items[1].valueEur").value(120.5));
		mvc.perform(get("/api/inventory")).andExpect(jsonPath("$[?(@.id == '" + id + "')].items[0].description").value("Sofa"));

		// The items of the request replace the room's items, in the order given
		mvc.perform(put("/api/inventory/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Salon\",\"items\":[{\"description\":\"Piano\"},{\"description\":\"Sofa\",\"quantity\":2}]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Salon"))
			.andExpect(jsonPath("$.comments").doesNotExist())
			.andExpect(jsonPath("$.items[*].description").value(contains("Piano", "Sofa")))
			.andExpect(jsonPath("$.items[1].quantity").value(2));

		mvc.perform(delete("/api/inventory/{id}", id)).andExpect(status().isNoContent());
		mvc.perform(get("/api/inventory/{id}", id)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/inventory/{id}", id)).andExpect(status().isNotFound());
		assertThat(jdbc.sql("SELECT count(*) FROM inventory_item WHERE room_id = ?").param(id).query(Integer.class).single())
			.isZero();
	}

	@Test
	void replaceUnknownRoomIsNotFound() throws Exception {
		mvc.perform(put("/api/inventory/{id}", "00000000-0000-0000-0000-000000000000").contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"X\"}")).andExpect(status().isNotFound());
	}

	@Test
	void invalidRoomIsBadRequest() throws Exception {
		mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content(inHouse("{\"title\":\" \"}")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("title must not be blank"));
		mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON)
			.content(inHouse("{\"title\":\"Box room\",\"items\":[{\"description\":\"\"}]}")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("description must not be blank"));
		mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON)
			.content(inHouse("{\"title\":\"Box room\",\"items\":[{\"description\":\"Box\",\"quantity\":-1}]}")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void eachBuildingHasItsOwnInventory() throws Exception {
		String cottage = createBuilding("Cottage");
		String mine = create("{\"title\":\"Library\"}");
		String theirs = JsonPath.read(mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON)
			.content("{\"propertyId\":\"" + cottage + "\",\"title\":\"Loft\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");
		mvc.perform(put("/api/inventory/other-items").param("propertyId", cottage).contentType(MediaType.APPLICATION_JSON)
			.content("[{\"description\":\"Canoe\"}]")).andExpect(status().isOk());

		mvc.perform(get("/api/inventory").param("propertyId", cottage))
			.andExpect(jsonPath("$[*].id").value(contains(theirs)));
		mvc.perform(get("/api/inventory").param("propertyId", house))
			.andExpect(jsonPath("$[?(@.id == '" + mine + "')]").exists())
			.andExpect(jsonPath("$[?(@.id == '" + theirs + "')]").doesNotExist());
		mvc.perform(get("/api/inventory/other-items").param("propertyId", house))
			.andExpect(jsonPath("$[?(@.description == 'Canoe')]").doesNotExist());

		// A room stays in its building
		mvc.perform(put("/api/inventory/{id}", theirs).contentType(MediaType.APPLICATION_JSON)
			.content(inHouse("{\"title\":\"Loft\"}"))).andExpect(jsonPath("$.propertyId").value(cottage));

		// Deleting the building deletes its inventory
		mvc.perform(delete("/api/properties/{id}", cottage)).andExpect(status().isNoContent());
		mvc.perform(get("/api/inventory/{id}", theirs)).andExpect(status().isNotFound());
		assertThat(jdbc.sql("SELECT count(*) FROM inventory_item WHERE property_id = ?").param(cottage).query(Integer.class).single())
			.isZero();
	}

	@Test
	void theRoomsOfABuildingAreEnteredAsAList() throws Exception {
		String villa = createBuilding("Villa");
		String list = mvc.perform(put("/api/inventory/rooms").param("propertyId", villa).contentType(MediaType.APPLICATION_JSON)
			.content("[{\"title\":\"Hall\"},{\"title\":\" Kitchen \",\"description\":\" Next to the hall \"},{\"title\":\"Cellar\"}]"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].title").value(contains("Hall", "Kitchen", "Cellar")))
			.andExpect(jsonPath("$[0].description").doesNotExist())
			.andExpect(jsonPath("$[1].description").value("Next to the hall"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String hall = JsonPath.read(list, "$[0].id");
		String kitchen = JsonPath.read(list, "$[1].id");
		String cellar = JsonPath.read(list, "$[2].id");
		mvc.perform(put("/api/inventory/{id}", kitchen).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Kitchen\",\"pdfTitle\":\" Cuisine \",\"comments\":\"North side\",\"items\":[{\"description\":\"Stove\"}]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Kitchen"))
			// The description is entered on the building; the inventory keeps it
			.andExpect(jsonPath("$.description").value("Next to the hall"))
			.andExpect(jsonPath("$.pdfTitle").value("Cuisine"));
		addPhoto(cellar, TestImages.png(10, 10));
		long filesBefore = countPictureFiles();

		// Reordered, one renamed, one added and one left out
		mvc.perform(put("/api/inventory/rooms").param("propertyId", villa).contentType(MediaType.APPLICATION_JSON)
			.content("[{\"id\":\"" + kitchen + "\",\"title\":\"Big kitchen\",\"description\":\"With the old stove\"},{\"title\":\"Attic\"},{\"id\":\"" + hall
					+ "\",\"title\":\"Hall\"}]"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].title").value(contains("Big kitchen", "Attic", "Hall")))
			.andExpect(jsonPath("$[0].id").value(kitchen))
			.andExpect(jsonPath("$[0].description").value("With the old stove"))
			.andExpect(jsonPath("$[0].pdfTitle").value("Cuisine"))
			.andExpect(jsonPath("$[1].pdfTitle").doesNotExist())
			.andExpect(jsonPath("$[0].comments").value("North side"))
			.andExpect(jsonPath("$[0].items[0].description").value("Stove"));
		mvc.perform(get("/api/inventory/{id}", cellar)).andExpect(status().isNotFound());
		assertThat(countPictureFiles()).isEqualTo(filesBefore - 1);

		// A room added from the inventory comes last
		mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON)
			.content("{\"propertyId\":\"" + villa + "\",\"title\":\"Garage\"}")).andExpect(status().isCreated());
		mvc.perform(get("/api/inventory").param("propertyId", villa))
			.andExpect(jsonPath("$[*].title").value(contains("Big kitchen", "Attic", "Hall", "Garage")));

		// Not a room of another building, not twice, not without a title
		String foreign = create("{\"title\":\"Elsewhere\"}");
		for (String body : new String[] { "[{\"id\":\"" + foreign + "\",\"title\":\"Stolen\"}]",
				"[{\"id\":\"" + hall + "\",\"title\":\"Hall\"},{\"id\":\"" + hall + "\",\"title\":\"Hall\"}]",
				"[{\"title\":\" \"}]" }) {
			mvc.perform(put("/api/inventory/rooms").param("propertyId", villa).contentType(MediaType.APPLICATION_JSON)
				.content(body)).andExpect(status().isBadRequest());
		}
		mvc.perform(get("/api/inventory").param("propertyId", villa))
			.andExpect(jsonPath("$[*].title").value(contains("Big kitchen", "Attic", "Hall", "Garage")));
		mvc.perform(get("/api/inventory/{id}", foreign)).andExpect(status().isOk());
	}

	@Test
	void missingItemsAreAListOfTheirOwn() throws Exception {
		String manor = createBuilding("Manor");
		mvc.perform(put("/api/inventory/other-items").param("propertyId", manor).contentType(MediaType.APPLICATION_JSON)
			.content("[{\"description\":\"Ladder\"}]")).andExpect(status().isOk());

		mvc.perform(put("/api/inventory/missing-items").param("propertyId", manor).contentType(MediaType.APPLICATION_JSON)
			.content("[{\"description\":\"Silver tray\",\"valueEur\":300},{\"description\":\"Candlesticks\",\"quantity\":2}]"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].description").value(contains("Silver tray", "Candlesticks")));

		mvc.perform(get("/api/inventory/missing-items").param("propertyId", manor))
			.andExpect(jsonPath("$[*].description").value(contains("Silver tray", "Candlesticks")))
			.andExpect(jsonPath("$[0].valueEur").value(300.0))
			.andExpect(jsonPath("$[1].quantity").value(2));
		mvc.perform(get("/api/inventory/other-items").param("propertyId", manor))
			.andExpect(jsonPath("$[*].description").value(contains("Ladder")));
		mvc.perform(put("/api/inventory/missing-items").param("propertyId", manor).contentType(MediaType.APPLICATION_JSON)
			.content("[{\"description\":\"Tray\",\"valueEur\":-1}]")).andExpect(status().isBadRequest());
	}

	@Test
	void inventoryNeedsABuilding() throws Exception {
		String land = JsonPath.read(mvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"LAND\",\"name\":\"Field\"}")).andReturn().getResponse().getContentAsString(), "$.id");

		for (String body : new String[] { "{\"title\":\"Nowhere\"}",
				"{\"propertyId\":\"00000000-0000-0000-0000-000000000000\",\"title\":\"Nowhere\"}",
				"{\"propertyId\":\"" + land + "\",\"title\":\"Shed\"}" }) {
			mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("the inventory is kept per building"));
		}
		mvc.perform(put("/api/inventory/other-items").param("propertyId", land).contentType(MediaType.APPLICATION_JSON)
			.content("[]")).andExpect(status().isBadRequest());
	}

	@Test
	void otherItemsHaveNoRoom() throws Exception {
		String room = create("{\"title\":\"Cellar\",\"items\":[{\"description\":\"Barrel\"}]}");

		mvc.perform(put("/api/inventory/other-items").param("propertyId", house).contentType(MediaType.APPLICATION_JSON)
			.content("[{\"description\":\"Ladder\"},{\"description\":\"Tools\",\"quantity\":12,\"valueEur\":80}]"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].description").value(contains("Ladder", "Tools")));
		mvc.perform(get("/api/inventory/other-items").param("propertyId", house))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].description").value(contains("Ladder", "Tools")))
			.andExpect(jsonPath("$[0].quantity").value(1))
			.andExpect(jsonPath("$[1].valueEur").value(80.0));
		// The rooms keep their own items
		mvc.perform(get("/api/inventory/{id}", room)).andExpect(jsonPath("$.items[*].description").value(contains("Barrel")));

		mvc.perform(put("/api/inventory/other-items").param("propertyId", house).contentType(MediaType.APPLICATION_JSON).content("[]"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/inventory/other-items").param("propertyId", house)).andExpect(jsonPath("$").isEmpty());
		mvc.perform(get("/api/inventory/{id}", room)).andExpect(jsonPath("$.items[*].description").value(contains("Barrel")));
	}

	@Autowired
	private JdbcClient jdbc;

	@Test
	void roomPictureCannotBeAPdf() throws Exception {
		String id = create("{\"title\":\"Painting\"}");
		mvc.perform(post("/api/inventory/{id}/pictures", id).contentType(MediaType.APPLICATION_PDF).content("%PDF-1.7"))
			.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void photosKeepTheirOrderAndTheFirstIsTheCover() throws Exception {
		String id = create("{\"title\":\"Painting\"}");
		byte[] front = TestImages.png(600, 300);
		String first = addPhoto(id, front);
		String second = addPhoto(id, TestImages.jpeg(50, 50));
		String third = addPhoto(id, TestImages.png(20, 20));

		mvc.perform(get("/api/inventory/{id}", id))
			.andExpect(jsonPath("$.pictures[*].id").value(contains(first, second, third)))
			.andExpect(jsonPath("$.pictures[0].url").value("/api/inventory/" + id + "/pictures/" + first))
			.andExpect(jsonPath("$.pictures[0].thumbnailUrl").value("/api/inventory/" + id + "/pictures/" + first + "/thumbnail"));
		mvc.perform(get("/api/inventory")).andExpect(jsonPath("$[?(@.id == '" + id + "')].pictures[0].id").value(first));

		mvc.perform(get("/api/inventory/{id}/pictures/{pictureId}", id, first))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_PNG))
			.andExpect(content().bytes(front))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")));
		mvc.perform(get("/api/inventory/{id}/pictures/{pictureId}/thumbnail", id, first))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_JPEG))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")));
	}

	@Test
	void reorderMakesAnotherPhotoTheCover() throws Exception {
		String id = create("{\"title\":\"Chair\"}");
		String first = addPhoto(id, TestImages.png(10, 10));
		String second = addPhoto(id, TestImages.png(10, 10));

		mvc.perform(put("/api/inventory/{id}/pictures/order", id).contentType(MediaType.APPLICATION_JSON)
			.content("[\"" + second + "\",\"" + first + "\"]")).andExpect(status().isNoContent());
		mvc.perform(get("/api/inventory/{id}", id)).andExpect(jsonPath("$.pictures[*].id").value(contains(second, first)));
	}

	@Test
	void reorderRejectsAListThatIsNotExactlyThePhotos() throws Exception {
		String id = create("{\"title\":\"Table\"}");
		String first = addPhoto(id, TestImages.png(10, 10));
		String second = addPhoto(id, TestImages.png(10, 10));
		String foreign = addPhoto(create("{\"title\":\"Other\"}"), TestImages.png(10, 10));

		for (String order : new String[] { "[\"" + first + "\"]", "[\"" + first + "\",\"" + first + "\"]",
				"[\"" + first + "\",\"" + second + "\",\"" + foreign + "\"]", "[\"" + first + "\",\"" + foreign + "\"]" }) {
			mvc.perform(put("/api/inventory/{id}/pictures/order", id).contentType(MediaType.APPLICATION_JSON).content(order))
				.andExpect(status().isBadRequest());
		}
		mvc.perform(get("/api/inventory/{id}", id)).andExpect(jsonPath("$.pictures[*].id").value(contains(first, second)));
		mvc.perform(put("/api/inventory/{id}/pictures/order", "00000000-0000-0000-0000-000000000000")
			.contentType(MediaType.APPLICATION_JSON).content("[]")).andExpect(status().isNotFound());
	}

	@Test
	void deletingAPhotoKeepsTheOthersInOrderWithoutGaps() throws Exception {
		String id = create("{\"title\":\"Lamp\"}");
		String first = addPhoto(id, TestImages.png(10, 10));
		String second = addPhoto(id, TestImages.png(10, 10));
		String third = addPhoto(id, TestImages.png(10, 10));
		long filesBefore = countPictureFiles();

		mvc.perform(delete("/api/inventory/{id}/pictures/{pictureId}", id, first)).andExpect(status().isNoContent());
		String fourth = addPhoto(id, TestImages.png(10, 10));

		mvc.perform(get("/api/inventory/{id}/pictures/{pictureId}", id, first)).andExpect(status().isNotFound());
		mvc.perform(get("/api/inventory/{id}", id))
			.andExpect(jsonPath("$.pictures[*].id").value(contains(second, third, fourth)));
		List<Integer> positions = jdbc.sql("SELECT position FROM inventory_picture WHERE room_id = ? ORDER BY position")
			.param(id)
			.query(Integer.class)
			.list();
		assertThat(positions).containsExactly(0, 1, 2);
		assertThat(countPictureFiles()).isEqualTo(filesBefore);
		mvc.perform(delete("/api/inventory/{id}/pictures/{pictureId}", id, first)).andExpect(status().isNotFound());
	}

	@Test
	void photoOfAnotherRoomIsNotFound() throws Exception {
		String mine = create("{\"title\":\"Mine\"}");
		String other = create("{\"title\":\"Other\"}");
		String othersPhoto = addPhoto(other, TestImages.png(10, 10));

		mvc.perform(get("/api/inventory/{id}/pictures/{pictureId}", mine, othersPhoto)).andExpect(status().isNotFound());
		mvc.perform(get("/api/inventory/{id}/pictures/{pictureId}/thumbnail", mine, othersPhoto))
			.andExpect(status().isNotFound());
		mvc.perform(delete("/api/inventory/{id}/pictures/{pictureId}", mine, othersPhoto)).andExpect(status().isNotFound());
		mvc.perform(get("/api/inventory/{id}/pictures/{pictureId}", other, othersPhoto)).andExpect(status().isOk());
	}

	@Test
	void deletingARoomDeletesAllItsPhotoFiles() throws Exception {
		String id = create("{\"title\":\"Mirror\"}");
		addPhoto(id, TestImages.png(10, 10));
		addPhoto(id, TestImages.png(10, 10));
		long filesBefore = countPictureFiles();

		mvc.perform(delete("/api/inventory/{id}", id)).andExpect(status().isNoContent());

		assertThat(countPictureFiles()).isEqualTo(filesBefore - 2);
	}

	@Test
	void rejectsBadPhotos() throws Exception {
		String id = create("{\"title\":\"Vase\"}");

		mvc.perform(post("/api/inventory/{id}/pictures", id).contentType(MediaType.TEXT_PLAIN).content("hi"))
			.andExpect(status().isUnsupportedMediaType());
		mvc.perform(post("/api/inventory/{id}/pictures", id).contentType("image/svg+xml").content("<svg/>"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/inventory/{id}/pictures", id).contentType(MediaType.IMAGE_PNG).content(new byte[100 * 1024 + 1]))
			.andExpect(status().isContentTooLarge());
		mvc.perform(post("/api/inventory/{id}/pictures", "00000000-0000-0000-0000-000000000000").contentType(MediaType.IMAGE_PNG)
			.content(new byte[] { 1 })).andExpect(status().isNotFound());
		mvc.perform(get("/api/inventory/{id}", id)).andExpect(jsonPath("$.pictures").isEmpty());
	}

	@Test
	void inventoryIsSeparateFromCatalog() throws Exception {
		String chair = create("{\"title\":\"Chair\"}");
		String photo = addPhoto(chair, TestImages.png(20, 20));

		// Not in the other list, and its photo is not served there
		mvc.perform(get("/api/catalog")).andExpect(jsonPath("$[?(@.id == '" + chair + "')]").doesNotExist());
		mvc.perform(get("/api/catalog/{id}", chair)).andExpect(status().isNotFound());
		mvc.perform(get("/api/catalog/{id}/pictures/{pictureId}", chair, photo)).andExpect(status().isNotFound());
		mvc.perform(get("/api/inventory/{id}/pictures/{pictureId}", chair, photo))
			.andExpect(status().isOk());
	}

	private String addPhoto(String roomId, byte[] image) throws Exception {
		String body = mvc.perform(post("/api/inventory/{id}/pictures", roomId).contentType(MediaType.IMAGE_PNG).content(image))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
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

	private String create(String json) throws Exception {
		String body = mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content(inHouse(json)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

}
