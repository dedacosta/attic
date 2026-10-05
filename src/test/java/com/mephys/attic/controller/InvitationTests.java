package com.mephys.attic.controller;

import java.nio.file.Path;
import java.time.Instant;

import com.jayway.jsonpath.JsonPath;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InvitationTests {

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
		jdbc.sql("DELETE FROM invitation").update();
		jdbc.sql("DELETE FROM app_user").update();
		jdbc.sql("DELETE FROM heir").update();
		mvc.perform(json(post("/api/setup"), "{\"username\":\"david\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isNoContent());
		admin = signIn("david", PASSWORD);
	}

	@Test
	void invitedPersonRegistersWithRoleAndHeirOfInvitation() throws Exception {
		String heir = JsonPath.read(mvc.perform(json(post("/api/heirs").session(admin), "{\"name\":\"Ana\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");
		String token = invite("{\"role\":\"USER\",\"heirId\":\"" + heir + "\"}");

		mvc.perform(json(post("/api/register"), registration(token, "ana").replace("}", ",\"email\":\"ana@example.com\"}")))
			.andExpect(status().isNoContent());

		MockHttpSession ana = signIn("ana", PASSWORD);
		mvc.perform(get("/api/session").session(ana))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.heirId").value(heir));
		mvc.perform(get("/api/account").session(ana)).andExpect(jsonPath("$.email").value("ana@example.com"));
	}

	@Test
	void tokenWorksOnlyOnce() throws Exception {
		String token = invite("{\"role\":\"USER\"}");
		mvc.perform(json(post("/api/register"), registration(token, "ana"))).andExpect(status().isNoContent());
		mvc.perform(json(post("/api/register"), registration(token, "rui")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("invitation is not valid"));
		mvc.perform(get("/api/invitations").session(admin)).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void wrongOrExpiredTokenIsRefused() throws Exception {
		mvc.perform(json(post("/api/register"), registration("made-up", "ana"))).andExpect(status().isBadRequest());

		String token = invite("{\"role\":\"USER\"}");
		jdbc.sql("UPDATE invitation SET expires_at = ?").param(Instant.now().minusSeconds(1).toString()).update();
		mvc.perform(json(post("/api/register"), registration(token, "ana"))).andExpect(status().isBadRequest());
		mvc.perform(get("/api/invitations").session(admin)).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void takenUsernameKeepsTheInvitation() throws Exception {
		String token = invite("{\"role\":\"USER\"}");
		mvc.perform(json(post("/api/register"), registration(token, "DAVID"))).andExpect(status().isConflict());
		mvc.perform(json(post("/api/register"), registration(token, "ana"))).andExpect(status().isNoContent());
	}

	@Test
	void tokenIsShownOnlyOnCreationAndNotStored() throws Exception {
		String token = invite("{\"role\":\"USER\"}");
		mvc.perform(get("/api/invitations").session(admin))
			.andExpect(jsonPath("$[0].role").value("USER"))
			.andExpect(jsonPath("$[0].createdBy").value("david"))
			.andExpect(jsonPath("$[0].expiresAt").exists())
			.andExpect(jsonPath("$[0].token").doesNotExist());
		assertThat(jdbc.sql("SELECT token_hash FROM invitation").query(String.class).single()).isNotEqualTo(token);
	}

	@Test
	void revokedInvitationCannotBeUsed() throws Exception {
		String token = invite("{\"role\":\"USER\"}");
		String id = JsonPath.read(mvc.perform(get("/api/invitations").session(admin)).andReturn().getResponse()
			.getContentAsString(), "$[0].id");
		mvc.perform(delete("/api/invitations/" + id).session(admin).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(json(post("/api/register"), registration(token, "ana"))).andExpect(status().isBadRequest());
	}

	@Test
	void onlySuperAdministratorInvitesAdministrators() throws Exception {
		mvc.perform(json(post("/api/users").session(admin),
				"{\"username\":\"rui\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}"))
			.andExpect(status().isCreated());
		mvc.perform(json(post("/api/users").session(admin),
				"{\"username\":\"ana\",\"password\":\"" + PASSWORD + "\",\"role\":\"USER\"}"))
			.andExpect(status().isCreated());
		MockHttpSession rui = signIn("rui", PASSWORD);
		MockHttpSession ana = signIn("ana", PASSWORD);

		mvc.perform(json(post("/api/invitations").session(rui), "{\"role\":\"USER\"}")).andExpect(status().isCreated());
		mvc.perform(json(post("/api/invitations").session(rui), "{\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());
		mvc.perform(json(post("/api/invitations").session(ana), "{\"role\":\"USER\"}")).andExpect(status().isForbidden());
		mvc.perform(get("/api/invitations").session(ana)).andExpect(status().isForbidden());

		String token = invite("{\"role\":\"ADMIN\"}");
		mvc.perform(json(post("/api/register"), registration(token, "maria"))).andExpect(status().isNoContent());
		mvc.perform(get("/api/session").session(signIn("maria", PASSWORD))).andExpect(jsonPath("$.role").value("ADMIN"));
	}

	private String invite(String body) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/invitations").session(admin), body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.token");
	}

	private static String registration(String token, String username) {
		return "{\"token\":\"" + token + "\",\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
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
