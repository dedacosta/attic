package com.mephys.attic.config;

import java.nio.file.Path;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityTests {

	private static final String PASSWORD = "correct horse battery";

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
	void noAccounts() {
		jdbc.sql("DELETE FROM app_user").update();
	}

	@Test
	void apiNeedsSignIn() throws Exception {
		for (String path : new String[] { "/api/items", "/api/official-inventory", "/api/heirs", "/api/documents", "/api/locations" }) {
			mvc.perform(get(path)).andExpect(status().isUnauthorized());
		}
		mvc.perform(post("/api/items").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void webPageIsPublic() throws Exception {
		int status = mvc.perform(get("/")).andReturn().getResponse().getStatus();
		assertThat(status).isNotIn(401, 403);
	}

	@Test
	void setupCreatesTheFirstAccountOnlyOnce() throws Exception {
		mvc.perform(get("/api/session"))
			.andExpect(jsonPath("$.authenticated").value(false))
			.andExpect(jsonPath("$.setupRequired").value(true));

		mvc.perform(setup("david", "short")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("password must have at least 8 characters"));
		mvc.perform(setup(" ", PASSWORD)).andExpect(status().isBadRequest());

		mvc.perform(setup(" david ", PASSWORD)).andExpect(status().isNoContent());
		mvc.perform(get("/api/session")).andExpect(jsonPath("$.setupRequired").value(false));
		mvc.perform(setup("intruder", PASSWORD)).andExpect(status().isConflict());
		assertThat(jdbc.sql("SELECT password_hash FROM app_user").query(String.class).single())
			.startsWith("{bcrypt}")
			.doesNotContain(PASSWORD);
	}

	@Test
	void setupNeedsCsrfToken() throws Exception {
		mvc.perform(post("/api/setup").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"david\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isForbidden());
	}

	@Test
	void signInWithSessionThenSignOut() throws Exception {
		mvc.perform(setup("david", PASSWORD)).andExpect(status().isNoContent());

		mvc.perform(login("david", "wrong password", false)).andExpect(status().isUnauthorized());

		MvcResult login = mvc.perform(login("DAVID", PASSWORD, false)).andExpect(status().isNoContent())
			.andExpect(cookie().doesNotExist("remember-me"))
			.andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession();

		mvc.perform(get("/api/items").session(session)).andExpect(status().isOk());
		mvc.perform(get("/api/session").session(session))
			.andExpect(jsonPath("$.authenticated").value(true))
			.andExpect(jsonPath("$.username").value("david"));

		mvc.perform(post("/api/logout").session(session).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get("/api/items").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void loginNeedsCsrfToken() throws Exception {
		mvc.perform(setup("david", PASSWORD)).andExpect(status().isNoContent());
		mvc.perform(post("/api/login").param("username", "david").param("password", PASSWORD))
			.andExpect(status().isForbidden());
	}

	@Test
	void staySignedInSurvivesTheSession() throws Exception {
		mvc.perform(setup("david", PASSWORD)).andExpect(status().isNoContent());

		Cookie rememberMe = mvc.perform(login("david", PASSWORD, true))
			.andExpect(status().isNoContent())
			.andReturn()
			.getResponse()
			.getCookie("remember-me");
		assertThat(rememberMe).isNotNull();
		assertThat(rememberMe.getMaxAge()).isEqualTo((int) SecurityConfiguration.REMEMBER_ME_VALIDITY.toSeconds());

		// A new visit, without the old session: the cookie alone signs in
		mvc.perform(get("/api/items").cookie(rememberMe)).andExpect(status().isOk());
	}

	@Test
	void changePassword() throws Exception {
		mvc.perform(setup("david", PASSWORD)).andExpect(status().isNoContent());
		MockHttpSession session = (MockHttpSession) mvc.perform(login("david", PASSWORD, false))
			.andReturn()
			.getRequest()
			.getSession();

		mvc.perform(changePassword(session, "not my password", "new password 123"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("current password is wrong"));
		mvc.perform(changePassword(session, PASSWORD, "short")).andExpect(status().isBadRequest());
		mvc.perform(changePassword(session, PASSWORD, "new password 123")).andExpect(status().isNoContent());

		mvc.perform(login("david", PASSWORD, false)).andExpect(status().isUnauthorized());
		mvc.perform(login("david", "new password 123", false)).andExpect(status().isNoContent());
	}

	private static RequestBuilder setup(String username, String password) {
		return post("/api/setup").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
	}

	private static RequestBuilder login(String username, String password,
			boolean remember) {
		var request = post("/api/login").with(csrf()).param("username", username).param("password", password);
		return remember ? request.param("remember", "true") : request;
	}

	private static RequestBuilder changePassword(MockHttpSession session,
			String current, String next) {
		return put("/api/account/password").session(session)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}");
	}

}
