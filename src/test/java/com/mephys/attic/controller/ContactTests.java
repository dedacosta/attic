package com.mephys.attic.controller;

import com.mephys.attic.model.Role;

import java.nio.file.Path;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Accounts have an optional email and phone.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ContactTests {

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

	private MockHttpSession superAdmin;

	@BeforeEach
	void superAdministratorSignedIn() throws Exception {
		jdbc.sql("DELETE FROM app_user").update();
		mvc.perform(json(post("/api/setup"), "{\"username\":\"david\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isNoContent());
		superAdmin = signIn("david");
	}

	@Test
	void accountsHaveEmailAndPhone() throws Exception {
		mvc.perform(json(post("/api/users").session(superAdmin), """
				{"username":"ana","password":"%s","role":"USER","email":" ana@example.pt ","phone":"+351 912 345 678"}
				""".formatted(PASSWORD)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("ana@example.pt"))
			.andExpect(jsonPath("$.phone").value("+351 912 345 678"));

		mvc.perform(json(put("/api/users/ana/contact").session(superAdmin), "{\"email\":\"\",\"phone\":\"(21) 123-4567\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").doesNotExist())
			.andExpect(jsonPath("$.phone").value("(21) 123-4567"));
		mvc.perform(get("/api/users").session(superAdmin)).andExpect(jsonPath("$[1].phone").value("(21) 123-4567"));
	}

	@Test
	void rejectsInvalidContactDetails() throws Exception {
		for (String email : new String[] { "ana", "ana@", "ana@example", "a na@example.pt" }) {
			mvc.perform(json(post("/api/users").session(superAdmin),
					"{\"username\":\"ana\",\"password\":\"" + PASSWORD + "\",\"role\":\"USER\",\"email\":\"" + email + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("email is not valid"));
		}
		for (String phone : new String[] { "call me", "12", "+351 912 345 678 ext. 9" }) {
			mvc.perform(json(put("/api/account/contact").session(superAdmin), "{\"phone\":\"" + phone + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("phone is not valid"));
		}
	}

	@Test
	void everybodyEditsTheirOwnDetails() throws Exception {
		createUser("ana", Role.USER);
		MockHttpSession ana = signIn("ana");

		mvc.perform(json(put("/api/account/contact").session(ana), "{\"email\":\"ana@example.pt\",\"phone\":\"912345678\"}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/account").session(ana))
			.andExpect(jsonPath("$.username").value("ana"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.email").value("ana@example.pt"))
			.andExpect(jsonPath("$.phone").value("912345678"));
		// but not those of others
		mvc.perform(json(put("/api/users/david/contact").session(ana), "{\"email\":\"x@example.pt\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void administratorsEditOnlyUsersDetails() throws Exception {
		createUser("rui", Role.ADMIN);
		createUser("ana", Role.USER);
		MockHttpSession rui = signIn("rui");

		mvc.perform(json(put("/api/users/ana/contact").session(rui), "{\"email\":\"ana@example.pt\"}"))
			.andExpect(status().isOk());
		mvc.perform(json(put("/api/users/david/contact").session(rui), "{\"email\":\"rui@example.pt\"}"))
			.andExpect(status().isForbidden());
	}

	private void createUser(String username, Role role) throws Exception {
		mvc.perform(json(post("/api/users").session(superAdmin),
				"{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"" + role + "\"}"))
			.andExpect(status().isCreated());
	}

	private MockHttpSession signIn(String username) throws Exception {
		return (MockHttpSession) mvc
			.perform(post("/api/login").with(csrf()).param("username", username).param("password", PASSWORD))
			.andExpect(status().isNoContent())
			.andReturn()
			.getRequest()
			.getSession();
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
