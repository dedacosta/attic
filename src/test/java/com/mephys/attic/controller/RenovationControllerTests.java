package com.mephys.attic.controller;

import java.nio.file.Path;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SignedInMockMvc.class)
class RenovationControllerTests {

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
	void empty() {
		jdbc.sql("DELETE FROM renovation").update();
		jdbc.sql("DELETE FROM heir").update();
	}

	@Test
	void costIsSharedLikeTheHeritage() throws Exception {
		String maria = createHeir("{\"name\":\"Maria\",\"deathDate\":\"2025-06-30\"}");
		createHeir("{\"name\":\"Ana\",\"parentId\":\"" + maria + "\"}");
		createHeir("{\"name\":\"Rui\",\"parentId\":\"" + maria + "\"}");
		createHeir("{\"name\":\"João\"}");

		create("{\"year\":2026,\"title\":\" Roof \",\"description\":\"New tiles\",\"costEur\":1000}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("Roof"))
			.andExpect(jsonPath("$.lines[*].heir").value(containsInAnyOrder("Ana", "Rui", "João")))
			.andExpect(jsonPath("$.lines[?(@.heir == 'Ana')].dueEur").value(contains(250.0)))
			.andExpect(jsonPath("$.lines[?(@.heir == 'João')].dueEur").value(contains(500.0)))
			.andExpect(jsonPath("$.paidEur").value(0))
			.andExpect(jsonPath("$.missingEur").value(1000.0));

		create("{\"year\":2025,\"title\":\"Windows\",\"costEur\":300}")
			.andExpect(jsonPath("$.lines[*].heir").value(containsInAnyOrder("Maria", "João")));
	}

	@Test
	void paymentsAreTickedOff() throws Exception {
		String joao = createHeir("{\"name\":\"João\"}");
		createHeir("{\"name\":\"Rui\"}");
		String roof = id(create("{\"year\":2026,\"title\":\"Roof\",\"costEur\":100.10}"));

		paid(roof, joao, true)
			.andExpect(jsonPath("$.lines[?(@.heir == 'João')].paid").value(contains(true)))
			.andExpect(jsonPath("$.paidEur").value(50.05))
			.andExpect(jsonPath("$.missingEur").value(50.05));
		paid(roof, joao, false).andExpect(jsonPath("$.paidEur").value(0));
		paid(roof, "00000000-0000-4000-8000-000000000000", true).andExpect(status().isNotFound());
	}

	@Test
	void renovationIsChangedWithoutLosingCommentAndPayments() throws Exception {
		String joao = createHeir("{\"name\":\"João\"}");
		String roof = id(create("{\"year\":2026,\"title\":\"Roof\",\"costEur\":100}"));
		paid(roof, joao, true);
		mvc.perform(put("/api/renovations/" + roof + "/comment").contentType(MediaType.APPLICATION_JSON)
			.content("{\"comment\":\"Paid in cash\"}")).andExpect(jsonPath("$.comment").value("Paid in cash"));

		mvc.perform(put("/api/renovations/" + roof).contentType(MediaType.APPLICATION_JSON)
			.content("{\"year\":2026,\"title\":\"Roof and gutters\",\"costEur\":120}"))
			.andExpect(jsonPath("$.title").value("Roof and gutters"))
			.andExpect(jsonPath("$.comment").value("Paid in cash"))
			.andExpect(jsonPath("$.paidEur").value(120.0));

		mvc.perform(delete("/api/renovations/" + roof)).andExpect(status().isNoContent());
		mvc.perform(get("/api/renovations")).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void invalidRenovationsAreRefused() throws Exception {
		create("{\"year\":2026,\"title\":\" \",\"costEur\":100}").andExpect(status().isBadRequest());
		create("{\"title\":\"Roof\",\"costEur\":100}").andExpect(status().isBadRequest());
		create("{\"year\":2026,\"title\":\"Roof\",\"costEur\":-1}").andExpect(status().isBadRequest());
	}

	private ResultActions create(String body) throws Exception {
		return mvc.perform(post("/api/renovations").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions paid(String renovation, String heirId, boolean paid) throws Exception {
		return mvc.perform(put("/api/renovations/" + renovation + "/payments/" + heirId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"paid\":" + paid + "}"));
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String createHeir(String body) throws Exception {
		return id(mvc.perform(post("/api/heirs").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated()));
	}

}
