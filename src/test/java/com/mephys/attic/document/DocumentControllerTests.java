package com.mephys.attic.document;

import com.mephys.attic.security.SignedInMockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import com.mephys.attic.picture.TestImages;
import org.jspecify.annotations.Nullable;
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
			.andExpect(jsonPath("$.pictures").isEmpty())
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
	void documentWithoutHeir() throws Exception {
		String david = createHeir("David");
		String body = mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"CONTRACT\",\"comments\":\"House insurance\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.heirId").doesNotExist())
			.andExpect(jsonPath("$.heir").doesNotExist())
			.andExpect(jsonPath("$.type").value("CONTRACT"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(body, "$.id");
		mvc.perform(get("/api/documents/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.heirId").doesNotExist());

		mvc.perform(put("/api/documents/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"heirId\":\"" + david + "\",\"type\":\"CONTRACT\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.heir").value("David"));
		mvc.perform(put("/api/documents/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"heirId\":null,\"type\":\"OTHER\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.heirId").doesNotExist())
			.andExpect(jsonPath("$.type").value("OTHER"));
	}

	@Test
	void listsSortedByHeirThenTypeWithDocumentsWithoutHeirLast() throws Exception {
		String zoe = createHeir("Zoe");
		String ana = createHeir("Ana");
		createDocument(null, "OTHER");
		createDocument(zoe, "PASSPORT");
		createDocument(ana, "PASSPORT");
		createDocument(ana, "DRIVING_LICENCE");

		String body = mvc.perform(get("/api/documents")).andReturn().getResponse().getContentAsString();
		List<String> heirs = JsonPath.read(body, "$[*].heir");
		List<String> types = JsonPath.read(body, "$[?(@.heir == 'Ana')].type");
		assertThat(heirs.getLast()).isNull();
		assertThat(heirs.stream().takeWhile((heir) -> heir != null).toList()).isSorted();
		assertThat(types).containsExactly("DRIVING_LICENCE", "PASSPORT");
	}

	@Test
	void rejectsInvalidDocuments() throws Exception {
		String david = createHeir("David");
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
	void photosLifecycle() throws Exception {
		String id = createDocument(createHeir("David"), "ID_CARD");
		byte[] front = TestImages.withExifOrientation(TestImages.jpeg(640, 400), 6);
		String frontId = addPhoto(id, MediaType.IMAGE_JPEG, front);
		String backId = addPhoto(id, MediaType.IMAGE_PNG, TestImages.png(40, 40));

		mvc.perform(get("/api/documents/{id}", id))
			.andExpect(jsonPath("$.pictures[0].id").value(frontId))
			.andExpect(jsonPath("$.pictures[0].url").value("/api/documents/" + id + "/pictures/" + frontId))
			.andExpect(jsonPath("$.pictures[1].id").value(backId));
		mvc.perform(get("/api/documents/{id}/pictures/{pictureId}", id, frontId))
			.andExpect(status().isOk())
			.andExpect(content().bytes(front));
		mvc.perform(get("/api/documents/{id}/pictures/{pictureId}/thumbnail", id, frontId))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_JPEG));

		mvc.perform(put("/api/documents/{id}/pictures/order", id).contentType(MediaType.APPLICATION_JSON)
			.content("[\"" + backId + "\",\"" + frontId + "\"]")).andExpect(status().isNoContent());
		mvc.perform(get("/api/documents/{id}", id)).andExpect(jsonPath("$.pictures[0].id").value(backId));

		mvc.perform(post("/api/documents/{id}/pictures", id).contentType(MediaType.IMAGE_PNG).content(new byte[100 * 1024 + 1]))
			.andExpect(status().isContentTooLarge());
		mvc.perform(post("/api/documents/{id}/pictures", id).contentType("image/svg+xml").content("<a/>"))
			.andExpect(status().isBadRequest());

		mvc.perform(delete("/api/documents/{id}/pictures/{pictureId}", id, backId)).andExpect(status().isNoContent());
		mvc.perform(get("/api/documents/{id}", id))
			.andExpect(jsonPath("$.pictures.length()").value(1))
			.andExpect(jsonPath("$.pictures[0].id").value(frontId));
	}

	@Test
	void deletingDocumentDeletesAllPhotoFiles() throws Exception {
		String id = createDocument(createHeir("David"), "HEALTH_CARD");
		addPhoto(id, MediaType.IMAGE_PNG, TestImages.png(50, 50));
		addPhoto(id, MediaType.IMAGE_PNG, TestImages.png(50, 50));
		long before = countPictureFiles();

		mvc.perform(delete("/api/documents/{id}", id)).andExpect(status().isNoContent());

		assertThat(countPictureFiles()).isEqualTo(before - 2);
	}

	@Test
	void listsDocumentTypes() throws Exception {
		mvc.perform(get("/api/document-types"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0]").value("ID_CARD"))
			.andExpect(jsonPath("$.length()").value(DocumentType.values().length));
	}

	private String addPhoto(String documentId, MediaType type, byte[] image) throws Exception {
		String body = mvc.perform(post("/api/documents/{id}/pictures", documentId).contentType(type).content(image))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
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

	private String createDocument(@Nullable String heirId, String type) throws Exception {
		String heir = (heirId != null) ? "\"" + heirId + "\"" : "null";
		String body = mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
			.content("{\"heirId\":" + heir + ",\"type\":\"" + type + "\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

}
