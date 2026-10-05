package com.mephys.attic.controller;

import com.mephys.attic.model.Location;
import com.mephys.attic.support.TestImages;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
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
class CatalogControllerTests {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@Autowired
	private MockMvc mvc;

	@Test
	void createAppliesDefaults() throws Exception {
		mvc.perform(post("/api/catalog").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Lamp\"}"))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", startsWith("http://localhost/api/catalog/")))
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.name").value("Lamp"))
			.andExpect(jsonPath("$.quantity").value(1))
			.andExpect(jsonPath("$.date").doesNotExist())
			.andExpect(jsonPath("$.location").doesNotExist())
			.andExpect(jsonPath("$.existent").value(true))
			.andExpect(jsonPath("$.valueEur").value(0.0))
			.andExpect(jsonPath("$.owner").value("Heritage"))
			.andExpect(jsonPath("$.comments").doesNotExist())
			.andExpect(jsonPath("$.pictures").isEmpty());
	}

	@Test
	void createGetReplaceDelete() throws Exception {
		String id = create("""
				{"name":"Clock","quantity":2,"date":"1985-05-17","location":"ATTIC","valueEur":120.5,"owner":"David",
				 "comments":"Wind every Sunday"}
				""");

		mvc.perform(get("/api/catalog/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.date").value("1985-05-17"))
			.andExpect(jsonPath("$.location").value("ATTIC"))
			.andExpect(jsonPath("$.valueEur").value(120.5))
			.andExpect(jsonPath("$.comments").value("Wind every Sunday"));

		mvc.perform(put("/api/catalog/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Clock\",\"existent\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.existent").value(false))
			.andExpect(jsonPath("$.location").doesNotExist());

		mvc.perform(get("/api/catalog")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + id + "')]").exists());

		mvc.perform(delete("/api/catalog/{id}", id)).andExpect(status().isNoContent());
		mvc.perform(get("/api/catalog/{id}", id)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/catalog/{id}", id)).andExpect(status().isNotFound());
	}

	@Test
	void replaceUnknownItemIsNotFound() throws Exception {
		mvc.perform(put("/api/catalog/{id}", "00000000-0000-0000-0000-000000000000").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"X\"}")).andExpect(status().isNotFound());
	}

	@Test
	void invalidItemIsBadRequest() throws Exception {
		mvc.perform(post("/api/catalog").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("name must not be blank"));
		mvc.perform(post("/api/catalog").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Box\",\"location\":\"MOON\"}")).andExpect(status().isBadRequest());
	}

	@Autowired
	private JdbcClient jdbc;

	@Test
	void itemPictureCannotBeAPdf() throws Exception {
		String id = create("{\"name\":\"Painting\"}");
		mvc.perform(post("/api/catalog/{id}/pictures", id).contentType(MediaType.APPLICATION_PDF).content("%PDF-1.7"))
			.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void photosKeepTheirOrderAndTheFirstIsTheCover() throws Exception {
		String id = create("{\"name\":\"Painting\"}");
		byte[] front = TestImages.png(600, 300);
		String first = addPhoto(id, front);
		String second = addPhoto(id, TestImages.jpeg(50, 50));
		String third = addPhoto(id, TestImages.png(20, 20));

		mvc.perform(get("/api/catalog/{id}", id))
			.andExpect(jsonPath("$.pictures[*].id").value(contains(first, second, third)))
			.andExpect(jsonPath("$.pictures[0].url").value("/api/catalog/" + id + "/pictures/" + first))
			.andExpect(jsonPath("$.pictures[0].thumbnailUrl").value("/api/catalog/" + id + "/pictures/" + first + "/thumbnail"));
		mvc.perform(get("/api/catalog")).andExpect(jsonPath("$[?(@.id == '" + id + "')].pictures[0].id").value(first));

		mvc.perform(get("/api/catalog/{id}/pictures/{pictureId}", id, first))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_PNG))
			.andExpect(content().bytes(front))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")));
		mvc.perform(get("/api/catalog/{id}/pictures/{pictureId}/thumbnail", id, first))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_JPEG))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")));
	}

	@Test
	void reorderMakesAnotherPhotoTheCover() throws Exception {
		String id = create("{\"name\":\"Chair\"}");
		String first = addPhoto(id, TestImages.png(10, 10));
		String second = addPhoto(id, TestImages.png(10, 10));

		mvc.perform(put("/api/catalog/{id}/pictures/order", id).contentType(MediaType.APPLICATION_JSON)
			.content("[\"" + second + "\",\"" + first + "\"]")).andExpect(status().isNoContent());
		mvc.perform(get("/api/catalog/{id}", id)).andExpect(jsonPath("$.pictures[*].id").value(contains(second, first)));
	}

	@Test
	void reorderRejectsAListThatIsNotExactlyThePhotos() throws Exception {
		String id = create("{\"name\":\"Table\"}");
		String first = addPhoto(id, TestImages.png(10, 10));
		String second = addPhoto(id, TestImages.png(10, 10));
		String foreign = addPhoto(create("{\"name\":\"Other\"}"), TestImages.png(10, 10));

		for (String order : new String[] { "[\"" + first + "\"]", "[\"" + first + "\",\"" + first + "\"]",
				"[\"" + first + "\",\"" + second + "\",\"" + foreign + "\"]", "[\"" + first + "\",\"" + foreign + "\"]" }) {
			mvc.perform(put("/api/catalog/{id}/pictures/order", id).contentType(MediaType.APPLICATION_JSON).content(order))
				.andExpect(status().isBadRequest());
		}
		mvc.perform(get("/api/catalog/{id}", id)).andExpect(jsonPath("$.pictures[*].id").value(contains(first, second)));
		mvc.perform(put("/api/catalog/{id}/pictures/order", "00000000-0000-0000-0000-000000000000")
			.contentType(MediaType.APPLICATION_JSON).content("[]")).andExpect(status().isNotFound());
	}

	@Test
	void deletingAPhotoKeepsTheOthersInOrderWithoutGaps() throws Exception {
		String id = create("{\"name\":\"Lamp\"}");
		String first = addPhoto(id, TestImages.png(10, 10));
		String second = addPhoto(id, TestImages.png(10, 10));
		String third = addPhoto(id, TestImages.png(10, 10));
		long filesBefore = countPictureFiles();

		mvc.perform(delete("/api/catalog/{id}/pictures/{pictureId}", id, first)).andExpect(status().isNoContent());
		String fourth = addPhoto(id, TestImages.png(10, 10));

		mvc.perform(get("/api/catalog/{id}/pictures/{pictureId}", id, first)).andExpect(status().isNotFound());
		mvc.perform(get("/api/catalog/{id}", id))
			.andExpect(jsonPath("$.pictures[*].id").value(contains(second, third, fourth)));
		List<Integer> positions = jdbc.sql("SELECT position FROM catalog_picture WHERE item_id = ? ORDER BY position")
			.param(id)
			.query(Integer.class)
			.list();
		assertThat(positions).containsExactly(0, 1, 2);
		assertThat(countPictureFiles()).isEqualTo(filesBefore);
		mvc.perform(delete("/api/catalog/{id}/pictures/{pictureId}", id, first)).andExpect(status().isNotFound());
	}

	@Test
	void photoOfAnotherItemIsNotFound() throws Exception {
		String mine = create("{\"name\":\"Mine\"}");
		String other = create("{\"name\":\"Other\"}");
		String othersPhoto = addPhoto(other, TestImages.png(10, 10));

		mvc.perform(get("/api/catalog/{id}/pictures/{pictureId}", mine, othersPhoto)).andExpect(status().isNotFound());
		mvc.perform(get("/api/catalog/{id}/pictures/{pictureId}/thumbnail", mine, othersPhoto))
			.andExpect(status().isNotFound());
		mvc.perform(delete("/api/catalog/{id}/pictures/{pictureId}", mine, othersPhoto)).andExpect(status().isNotFound());
		mvc.perform(get("/api/catalog/{id}/pictures/{pictureId}", other, othersPhoto)).andExpect(status().isOk());
	}

	@Test
	void deletingAnItemDeletesAllItsPhotoFiles() throws Exception {
		String id = create("{\"name\":\"Mirror\"}");
		addPhoto(id, TestImages.png(10, 10));
		addPhoto(id, TestImages.png(10, 10));
		long filesBefore = countPictureFiles();

		mvc.perform(delete("/api/catalog/{id}", id)).andExpect(status().isNoContent());

		assertThat(countPictureFiles()).isEqualTo(filesBefore - 2);
	}

	@Test
	void rejectsBadPhotos() throws Exception {
		String id = create("{\"name\":\"Vase\"}");

		mvc.perform(post("/api/catalog/{id}/pictures", id).contentType(MediaType.TEXT_PLAIN).content("hi"))
			.andExpect(status().isUnsupportedMediaType());
		mvc.perform(post("/api/catalog/{id}/pictures", id).contentType("image/svg+xml").content("<svg/>"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/catalog/{id}/pictures", id).contentType(MediaType.IMAGE_PNG).content(new byte[100 * 1024 + 1]))
			.andExpect(status().isContentTooLarge());
		mvc.perform(post("/api/catalog/{id}/pictures", "00000000-0000-0000-0000-000000000000").contentType(MediaType.IMAGE_PNG)
			.content(new byte[] { 1 })).andExpect(status().isNotFound());
		mvc.perform(get("/api/catalog/{id}", id)).andExpect(jsonPath("$.pictures").isEmpty());
	}

	@Test
	void listsLocations() throws Exception {
		mvc.perform(get("/api/locations"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0]").value("LIVING_ROOM"))
			.andExpect(jsonPath("$.length()").value(Location.values().length));
	}

	private String addPhoto(String itemId, byte[] image) throws Exception {
		String body = mvc.perform(post("/api/catalog/{id}/pictures", itemId).contentType(MediaType.IMAGE_PNG).content(image))
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
		String body = mvc.perform(post("/api/catalog").contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

}
