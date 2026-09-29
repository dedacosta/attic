package com.mephys.attic.security;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

/**
 * The page gets its CSRF token as a cookie from the session endpoint. Kept apart from the other
 * security tests: spring-security-test's {@code csrf()} replaces the token repository of the
 * shared application context, after which no cookie would be written.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CsrfCookieTests {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@Autowired
	private MockMvc mvc;

	@Test
	void sessionHandsOutCsrfCookieReadableByThePage() throws Exception {
		mvc.perform(get("/api/session"))
			.andExpect(cookie().exists("XSRF-TOKEN"))
			.andExpect(cookie().httpOnly("XSRF-TOKEN", false))
			.andExpect(cookie().path("XSRF-TOKEN", "/"));
	}

}
