package com.mephys.attic.controller;

import com.mephys.attic.model.Location;
import com.mephys.attic.support.TestImages;

import java.nio.file.Path;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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

	@Test
	void createAppliesDefaults() throws Exception {
		mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Lamp\"}"))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", startsWith("http://localhost/api/items/")))
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.name").value("Lamp"))
			.andExpect(jsonPath("$.quantity").value(1))
			.andExpect(jsonPath("$.date").doesNotExist())
			.andExpect(jsonPath("$.location").doesNotExist())
			.andExpect(jsonPath("$.existent").value(true))
			.andExpect(jsonPath("$.valueEur").value(0.0))
			.andExpect(jsonPath("$.owner").value("Heritage"))
			.andExpect(jsonPath("$.comments").doesNotExist())
			.andExpect(jsonPath("$.pictureUrl").doesNotExist())
			.andExpect(jsonPath("$.thumbnailUrl").doesNotExist());
	}

	@Test
	void createGetReplaceDelete() throws Exception {
		String id = create("""
				{"name":"Clock","quantity":2,"date":"1985-05-17","location":"ATTIC","valueEur":120.5,"owner":"David",
				 "comments":"Wind every Sunday"}
				""");

		mvc.perform(get("/api/items/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.date").value("1985-05-17"))
			.andExpect(jsonPath("$.location").value("ATTIC"))
			.andExpect(jsonPath("$.valueEur").value(120.5))
			.andExpect(jsonPath("$.comments").value("Wind every Sunday"));

		mvc.perform(put("/api/items/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Clock\",\"existent\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.existent").value(false))
			.andExpect(jsonPath("$.location").doesNotExist());

		mvc.perform(get("/api/items")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + id + "')]").exists());

		mvc.perform(delete("/api/items/{id}", id)).andExpect(status().isNoContent());
		mvc.perform(get("/api/items/{id}", id)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/items/{id}", id)).andExpect(status().isNotFound());
	}

	@Test
	void replaceUnknownItemIsNotFound() throws Exception {
		mvc.perform(put("/api/items/{id}", "00000000-0000-0000-0000-000000000000").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"X\"}")).andExpect(status().isNotFound());
	}

	@Test
	void invalidItemIsBadRequest() throws Exception {
		mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("name must not be blank"));
		mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Box\",\"location\":\"MOON\"}")).andExpect(status().isBadRequest());
	}

	@Test
	void pictureLifecycle() throws Exception {
		String id = create("{\"name\":\"Painting\"}");
		byte[] png = TestImages.png(600, 300);

		mvc.perform(get("/api/items/{id}/picture", id)).andExpect(status().isNotFound());
		mvc.perform(get("/api/items/{id}/thumbnail", id)).andExpect(status().isNotFound());
		mvc.perform(put("/api/items/{id}/picture", id).contentType(MediaType.IMAGE_PNG).content(png))
			.andExpect(status().isNoContent());

		mvc.perform(get("/api/items/{id}/picture", id))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_PNG))
			.andExpect(content().bytes(png));
		mvc.perform(get("/api/items/{id}/thumbnail", id))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_JPEG));
		mvc.perform(get("/api/items/{id}", id))
			.andExpect(jsonPath("$.pictureUrl").value(startsWith("/api/items/" + id + "/picture?v=")))
			.andExpect(jsonPath("$.thumbnailUrl").value(startsWith("/api/items/" + id + "/thumbnail?v=")));
		String thumbnailUrl = thumbnailUrl(id);
		mvc.perform(get(thumbnailUrl)).andExpect(status().isOk());
		mvc.perform(get("/api/items"))
			.andExpect(jsonPath("$[?(@.id == '" + id + "')].thumbnailUrl").value(thumbnailUrl));

		mvc.perform(put("/api/items/{id}/picture", id).contentType(MediaType.IMAGE_JPEG).content(TestImages.jpeg(50, 50)))
			.andExpect(status().isNoContent());
		assertThat(thumbnailUrl(id)).isNotEqualTo(thumbnailUrl);

		mvc.perform(delete("/api/items/{id}/picture", id)).andExpect(status().isNoContent());
		mvc.perform(get("/api/items/{id}/picture", id)).andExpect(status().isNotFound());
		mvc.perform(get("/api/items/{id}/thumbnail", id)).andExpect(status().isNotFound());
		mvc.perform(get("/api/items/{id}", id)).andExpect(jsonPath("$.pictureUrl").doesNotExist());
	}

	@Test
	void rejectsBadPictures() throws Exception {
		String id = create("{\"name\":\"Vase\"}");

		mvc.perform(put("/api/items/{id}/picture", id).contentType(MediaType.TEXT_PLAIN).content("hi"))
			.andExpect(status().isUnsupportedMediaType());
		mvc.perform(put("/api/items/{id}/picture", id).contentType("image/svg+xml").content("<svg/>"))
			.andExpect(status().isBadRequest());
		mvc.perform(put("/api/items/{id}/picture", id).contentType(MediaType.IMAGE_PNG).content(new byte[100 * 1024 + 1]))
			.andExpect(status().isContentTooLarge());
		mvc.perform(put("/api/items/{id}/picture", "00000000-0000-0000-0000-000000000000").contentType(MediaType.IMAGE_PNG)
			.content(new byte[] { 1 })).andExpect(status().isNotFound());
	}

	@Test
	void listsLocations() throws Exception {
		mvc.perform(get("/api/locations"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0]").value("LIVING_ROOM"))
			.andExpect(jsonPath("$.length()").value(Location.values().length));
	}

	private String thumbnailUrl(String id) throws Exception {
		String body = mvc.perform(get("/api/items/{id}", id)).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.thumbnailUrl");
	}

	private String create(String json) throws Exception {
		String body = mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

}
