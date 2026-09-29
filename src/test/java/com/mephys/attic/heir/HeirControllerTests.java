package com.mephys.attic.heir;

import com.mephys.attic.security.SignedInMockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import com.mephys.attic.picture.TestImages;
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
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SignedInMockMvc.class)
class HeirControllerTests {

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

	@Test
	void createGetReplaceDelete() throws Exception {
		String body = mvc.perform(post("/api/heirs").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":" Ana Costa ","birthDate":"1980-02-29","address":"Rua das Flores 12\\n1200-001 Lisboa",
				 "filiation":"João Costa\\nMaria Silva","sex":"FEMALE","heritageShare":" 1 / 3 ",
				 "comments":"Eldest daughter"}
				"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", startsWith("http://localhost/api/heirs/")))
			.andExpect(jsonPath("$.name").value("Ana Costa"))
			.andExpect(jsonPath("$.birthDate").value("1980-02-29"))
			.andExpect(jsonPath("$.address").value("Rua das Flores 12\n1200-001 Lisboa"))
			.andExpect(jsonPath("$.filiation").value("João Costa\nMaria Silva"))
			.andExpect(jsonPath("$.sex").value("FEMALE"))
			.andExpect(jsonPath("$.heritageShare").value("1/3"))
			.andExpect(jsonPath("$.comments").value("Eldest daughter"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(body, "$.id");

		mvc.perform(get("/api/heirs/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.heritageShare").value("1/3"));

		mvc.perform(put("/api/heirs/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Ana Costa\",\"heritageShare\":\"2/6\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.heritageShare").value("2/6"))
			.andExpect(jsonPath("$.sex").doesNotExist())
			.andExpect(jsonPath("$.address").doesNotExist());

		mvc.perform(delete("/api/heirs/{id}", id)).andExpect(status().isNoContent());
		mvc.perform(get("/api/heirs/{id}", id)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/heirs/{id}", id)).andExpect(status().isNotFound());
	}

	@Test
	void acceptsWholeAndEmptyShares() throws Exception {
		mvc.perform(post("/api/heirs").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Only heir\",\"heritageShare\":\"1\"}"))
			.andExpect(jsonPath("$.heritageShare").value("1/1"));
		mvc.perform(post("/api/heirs").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Guest\",\"heritageShare\":\" \"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.heritageShare").doesNotExist());
	}

	@Test
	void rejectsInvalidHeirs() throws Exception {
		mvc.perform(post("/api/heirs").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("name must not be blank"));
		for (String share : new String[] { "3/2", "1/0", "-1/3", "a third", "1/3/4" }) {
			mvc.perform(post("/api/heirs").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"X\",\"heritageShare\":\"" + share + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("heritageShare must be a fraction between 0 and 1, like 1/3"));
		}
		mvc.perform(post("/api/heirs").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"sex\":\"ROBOT\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void deletingHeirKeepsTheirDocumentsWithoutHeir() throws Exception {
		String david = createHeir("David");
		String ana = createHeir("Ana");
		String passport = createDocument(david, "PASSPORT");
		createDocument(david, "ID_CARD");
		String anasCard = createDocument(ana, "HEALTH_CARD");
		mvc.perform(put("/api/documents/{id}/picture", passport).contentType(MediaType.IMAGE_PNG).content(TestImages.png(40, 40)))
			.andExpect(status().isNoContent());
		long picturesBefore = countPictureFiles();

		mvc.perform(delete("/api/heirs/{id}", david)).andExpect(status().isNoContent());

		mvc.perform(get("/api/documents/{id}", passport))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.heirId").doesNotExist())
			.andExpect(jsonPath("$.heir").doesNotExist())
			.andExpect(jsonPath("$.pictureUrl").exists());
		mvc.perform(get("/api/documents/{id}", anasCard)).andExpect(jsonPath("$.heirId").value(ana));
		assertThat(countPictureFiles()).isEqualTo(picturesBefore);
	}

	@Test
	void listsHeirsInTheOrderTheyWereAdded() throws Exception {
		createHeir("Zoe");
		createHeir("Ana");
		createHeir("Maria");

		String body = mvc.perform(get("/api/heirs")).andReturn().getResponse().getContentAsString();
		List<String> names = JsonPath.read(body, "$[*].name");
		List<String> added = JsonPath.read(body, "$[*].createdAt");
		assertThat(names).containsSubsequence("Zoe", "Ana", "Maria");
		// Other tests of this class may add heirs without a timestamp
		assertThat(added.stream().filter(Objects::nonNull).toList()).isSorted();
	}

	@Test
	void heirsFromBeforeTimestampsKeepTheirEntryOrderAndComeFirst() throws Exception {
		createHeir("Added with a timestamp");
		// As stored before timestamps were recorded: no created_at, inserted in this order
		for (String name : new String[] { "Old Zé", "Old Ana", "Old Mia" }) {
			jdbc.sql("INSERT INTO heir (id, name) VALUES (?, ?)").params(UUID.randomUUID().toString(), name).update();
		}

		String body = mvc.perform(get("/api/heirs")).andReturn().getResponse().getContentAsString();
		List<String> names = JsonPath.read(body, "$[*].name");
		assertThat(names).containsSubsequence("Old Zé", "Old Ana", "Old Mia", "Added with a timestamp");
	}

	@Test
	void recordsWhenHeirsAreAddedAndChanged() throws Exception {
		Instant before = Instant.now().minusSeconds(1);
		String body = mvc.perform(post("/api/heirs").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rui\"}"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(body, "$.id");
		Instant created = Instant.parse(JsonPath.read(body, "$.createdAt"));
		assertThat(created).isAfter(before).isBeforeOrEqualTo(Instant.now());
		assertThat((String) JsonPath.read(body, "$.updatedAt")).isEqualTo(JsonPath.read(body, "$.createdAt"));

		Thread.sleep(5);
		String changed = mvc.perform(put("/api/heirs/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Rui Costa\"}")).andReturn().getResponse().getContentAsString();
		assertThat(Instant.parse(JsonPath.read(changed, "$.createdAt"))).isEqualTo(created);
		assertThat(Instant.parse(JsonPath.read(changed, "$.updatedAt"))).isAfter(created);
	}

	@Test
	void listsSexes() throws Exception {
		mvc.perform(get("/api/sexes")).andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0]").value("FEMALE"));
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

	private static long countPictureFiles() throws Exception {
		Path directory = tempDir.resolve("pictures");
		if (!Files.exists(directory)) {
			return 0;
		}
		try (Stream<Path> files = Files.list(directory)) {
			return files.count();
		}
	}

}
