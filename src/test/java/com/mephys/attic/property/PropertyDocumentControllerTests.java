package com.mephys.attic.property;

import com.mephys.attic.picture.TestImages;
import com.mephys.attic.security.SignedInMockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
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
class PropertyDocumentControllerTests {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@Autowired
	private MockMvc mvc;

	@Test
	void documentLifecycle() throws Exception {
		String land = createLand("Vinha");
		String body = mvc.perform(post("/api/properties/{id}/documents", land).contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"TITLE_DEED\",\"date\":\"1987-03-12\",\"notes\":\" Escritura de compra \"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.propertyId").value(land))
			.andExpect(jsonPath("$.type").value("TITLE_DEED"))
			.andExpect(jsonPath("$.date").value("1987-03-12"))
			.andExpect(jsonPath("$.notes").value("Escritura de compra"))
			.andExpect(jsonPath("$.files").isEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(body, "$.id");
		createDocument(land, "{\"type\":\"TAX_RECORD\",\"date\":\"2024-01-01\"}");
		createDocument(land, "{\"type\":\"PLAN\"}");

		// Newest first, documents without date last
		mvc.perform(get("/api/properties/{id}", land))
			.andExpect(jsonPath("$.documents[*].type").value(contains("TAX_RECORD", "TITLE_DEED", "PLAN")));

		mvc.perform(put("/api/property-documents/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"LAND_REGISTRY\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value("LAND_REGISTRY"))
			.andExpect(jsonPath("$.date").doesNotExist())
			.andExpect(jsonPath("$.notes").doesNotExist());

		mvc.perform(delete("/api/property-documents/{id}", id)).andExpect(status().isNoContent());
		mvc.perform(delete("/api/property-documents/{id}", id)).andExpect(status().isNotFound());
		mvc.perform(get("/api/properties/{id}", land)).andExpect(jsonPath("$.documents.length()").value(2));
	}

	@Test
	void filesAreImagesOrPdf() throws Exception {
		String document = createDocument(createLand("Olival"), "{\"type\":\"PLAN\"}");
		String photo = addFile(document, MediaType.IMAGE_JPEG, TestImages.jpeg(40, 40));
		String pdf = addFile(document, MediaType.APPLICATION_PDF, PropertyControllerTests.PDF);

		mvc.perform(get("/api/property-documents/{id}/pictures/{fileId}", document, pdf))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
			.andExpect(content().bytes(PropertyControllerTests.PDF));
		mvc.perform(get("/api/property-documents/{id}/pictures/{fileId}/thumbnail", document, pdf))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/property-documents/{id}/pictures/{fileId}/thumbnail", document, photo))
			.andExpect(status().isOk());

		String property = mvc.perform(get("/api/property-documents/{id}", document)).andReturn().getResponse()
			.getContentAsString();
		assertThat((String) JsonPath.read(property, "$.files[1].contentType")).isEqualTo("application/pdf");
		assertThat((Object) JsonPath.read(property, "$.files[1].thumbnailUrl")).isNull();

		mvc.perform(put("/api/property-documents/{id}/pictures/order", document).contentType(MediaType.APPLICATION_JSON)
			.content("[\"" + pdf + "\",\"" + photo + "\"]")).andExpect(status().isNoContent());
		mvc.perform(get("/api/property-documents/{id}", document)).andExpect(jsonPath("$.files[0].id").value(pdf));

		mvc.perform(post("/api/property-documents/{id}/pictures", document).contentType("image/svg+xml").content("<svg/>"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/property-documents/{id}/pictures", document).contentType(MediaType.TEXT_PLAIN).content("hi"))
			.andExpect(status().isUnsupportedMediaType());
		mvc.perform(post("/api/property-documents/{id}/pictures", document).contentType(MediaType.APPLICATION_PDF)
			.content(new byte[100 * 1024 + 1])).andExpect(status().isContentTooLarge());
	}

	@Test
	void deletingADocumentDeletesItsFiles() throws Exception {
		String document = createDocument(createLand("Mata"), "{\"type\":\"OTHER\"}");
		addFile(document, MediaType.APPLICATION_PDF, PropertyControllerTests.PDF);
		addFile(document, MediaType.IMAGE_PNG, TestImages.png(10, 10));
		long before = countFiles();

		mvc.perform(delete("/api/property-documents/{id}", document)).andExpect(status().isNoContent());

		assertThat(countFiles()).isEqualTo(before - 2);
	}

	@Test
	void deletingAPropertyDeletesAllItsFilesButNoOthers() throws Exception {
		String land = createLand("Pinhal");
		String other = createLand("Horta");
		mvc.perform(post("/api/properties/{id}/pictures", land).contentType(MediaType.IMAGE_PNG).content(TestImages.png(10, 10)))
			.andExpect(status().isCreated());
		String deed = createDocument(land, "{\"type\":\"TITLE_DEED\"}");
		String registry = createDocument(land, "{\"type\":\"LAND_REGISTRY\"}");
		addFile(deed, MediaType.APPLICATION_PDF, PropertyControllerTests.PDF);
		addFile(deed, MediaType.IMAGE_PNG, TestImages.png(10, 10));
		addFile(registry, MediaType.IMAGE_PNG, TestImages.png(10, 10));
		String othersDocument = createDocument(other, "{\"type\":\"PLAN\"}");
		String othersFile = addFile(othersDocument, MediaType.APPLICATION_PDF, PropertyControllerTests.PDF);
		long before = countFiles();

		mvc.perform(delete("/api/properties/{id}", land)).andExpect(status().isNoContent());

		assertThat(countFiles()).isEqualTo(before - 4);
		mvc.perform(get("/api/property-documents/{id}", deed)).andExpect(status().isNotFound());
		mvc.perform(get("/api/property-documents/{id}/pictures/{fileId}", othersDocument, othersFile))
			.andExpect(status().isOk());
	}

	@Test
	void rejectsUnknownPropertyOrType() throws Exception {
		mvc.perform(post("/api/properties/{id}/documents", "00000000-0000-0000-0000-000000000000")
			.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"PLAN\"}")).andExpect(status().isNotFound());
		String land = createLand("Eira");
		mvc.perform(post("/api/properties/{id}/documents", land).contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"TREASURE_MAP\"}")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/properties/{id}/documents", land).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
		mvc.perform(put("/api/property-documents/{id}", "00000000-0000-0000-0000-000000000000")
			.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"PLAN\"}")).andExpect(status().isNotFound());
		mvc.perform(post("/api/property-documents/{id}/pictures", "00000000-0000-0000-0000-000000000000")
			.contentType(MediaType.APPLICATION_PDF).content(PropertyControllerTests.PDF)).andExpect(status().isNotFound());
	}

	@Test
	void listsDocumentTypes() throws Exception {
		mvc.perform(get("/api/property-document-types"))
			.andExpect(jsonPath("$[0]").value("TITLE_DEED"))
			.andExpect(jsonPath("$.length()").value(PropertyDocumentType.values().length));
	}

	private String createLand(String name) throws Exception {
		String body = mvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"LAND\",\"name\":\"" + name + "\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String createDocument(String propertyId, String json) throws Exception {
		String body = mvc.perform(post("/api/properties/{id}/documents", propertyId).contentType(MediaType.APPLICATION_JSON)
			.content(json))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String addFile(String documentId, MediaType type, byte[] data) throws Exception {
		String body = mvc.perform(post("/api/property-documents/{id}/pictures", documentId).contentType(type).content(data))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private static long countFiles() throws Exception {
		Path directory = tempDir.resolve("pictures");
		if (!Files.exists(directory)) {
			return 0;
		}
		try (Stream<Path> files = Files.list(directory)) {
			return files.count();
		}
	}

}
