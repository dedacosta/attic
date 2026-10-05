package com.mephys.attic.controller;

import com.mephys.attic.model.Role;
import com.mephys.attic.support.TestImages;

import java.nio.file.Path;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A user sees and edits only the heir linked to their account, and sees only that heir's
 * documents.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OwnHeirTests {

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

	private String celina;

	private String maria;

	private String celinasCard;

	private String mariasPassport;

	private String mariasPhoto;

	@BeforeEach
	void twoHeirsWithDocuments() throws Exception {
		jdbc.sql("DELETE FROM app_user").update();
		jdbc.sql("DELETE FROM heir_document").update();
		jdbc.sql("DELETE FROM heir").update();
		mvc.perform(json(post("/api/setup"), "{\"username\":\"david\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isNoContent());
		admin = signIn("david");
		celina = id(mvc.perform(json(post("/api/heirs").session(admin), "{\"name\":\"Celina\",\"heritageShare\":\"1/9\"}")));
		maria = id(mvc.perform(json(post("/api/heirs").session(admin), "{\"name\":\"Maria\",\"heritageShare\":\"1/9\"}")));
		celinasCard = id(mvc.perform(json(post("/api/documents").session(admin),
				"{\"heirId\":\"" + celina + "\",\"type\":\"ID_CARD\"}")));
		mariasPassport = id(mvc.perform(json(post("/api/documents").session(admin),
				"{\"heirId\":\"" + maria + "\",\"type\":\"PASSPORT\"}")));
		mariasPhoto = id(mvc.perform(post("/api/documents/" + mariasPassport + "/pictures").session(admin).with(csrf())
			.contentType(MediaType.IMAGE_PNG).content(TestImages.png(40, 40))));
		createUser("ana", Role.USER);
	}

	@Test
	void linkedUserSeesOnlyTheirOwnCardAndDocuments() throws Exception {
		String contract = id(mvc.perform(json(post("/api/documents").session(admin), "{\"type\":\"CONTRACT\"}")));
		link("ana", celina).andExpect(status().isOk()).andExpect(jsonPath("$.heirId").value(celina));
		MockHttpSession ana = signIn("ana");
		mvc.perform(get("/api/session").session(ana)).andExpect(jsonPath("$.heirId").value(celina));

		mvc.perform(get("/api/heirs").session(ana))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("Celina"));
		mvc.perform(get("/api/heirs/" + maria).session(ana)).andExpect(status().isNotFound());

		mvc.perform(get("/api/documents").session(ana))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(celinasCard));
		mvc.perform(get("/api/documents/" + mariasPassport).session(ana)).andExpect(status().isNotFound());
		mvc.perform(get("/api/documents/" + mariasPassport + "/pictures/" + mariasPhoto).session(ana))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/documents/" + mariasPassport + "/pictures/" + mariasPhoto + "/thumbnail").session(ana))
			.andExpect(status().isNotFound());
		// Documents without an heir are for administrators only
		mvc.perform(get("/api/documents/" + contract).session(ana)).andExpect(status().isNotFound());
		String contractPhoto = id(mvc.perform(post("/api/documents/" + contract + "/pictures").session(admin).with(csrf())
			.contentType(MediaType.IMAGE_PNG).content(TestImages.png(10, 10))));
		mvc.perform(get("/api/documents/" + contract + "/pictures/" + contractPhoto).session(ana))
			.andExpect(status().isNotFound());

		// The administrator still sees everybody
		mvc.perform(get("/api/heirs").session(admin)).andExpect(jsonPath("$.length()").value(2));
		mvc.perform(get("/api/documents/" + contract).session(admin)).andExpect(status().isOk());
		mvc.perform(get("/api/documents/" + mariasPassport + "/pictures/" + mariasPhoto).session(admin))
			.andExpect(status().isOk());
	}

	@Test
	void linkedUserEditsOwnCardButNotTheShareParentDeathOrOthers() throws Exception {
		link("ana", celina).andExpect(status().isOk());
		MockHttpSession ana = signIn("ana");

		mvc.perform(json(put("/api/heirs/" + celina).session(ana),
				"{\"name\":\"Celina Costa\",\"address\":\"Rua Nova 1\",\"heritageShare\":\"1/1\",\"parentId\":\""
						+ maria + "\",\"deceased\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Celina Costa"))
			.andExpect(jsonPath("$.address").value("Rua Nova 1"))
			.andExpect(jsonPath("$.heritageShare").value("1/9"))
			.andExpect(jsonPath("$.parentId").doesNotExist())
			.andExpect(jsonPath("$.deceased").value(false));

		mvc.perform(json(put("/api/heirs/" + maria).session(ana), "{\"name\":\"Hacked\"}"))
			.andExpect(status().isNotFound());
		mvc.perform(delete("/api/heirs/" + celina).session(ana).with(csrf())).andExpect(status().isForbidden());
		mvc.perform(json(post("/api/heirs").session(ana), "{\"name\":\"New\"}")).andExpect(status().isForbidden());
		mvc.perform(json(post("/api/documents").session(ana), "{\"heirId\":\"" + celina + "\",\"type\":\"PASSPORT\"}"))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/heirs/" + maria).session(admin)).andExpect(jsonPath("$.name").value("Maria"));
	}

	@Test
	void unlinkedUserSeesNoHeirsOrDocuments() throws Exception {
		MockHttpSession ana = signIn("ana");
		mvc.perform(get("/api/session").session(ana)).andExpect(jsonPath("$.heirId").doesNotExist());
		mvc.perform(get("/api/heirs").session(ana)).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/api/documents").session(ana)).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(json(put("/api/heirs/" + celina).session(ana), "{\"name\":\"X\"}")).andExpect(status().isNotFound());
	}

	@Test
	void linkingRules() throws Exception {
		link("ana", "00000000-0000-4000-8000-000000000000").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("heir does not exist"));
		link("ana", celina).andExpect(status().isOk());
		createUser("rui", Role.ADMIN);
		mvc.perform(json(put("/api/users/rui/heir").session(admin), "{\"heirId\":\"" + celina + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("heir is already linked to another account"));

		// An administrator links users, but not other administrators
		MockHttpSession rui = signIn("rui");
		mvc.perform(json(put("/api/users/ana/heir").session(rui), "{\"heirId\":\"" + maria + "\"}"))
			.andExpect(status().isOk());
		mvc.perform(json(put("/api/users/rui/heir").session(rui), "{\"heirId\":\"" + celina + "\"}"))
			.andExpect(status().isForbidden());

		// Unlinking, and deleting the heir, both leave the account without an heir
		mvc.perform(json(put("/api/users/ana/heir").session(admin), "{\"heirId\":null}"))
			.andExpect(jsonPath("$.heirId").doesNotExist());
		link("ana", maria).andExpect(status().isOk());
		mvc.perform(delete("/api/heirs/" + maria).session(admin).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get("/api/users").session(admin)).andExpect(jsonPath("$[?(@.username == 'ana')].heirId").value(
				contains((Object) null)));
	}

	private ResultActions link(String username, String heirId) throws Exception {
		return mvc.perform(json(put("/api/users/" + username + "/heir").session(admin), "{\"heirId\":\"" + heirId + "\"}"));
	}

	private void createUser(String username, Role role) throws Exception {
		mvc.perform(json(post("/api/users").session(admin),
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

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
