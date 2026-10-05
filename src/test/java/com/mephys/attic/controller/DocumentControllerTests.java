package com.mephys.attic.controller;

import com.mephys.attic.model.DocumentType;
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
class DocumentControllerTests {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@Autowired
	private MockMvc mvc;

	@Test
	void createGetReplaceDelete() throws Exception {
		String david = createHeir("David");
		String body = mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content("""
				{"heirId":"%s","type":"PASSPORT","validUntil":"2031-05-17","comments":"Renew 6 months before"}
				""".formatted(david)))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", startsWith("http://localhost/api/documents/")))
			.andExpect(jsonPath("$.heirId").value(david))
			.andExpect(jsonPath("$.heir").value("David"))
			.andExpect(jsonPath("$.type").value("PASSPORT"))
			.andExpect(jsonPath("$.validUntil").value("2031-05-17"))
			.andExpect(jsonPath("$.comments").value("Renew 6 months before"))
			.andExpect(jsonPath("$.pictureUrl").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(body, "$.id");

		mvc.perform(get("/api/documents/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.type").value("PASSPORT"));

		mvc.perform(put("/api/documents/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"heirId\":\"" + david + "\",\"type\":\"ID_CARD\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.heir").value("David"))
			.andExpect(jsonPath("$.type").value("ID_CARD"))
			.andExpect(jsonPath("$.validUntil").doesNotExist())
			.andExpect(jsonPath("$.comments").doesNotExist());

		mvc.perform(get("/api/documents")).andExpect(jsonPath("$[?(@.id == '" + id + "')].type").value("ID_CARD"));

		mvc.perform(delete("/api/documents/{id}", id)).andExpect(status().isNoContent());
		mvc.perform(get("/api/documents/{id}", id)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/documents/{id}", id)).andExpect(status().isNotFound());
	}

	@Test
	void listsSortedByHeirThenType() throws Exception {
		String zoe = createHeir("Zoe");
		String ana = createHeir("Ana");
		createDocument(zoe, "PASSPORT");
		createDocument(ana, "PASSPORT");
		createDocument(ana, "DRIVING_LICENCE");

		String body = mvc.perform(get("/api/documents")).andReturn().getResponse().getContentAsString();
		List<String> heirs = JsonPath.read(body, "$[*].heir");
		List<String> types = JsonPath.read(body, "$[?(@.heir == 'Ana')].type");
		assertThat(heirs).isSorted();
		assertThat(types).containsExactly("DRIVING_LICENCE", "PASSPORT");
	}

	@Test
	void rejectsInvalidDocuments() throws Exception {
		String david = createHeir("David");
		mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"PASSPORT\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("heirId must not be null"));
		mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
			.content("{\"heirId\":\"00000000-0000-4000-8000-000000000000\",\"type\":\"PASSPORT\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("heir does not exist"));
		mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content("{\"heirId\":\"" + david + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("type must not be null"));
		mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
			.content("{\"heirId\":\"" + david + "\",\"type\":\"TREASURE_MAP\"}")).andExpect(status().isBadRequest());
		mvc.perform(put("/api/documents/{id}", "00000000-0000-0000-0000-000000000000").contentType(MediaType.APPLICATION_JSON)
			.content("{\"heirId\":\"" + david + "\",\"type\":\"PASSPORT\"}")).andExpect(status().isNotFound());
	}

	@Test
	void pictureLifecycle() throws Exception {
		String id = createDocument(createHeir("David"), "ID_CARD");
		byte[] photo = TestImages.withExifOrientation(TestImages.jpeg(640, 400), 6);

		mvc.perform(put("/api/documents/{id}/picture", id).contentType(MediaType.IMAGE_JPEG).content(photo))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/documents/{id}", id))
			.andExpect(jsonPath("$.pictureUrl").value(startsWith("/api/documents/" + id + "/picture?v=")))
			.andExpect(jsonPath("$.thumbnailUrl").value(startsWith("/api/documents/" + id + "/thumbnail?v=")));
		mvc.perform(get("/api/documents/{id}/picture", id)).andExpect(status().isOk()).andExpect(content().bytes(photo));
		mvc.perform(get("/api/documents/{id}/thumbnail", id))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_JPEG));

		mvc.perform(put("/api/documents/{id}/picture", id).contentType(MediaType.IMAGE_PNG).content(new byte[100 * 1024 + 1]))
			.andExpect(status().isContentTooLarge());
		mvc.perform(put("/api/documents/{id}/picture", id).contentType("image/svg+xml").content("<a/>"))
			.andExpect(status().isBadRequest());

		mvc.perform(delete("/api/documents/{id}/picture", id)).andExpect(status().isNoContent());
		mvc.perform(get("/api/documents/{id}/picture", id)).andExpect(status().isNotFound());
		mvc.perform(get("/api/documents/{id}", id)).andExpect(jsonPath("$.pictureUrl").doesNotExist());
	}

	@Test
	void deletingDocumentDeletesPictureFile() throws Exception {
		String id = createDocument(createHeir("David"), "HEALTH_CARD");
		mvc.perform(put("/api/documents/{id}/picture", id).contentType(MediaType.IMAGE_PNG).content(TestImages.png(50, 50)))
			.andExpect(status().isNoContent());
		long before = countPictureFiles();

		mvc.perform(delete("/api/documents/{id}", id)).andExpect(status().isNoContent());

		assertThat(countPictureFiles()).isEqualTo(before - 1);
	}

	@Test
	void listsDocumentTypes() throws Exception {
		mvc.perform(get("/api/document-types"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0]").value("ID_CARD"))
			.andExpect(jsonPath("$.length()").value(DocumentType.values().length));
	}

	private long countPictureFiles() throws Exception {
		try (Stream<Path> files = Files.list(tempDir.resolve("pictures"))) {
			return files.count();
		}
	}

	private String createHeir(String name) throws Exception {
		String body = mvc.perform(post("/api/heirs").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String createDocument(String heirId, String type) throws Exception {
		String body = mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
			.content("{\"heirId\":\"" + heirId + "\",\"type\":\"" + type + "\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

}
