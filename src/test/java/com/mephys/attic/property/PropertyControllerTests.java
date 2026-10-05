package com.mephys.attic.property;

import com.mephys.attic.support.TestImages;
import com.mephys.attic.controller.SignedInMockMvc;

import java.nio.charset.StandardCharsets;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "attic.picture.max-size=100KB")
@AutoConfigureMockMvc
@Import(SignedInMockMvc.class)
class PropertyControllerTests {

	static final byte[] PDF = "%PDF-1.4\n1 0 obj << >> endobj\ntrailer << >>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void noHouse() {
		jdbc.sql("DELETE FROM property WHERE kind = 'HOUSE'").update();
	}

	@Test
	void houseLifecycle() throws Exception {
		String id = create("""
				{"kind":"HOUSE","name":"Casa de Viseu","address":"Rua Direita 1, Viseu","valueEur":250000,
				 "comments":"Roof redone in 2019","facts":[{"label":"Área (m²)","value":"180"}]}
				""");

		mvc.perform(get("/api/properties/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.kind").value("HOUSE"))
			.andExpect(jsonPath("$.name").value("Casa de Viseu"))
			.andExpect(jsonPath("$.address").value("Rua Direita 1, Viseu"))
			.andExpect(jsonPath("$.valueEur").value(250000.0))
			.andExpect(jsonPath("$.comments").value("Roof redone in 2019"))
			.andExpect(jsonPath("$.facts[0].label").value("Área (m²)"))
			.andExpect(jsonPath("$.facts[0].value").value("180"))
			.andExpect(jsonPath("$.pictures").isEmpty())
			.andExpect(jsonPath("$.documents").isEmpty());

		mvc.perform(put("/api/properties/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
				{"kind":"HOUSE","name":"Casa da família","facts":[{"label":"Divisões","value":"7"}]}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Casa da família"))
			.andExpect(jsonPath("$.address").doesNotExist())
			.andExpect(jsonPath("$.valueEur").doesNotExist())
			.andExpect(jsonPath("$.facts.length()").value(1))
			.andExpect(jsonPath("$.facts[0].label").value("Divisões"));

		mvc.perform(delete("/api/properties/{id}", id)).andExpect(status().isNoContent());
		mvc.perform(get("/api/properties/{id}", id)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/properties/{id}", id)).andExpect(status().isNotFound());
	}

	@Test
	void thereCanBeSeveralHouses() throws Exception {
		String first = create("{\"kind\":\"HOUSE\",\"name\":\"Casa de Viseu\"}");
		String second = create("{\"kind\":\"HOUSE\",\"name\":\"Casa da praia\"}");

		String body = mvc.perform(get("/api/properties").param("kind", "HOUSE"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<String> ids = JsonPath.read(body, "$[*].id");
		List<String> kinds = JsonPath.read(body, "$[*].kind");
		assertThat(ids).containsExactlyInAnyOrder(first, second);
		assertThat(kinds).containsOnly("HOUSE");
	}

	@Test
	void theListWithoutKindHasHousesAndLandByName() throws Exception {
		create("{\"kind\":\"LAND\",\"name\":\"Vinha\"}");
		create("{\"kind\":\"HOUSE\",\"name\":\"Casa\"}");
		create("{\"kind\":\"LAND\",\"name\":\"Olival\"}");

		String body = mvc.perform(get("/api/properties"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<String> names = JsonPath.read(body, "$[*].name");
		assertThat(names).containsSubsequence("Casa", "Olival", "Vinha");
	}

	@Test
	void theSingleHouseAddressIsGone() throws Exception {
		create("{\"kind\":\"HOUSE\",\"name\":\"Casa\"}");

		mvc.perform(get("/api/properties/house")).andExpect(status().is4xxClientError());
	}

	@Test
	void kindCannotChange() throws Exception {
		String id = create("{\"kind\":\"LAND\",\"name\":\"Pinhal\"}");

		mvc.perform(put("/api/properties/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"HOUSE\",\"name\":\"Pinhal\"}")).andExpect(status().isBadRequest());
	}

	@Test
	void landIsListedByNameWithoutTheHouse() throws Exception {
		create("{\"kind\":\"HOUSE\",\"name\":\"A casa\"}");
		create("{\"kind\":\"LAND\",\"name\":\"Vinha\"}");
		create("{\"kind\":\"LAND\",\"name\":\"Olival\"}");
		create("{\"kind\":\"LAND\",\"name\":\"Mata\"}");

		String body = mvc.perform(get("/api/properties").param("kind", "LAND"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<String> names = JsonPath.read(body, "$[*].name");
		List<String> kinds = JsonPath.read(body, "$[*].kind");
		assertThat(names).containsSubsequence("Mata", "Olival", "Vinha").doesNotContain("A casa");
		assertThat(kinds).containsOnly("LAND");
	}

	@Test
	void factsKeepTheirOrderAndBlankLinesAreDropped() throws Exception {
		String id = create("""
				{"kind":"LAND","name":"Quinta","facts":[{"label":" Área (m²) ","value":" 2300 "},{"label":"","value":" "},
				 {"label":"Artigo matricial","value":"1234"}]}
				""");

		mvc.perform(get("/api/properties/{id}", id))
			.andExpect(jsonPath("$.facts[*].label").value(contains("Área (m²)", "Artigo matricial")))
			.andExpect(jsonPath("$.facts[*].value").value(contains("2300", "1234")));

		mvc.perform(put("/api/properties/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"LAND\",\"name\":\"Quinta\",\"facts\":[{\"label\":\"Artigo matricial\",\"value\":\"1234\"},"
					+ "{\"label\":\"Área (m²)\",\"value\":\"2300\"}]}"))
			.andExpect(jsonPath("$.facts[*].label").value(contains("Artigo matricial", "Área (m²)")));
	}

	@Test
	void rejectsInvalidProperties() throws Exception {
		mvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"LAND\",\"name\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("name must not be blank"));
		mvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"LAND\",\"name\":\"X\",\"valueEur\":-1}")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"CASTLE\",\"name\":\"X\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(put("/api/properties/{id}", "00000000-0000-0000-0000-000000000000").contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"LAND\",\"name\":\"X\"}")).andExpect(status().isNotFound());
	}

	@Test
	void photosAreImagesOnlyAndCanBeReordered() throws Exception {
		String id = create("{\"kind\":\"LAND\",\"name\":\"Horta\"}");
		String first = addPhoto(id, TestImages.png(20, 20));
		String second = addPhoto(id, TestImages.png(20, 20));

		mvc.perform(put("/api/properties/{id}/pictures/order", id).contentType(MediaType.APPLICATION_JSON)
			.content("[\"" + second + "\",\"" + first + "\"]")).andExpect(status().isNoContent());
		mvc.perform(get("/api/properties/{id}", id))
			.andExpect(jsonPath("$.pictures[*].id").value(contains(second, first)))
			.andExpect(jsonPath("$.pictures[0].url").value("/api/properties/" + id + "/pictures/" + second))
			.andExpect(jsonPath("$.pictures[0].contentType").value("image/png"));
		mvc.perform(get("/api/properties/{id}/pictures/{pictureId}", id, first)).andExpect(status().isOk());

		mvc.perform(post("/api/properties/{id}/pictures", id).contentType(MediaType.APPLICATION_PDF).content(PDF))
			.andExpect(status().isUnsupportedMediaType());
		mvc.perform(post("/api/properties/{id}/pictures", id).contentType("image/pdf").content(PDF))
			.andExpect(status().isBadRequest());
	}

	@Test
	void deletingAPropertyDeletesItsPhotoFiles() throws Exception {
		String id = create("{\"kind\":\"LAND\",\"name\":\"Eira\"}");
		addPhoto(id, TestImages.png(10, 10));
		addPhoto(id, TestImages.png(10, 10));
		long before = countPictureFiles();

		mvc.perform(delete("/api/properties/{id}", id)).andExpect(status().isNoContent());

		assertThat(countPictureFiles()).isEqualTo(before - 2);
	}

	@Test
	void labelsInUseAreDistinctAndSorted() throws Exception {
		create("{\"kind\":\"LAND\",\"name\":\"L1\",\"facts\":[{\"label\":\"Zona\",\"value\":\"x\"},{\"label\":\"Área (m²)\",\"value\":\"1\"}]}");
		create("{\"kind\":\"LAND\",\"name\":\"L2\",\"facts\":[{\"label\":\"Área (m²)\",\"value\":\"2\"}]}");

		String body = mvc.perform(get("/api/property-labels")).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		List<String> labels = JsonPath.read(body, "$");
		assertThat(labels).containsSubsequence("Área (m²)", "Zona").doesNotHaveDuplicates();
	}

	private String create(String json) throws Exception {
		String body = mvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String addPhoto(String propertyId, byte[] image) throws Exception {
		String body = mvc.perform(post("/api/properties/{id}/pictures", propertyId).contentType(MediaType.IMAGE_PNG).content(image))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	static long countPictureFiles() throws Exception {
		Path directory = tempDir.resolve("pictures");
		if (!Files.exists(directory)) {
			return 0;
		}
		try (Stream<Path> files = Files.list(directory)) {
			return files.count();
		}
	}

}
