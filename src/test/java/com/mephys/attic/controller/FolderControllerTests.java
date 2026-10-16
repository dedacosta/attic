package com.mephys.attic.controller;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SignedInMockMvc.class)
class FolderControllerTests {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@Autowired
	private MockMvc mvc;

	private Path folder;

	@BeforeEach
	void empty() throws Exception {
		folder = tempDir.resolve("folder");
		FileSystemUtils.deleteRecursively(folder);
		Files.createDirectories(folder);
	}

	@Test
	void listsFoldersFirstThenFilesByName() throws Exception {
		Files.createDirectories(folder.resolve("Deeds"));
		Files.writeString(folder.resolve("Deeds/1987 purchase.pdf"), "%PDF");
		Files.writeString(folder.resolve("b.txt"), "b");
		Files.writeString(folder.resolve("A.txt"), "aa");
		Files.writeString(folder.resolve(".DS_Store"), "hidden");

		mvc.perform(get("/api/folder"))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$[*].name").value(contains("Deeds", "A.txt", "b.txt")))
			.andExpect(jsonPath("$[0].folder").value(true))
			.andExpect(jsonPath("$[0].children[0].path").value("Deeds/1987 purchase.pdf"))
			.andExpect(jsonPath("$[1].size").value(2));
	}

	@Test
	void showsChangesOnDiskRightAway() throws Exception {
		mvc.perform(get("/api/folder")).andExpect(jsonPath("$", hasSize(0)));
		Files.writeString(folder.resolve("new.txt"), "new");
		mvc.perform(get("/api/folder")).andExpect(jsonPath("$[*].name").value(contains("new.txt")));
	}

	@Test
	void opensSafeFilesAndDownloadsTheRest() throws Exception {
		Files.createDirectories(folder.resolve("Deeds"));
		Files.writeString(folder.resolve("Deeds/deed.pdf"), "%PDF");
		Files.writeString(folder.resolve("page.html"), "<script>alert(1)</script>");

		mvc.perform(get("/api/folder/file").param("path", "Deeds/deed.pdf"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/pdf"))
			.andExpect(header().string("Content-Disposition", containsString("inline")))
			.andExpect(content().string("%PDF"));
		mvc.perform(get("/api/folder/file").param("path", "Deeds/deed.pdf").param("download", "true"))
			.andExpect(header().string("Content-Disposition", containsString("attachment")));
		mvc.perform(get("/api/folder/file").param("path", "page.html"))
			.andExpect(header().string("Content-Type", "application/octet-stream"))
			.andExpect(header().string("Content-Disposition", containsString("attachment")));
	}

	@Test
	void nothingOutsideTheFolderNorHidden() throws Exception {
		Files.writeString(folder.resolve(".secret"), "hidden");
		Files.createDirectories(folder.resolve("Deeds"));

		for (String path : new String[] { "../attic.db", "Deeds/../../attic.db", "/etc/passwd", ".secret", "Deeds",
				"missing.pdf", "" }) {
			mvc.perform(get("/api/folder/file").param("path", path)).andExpect(status().isNotFound());
		}
	}

}
