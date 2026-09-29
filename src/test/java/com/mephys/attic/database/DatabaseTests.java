package com.mephys.attic.database;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DatabaseTests {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("nested/attic.db").toString());
	}

	@Autowired
	private JdbcClient jdbc;

	@Test
	void persistsDataToFile() {
		jdbc.sql("CREATE TABLE note (id INTEGER PRIMARY KEY, text TEXT NOT NULL)").update();
		jdbc.sql("INSERT INTO note (text) VALUES (?)").param("hello").update();

		assertThat(jdbc.sql("SELECT text FROM note").query(String.class).single()).isEqualTo("hello");
		assertThat(tempDir.resolve("nested/attic.db")).exists();
	}

}
