package com.mephys.attic.security;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RolesTests {

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

	private MockHttpSession admin;

	@BeforeEach
	void administratorSignedIn() throws Exception {
		jdbc.sql("DELETE FROM app_user").update();
		mvc.perform(json(post("/api/setup"), "{\"username\":\"david\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isNoContent());
		admin = signIn("david", PASSWORD);
	}

	@Test
	void firstAccountIsSuperAdministrator() throws Exception {
		mvc.perform(get("/api/session").session(admin)).andExpect(jsonPath("$.role").value("SUPER_ADMIN"));
	}

	@Test
	void administratorManagesOnlyUsersAndDeletesNothing() throws Exception {
		createUser("rui", Role.ADMIN);
		createUser("ana", Role.USER);
		MockHttpSession rui = signIn("rui", PASSWORD);

		mvc.perform(get("/api/users").session(rui)).andExpect(status().isOk());
		mvc.perform(json(post("/api/users").session(rui), newUser("maria", Role.USER))).andExpect(status().isCreated());
		mvc.perform(json(put("/api/users/ana/password").session(rui), "{\"password\":\"reset by rui\"}"))
			.andExpect(status().isNoContent());
		// Editing data is still allowed
		mvc.perform(json(post("/api/items").session(rui), "{\"name\":\"Lamp\"}")).andExpect(status().isCreated());

		mvc.perform(json(post("/api/users").session(rui), newUser("mallory", Role.ADMIN))).andExpect(status().isForbidden());
		mvc.perform(json(post("/api/users").session(rui), newUser("mallory", Role.SUPER_ADMIN)))
			.andExpect(status().isForbidden());
		mvc.perform(json(put("/api/users/david/password").session(rui), "{\"password\":\"taken over\"}"))
			.andExpect(status().isForbidden());
		mvc.perform(json(put("/api/users/ana/role").session(rui), "{\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());
		mvc.perform(json(put("/api/users/rui/role").session(rui), "{\"role\":\"SUPER_ADMIN\"}"))
			.andExpect(status().isForbidden());
		mvc.perform(delete("/api/users/ana").session(rui).with(csrf())).andExpect(status().isForbidden());

		// The super-administrator can
		mvc.perform(json(put("/api/users/rui/password").session(admin), "{\"password\":\"reset by david\"}"))
			.andExpect(status().isNoContent());
		mvc.perform(delete("/api/users/ana").session(admin).with(csrf())).andExpect(status().isNoContent());
	}

	@Test
	void userCanReadButNotChange() throws Exception {
		createUser("ana", Role.USER);
		MockHttpSession user = signIn("ana", PASSWORD);
		mvc.perform(get("/api/session").session(user)).andExpect(jsonPath("$.role").value("USER"));

		for (String path : new String[] { "/api/items", "/api/heirs", "/api/documents", "/api/locations",
				"/api/sexes", "/api/document-types" }) {
			mvc.perform(get(path).session(user)).andExpect(status().isOk());
		}
		mvc.perform(json(post("/api/items").session(user), "{\"name\":\"Lamp\"}")).andExpect(status().isForbidden());
		mvc.perform(json(post("/api/heirs").session(user), "{\"name\":\"Rui\"}")).andExpect(status().isForbidden());
		mvc.perform(json(post("/api/documents").session(user), "{}")).andExpect(status().isForbidden());

		// Changes to existing data are refused too, whatever the item
		String item = "00000000-0000-4000-8000-000000000000";
		mvc.perform(json(put("/api/items/" + item).session(user), "{\"name\":\"X\"}")).andExpect(status().isForbidden());
		mvc.perform(delete("/api/items/" + item).session(user).with(csrf())).andExpect(status().isForbidden());
		mvc.perform(post("/api/items/" + item + "/pictures").session(user).with(csrf())
			.contentType(MediaType.IMAGE_PNG).content(new byte[] { 1 })).andExpect(status().isForbidden());
		String photo = "00000000-0000-4000-8000-000000000001";
		mvc.perform(delete("/api/items/" + item + "/pictures/" + photo).session(user).with(csrf()))
			.andExpect(status().isForbidden());
		mvc.perform(json(put("/api/items/" + item + "/pictures/order").session(user), "[]"))
			.andExpect(status().isForbidden());
		mvc.perform(delete("/api/heirs/" + item).session(user).with(csrf())).andExpect(status().isForbidden());

		// The administrator can
		mvc.perform(json(post("/api/items").session(admin), "{\"name\":\"Lamp\"}")).andExpect(status().isCreated());
	}

	@Test
	void userCannotManageAccountsButCanChangeOwnPassword() throws Exception {
		createUser("ana", Role.USER);
		MockHttpSession user = signIn("ana", PASSWORD);

		mvc.perform(get("/api/users").session(user)).andExpect(status().isForbidden());
		mvc.perform(json(post("/api/users").session(user), newUser("mallory", Role.ADMIN))).andExpect(status().isForbidden());
		mvc.perform(json(put("/api/users/ana/role").session(user), "{\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());

		mvc.perform(json(put("/api/account/password").session(user),
				"{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"another password\"}"))
			.andExpect(status().isNoContent());
	}

	@Test
	void administratorManagesAccounts() throws Exception {
		createUser("ana", Role.USER);
		mvc.perform(json(post("/api/users").session(admin), newUser("ANA", Role.USER))).andExpect(status().isConflict());
		mvc.perform(json(post("/api/users").session(admin), "{\"username\":\"rui\",\"password\":\"short\",\"role\":\"USER\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/users").session(admin), "{\"username\":\"rui\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("role must not be null"));

		mvc.perform(get("/api/users").session(admin))
			.andExpect(jsonPath("$[0].username").value("david"))
			.andExpect(jsonPath("$[0].role").value("SUPER_ADMIN"))
			.andExpect(jsonPath("$[1].username").value("ana"))
			.andExpect(jsonPath("$[1].role").value("USER"))
			.andExpect(jsonPath("$[1].createdAt").exists())
			.andExpect(jsonPath("$[1].passwordHash").doesNotExist());

		mvc.perform(json(put("/api/users/ana/password").session(admin), "{\"password\":\"reset by david\"}"))
			.andExpect(status().isNoContent());
		signIn("ana", "reset by david");

		mvc.perform(delete("/api/users/ana").session(admin).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(delete("/api/users/ana").session(admin).with(csrf())).andExpect(status().isNotFound());
	}

	@Test
	void thereIsAlwaysASuperAdministrator() throws Exception {
		mvc.perform(delete("/api/users/david").session(admin).with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("you cannot delete your own account"));
		mvc.perform(json(put("/api/users/david/role").session(admin), "{\"role\":\"ADMIN\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("there must be at least one super-administrator"));

		createUser("ana", Role.SUPER_ADMIN);
		mvc.perform(json(put("/api/users/david/role").session(admin), "{\"role\":\"ADMIN\"}")).andExpect(status().isOk());
	}

	@Test
	void deletedAccountIsSignedOutAtOnce() throws Exception {
		createUser("ana", Role.USER);
		MockHttpSession user = signIn("ana", PASSWORD);
		mvc.perform(get("/api/items").session(user)).andExpect(status().isOk());

		mvc.perform(delete("/api/users/ana").session(admin).with(csrf())).andExpect(status().isNoContent());

		mvc.perform(get("/api/items").session(user)).andExpect(status().isUnauthorized());
	}

	@Test
	void demotedAdministratorLosesRightsAtOnce() throws Exception {
		createUser("ana", Role.ADMIN);
		MockHttpSession ana = signIn("ana", PASSWORD);
		mvc.perform(json(post("/api/items").session(ana), "{\"name\":\"Lamp\"}")).andExpect(status().isCreated());

		mvc.perform(json(put("/api/users/ana/role").session(admin), "{\"role\":\"USER\"}")).andExpect(status().isOk());

		mvc.perform(json(post("/api/items").session(ana), "{\"name\":\"Lamp\"}")).andExpect(status().isUnauthorized());
		MockHttpSession again = signIn("ana", PASSWORD);
		mvc.perform(json(post("/api/items").session(again), "{\"name\":\"Lamp\"}")).andExpect(status().isForbidden());
	}

	private void createUser(String username, Role role) throws Exception {
		mvc.perform(json(post("/api/users").session(admin), newUser(username, role))).andExpect(status().isCreated());
	}

	private static String newUser(String username, Role role) {
		return "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"" + role + "\"}";
	}

	private MockHttpSession signIn(String username, String password) throws Exception {
		return (MockHttpSession) mvc
			.perform(post("/api/login").with(csrf()).param("username", username).param("password", password))
			.andExpect(status().isNoContent())
			.andReturn()
			.getRequest()
			.getSession();
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
