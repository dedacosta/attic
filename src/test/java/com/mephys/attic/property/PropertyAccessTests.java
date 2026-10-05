package com.mephys.attic.property;

import com.mephys.attic.support.TestImages;

import java.nio.file.Path;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every signed-in account sees the house and the land; only administrators change them.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PropertyAccessTests {

	private static final String PASSWORD = "correct horse battery";

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void databaseFile(DynamicPropertyRegistry registry) {
		registry.add("attic.database.file", () -> tempDir.resolve("attic.db").toString());
	}

	@Autowired
	private MockMvc mvc;

	@Test
	void userCanReadButNotChange() throws Exception {
		mvc.perform(json(post("/api/setup"), "{\"username\":\"david\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isNoContent());
		MockHttpSession admin = signIn("david");
		String house = id(mvc.perform(json(post("/api/properties").session(admin), "{\"kind\":\"HOUSE\",\"name\":\"Casa\"}")));
		String photo = id(mvc.perform(post("/api/properties/" + house + "/pictures").session(admin).with(csrf())
			.contentType(MediaType.IMAGE_PNG).content(TestImages.png(10, 10))));
		String document = id(mvc.perform(json(post("/api/properties/" + house + "/documents").session(admin),
				"{\"type\":\"TITLE_DEED\"}")));
		String file = id(mvc.perform(post("/api/property-documents/" + document + "/pictures").session(admin).with(csrf())
			.contentType(MediaType.APPLICATION_PDF).content(PropertyControllerTests.PDF)));
		mvc.perform(json(post("/api/users").session(admin),
				"{\"username\":\"ana\",\"password\":\"" + PASSWORD + "\",\"role\":\"USER\"}"))
			.andExpect(status().isCreated());
		MockHttpSession user = signIn("ana");

		for (String path : new String[] { "/api/properties/house", "/api/properties?kind=LAND", "/api/properties/" + house,
				"/api/properties/" + house + "/pictures/" + photo, "/api/property-documents/" + document,
				"/api/property-documents/" + document + "/pictures/" + file, "/api/property-labels",
				"/api/property-document-types" }) {
			mvc.perform(get(path).session(user)).andExpect(status().isOk());
		}

		mvc.perform(json(post("/api/properties").session(user), "{\"kind\":\"LAND\",\"name\":\"X\"}"))
			.andExpect(status().isForbidden());
		mvc.perform(json(put("/api/properties/" + house).session(user), "{\"kind\":\"HOUSE\",\"name\":\"X\"}"))
			.andExpect(status().isForbidden());
		mvc.perform(delete("/api/properties/" + house).session(user).with(csrf())).andExpect(status().isForbidden());
		mvc.perform(post("/api/properties/" + house + "/pictures").session(user).with(csrf())
			.contentType(MediaType.IMAGE_PNG).content(TestImages.png(10, 10))).andExpect(status().isForbidden());
		mvc.perform(delete("/api/properties/" + house + "/pictures/" + photo).session(user).with(csrf()))
			.andExpect(status().isForbidden());
		mvc.perform(json(put("/api/properties/" + house + "/pictures/order").session(user), "[]"))
			.andExpect(status().isForbidden());
		mvc.perform(json(post("/api/properties/" + house + "/documents").session(user), "{\"type\":\"PLAN\"}"))
			.andExpect(status().isForbidden());
		mvc.perform(json(put("/api/property-documents/" + document).session(user), "{\"type\":\"PLAN\"}"))
			.andExpect(status().isForbidden());
		mvc.perform(delete("/api/property-documents/" + document).session(user).with(csrf()))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/property-documents/" + document + "/pictures").session(user).with(csrf())
			.contentType(MediaType.APPLICATION_PDF).content(PropertyControllerTests.PDF)).andExpect(status().isForbidden());
		mvc.perform(delete("/api/property-documents/" + document + "/pictures/" + file).session(user).with(csrf()))
			.andExpect(status().isForbidden());

		// Nothing changed
		mvc.perform(get("/api/property-documents/" + document + "/pictures/" + file).session(admin))
			.andExpect(status().isOk());
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
