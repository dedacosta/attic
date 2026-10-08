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
class ContributionControllerTests {

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
		jdbc.sql("DELETE FROM contribution_year").update();
		jdbc.sql("DELETE FROM heir").update();
	}

	@Test
	void yearListsTheHeirsAliveThatYear() throws Exception {
		createHeir("{\"name\":\"Maria\"}");
		createHeir("{\"name\":\"Ana\",\"birthDate\":\"2025-04-01\"}");
		createHeir("{\"name\":\"Rui\",\"deathDate\":\"2025-06-30\"}");
		createHeir("{\"name\":\"Celina\",\"deathDate\":\"2023-01-15\"}");
		createHeir("{\"name\":\"João\",\"deceased\":true}");
		createYear(2024).andExpect(status().isCreated());
		createYear(2025).andExpect(status().isCreated());

		mvc.perform(get("/api/contributions"))
			.andExpect(jsonPath("$[0].year").value(2025))
			.andExpect(jsonPath("$[0].lines.length()").value(5))
			.andExpect(jsonPath("$[0].lines[?(@.pays == true)].heir").value(containsInAnyOrder("Maria", "Ana", "Rui")))
			.andExpect(jsonPath("$[1].year").value(2024))
			.andExpect(jsonPath("$[1].lines[?(@.pays == true)].heir").value(containsInAnyOrder("Maria", "Rui")));
	}

	@Test
	void childrenPayOnceTheirParentHasDied() throws Exception {
		String maria = createHeir("{\"name\":\"Maria\",\"deathDate\":\"2025-06-30\"}");
		createHeir("{\"name\":\"Ana\",\"parentId\":\"" + maria + "\"}");
		String joao = createHeir("{\"name\":\"João\"}");
		createHeir("{\"name\":\"Rui\",\"parentId\":\"" + joao + "\"}");
		createYear(2025);
		createYear(2026);

		mvc.perform(get("/api/contributions"))
			.andExpect(jsonPath("$[0].lines[?(@.pays == true)].heir").value(containsInAnyOrder("Ana", "João")))
			.andExpect(jsonPath("$[1].lines[?(@.pays == true)].heir").value(containsInAnyOrder("Maria", "João")))
			.andExpect(jsonPath("$[1].lines[?(@.heir == 'Maria')].deceased").value(contains(true)))
			.andExpect(jsonPath("$[0].lines[?(@.heir == 'Ana')].portion").value(contains(1.0)))
			.andExpect(jsonPath("$[0].lines[?(@.heir == 'Rui')].portion").value(contains((Object) null)));
	}

	@Test
	void amountsAreEnteredChangedAndRemoved() throws Exception {
		String maria = createHeir("{\"name\":\"Maria\"}");
		String rui = createHeir("{\"name\":\"Rui\"}");
		createYear(2026).andExpect(jsonPath("$.lines[0].amountEur").doesNotExist()).andExpect(jsonPath("$.totalEur").value(0));

		amount(2026, maria, "{\"amountEur\":200}").andExpect(status().isOk());
		amount(2026, rui, "{\"amountEur\":150.5}")
			.andExpect(jsonPath("$.lines[?(@.heir == 'Rui')].amountEur").value(150.5))
			.andExpect(jsonPath("$.totalEur").value(350.5));

		amount(2026, maria, "{\"amountEur\":null}")
			.andExpect(jsonPath("$.lines[?(@.heir == 'Maria')].amountEur").value(contains((Object) null)))
			.andExpect(jsonPath("$.totalEur").value(150.5));
		amount(2026, rui, "{\"amountEur\":-1}").andExpect(status().isBadRequest());
	}

	@Test
	void amountStaysWhenHeirNoLongerPays() throws Exception {
		String maria = createHeir("{\"name\":\"Maria\"}");
		createYear(2020);
		amount(2020, maria, "{\"amountEur\":100}").andExpect(status().isOk());

		mvc.perform(put("/api/heirs/" + maria).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Maria\",\"birthDate\":\"2022-01-01\"}")).andExpect(status().isOk());

		mvc.perform(get("/api/contributions"))
			.andExpect(jsonPath("$[0].lines[0].heir").value("Maria"))
			.andExpect(jsonPath("$[0].lines[0].pays").value(false))
			.andExpect(jsonPath("$[0].lines[0].amountEur").value(100));
	}

	@Test
	void yearHasAComment() throws Exception {
		createYear(2026).andExpect(jsonPath("$.comment").doesNotExist());
		comment(2026, "{\"comment\":\" Paid by bank transfer\\nRui pays in March \"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.comment").value("Paid by bank transfer\nRui pays in March"));
		mvc.perform(get("/api/contributions")).andExpect(jsonPath("$[0].comment").value("Paid by bank transfer\nRui pays in March"));

		comment(2026, "{\"comment\":\"  \"}").andExpect(jsonPath("$.comment").doesNotExist());
		comment(2026, "{\"comment\":\"" + "x".repeat(1001) + "\"}").andExpect(status().isBadRequest());
	}

	@Test
	void yearsAreUniqueAndCanBeDeleted() throws Exception {
		String maria = createHeir("{\"name\":\"Maria\"}");
		createYear(2026).andExpect(status().isCreated());
		createYear(2026).andExpect(status().isConflict());
		createYear(1800).andExpect(status().isBadRequest());
		amount(2026, "00000000-0000-4000-8000-000000000000", "{\"amountEur\":1}").andExpect(status().isNotFound());

		amount(2026, maria, "{\"amountEur\":1}").andExpect(status().isOk());
		mvc.perform(delete("/api/contributions/2026")).andExpect(status().isNoContent());
		mvc.perform(delete("/api/contributions/2026")).andExpect(status().isNotFound());
		mvc.perform(get("/api/contributions")).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void aRangeHasEveryYearWhetherStoredOrNot() throws Exception {
		String maria = createHeir("{\"name\":\"Maria\",\"birthDate\":\"2000-05-01\"}");
		createYear(2025);
		amount(2025, maria, "{\"amountEur\":200}");

		mvc.perform(get("/api/contributions").param("from", "2024").param("to", "2026"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].year").value(contains(2024, 2025, 2026)))
			.andExpect(jsonPath("$[0].totalEur").value(0))
			.andExpect(jsonPath("$[0].lines[?(@.heir == 'Maria')].pays").value(contains(true)))
			.andExpect(jsonPath("$[0].lines[?(@.heir == 'Maria')].amountEur").value(contains((Object) null)))
			.andExpect(jsonPath("$[1].lines[?(@.heir == 'Maria')].amountEur").value(contains(200.0)));
		// Asking does not store the years
		mvc.perform(get("/api/contributions")).andExpect(jsonPath("$.length()").value(1));

		// Before she was born she does not pay
		mvc.perform(get("/api/contributions").param("from", "1999").param("to", "1999"))
			.andExpect(jsonPath("$[0].lines[?(@.heir == 'Maria')].pays").value(contains(false)));
	}

	@Test
	void aRangeMustBeSensible() throws Exception {
		mvc.perform(get("/api/contributions").param("from", "2026").param("to", "2025")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/contributions").param("from", "1800").param("to", "1805")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/contributions").param("from", "1900").param("to", "2999")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/contributions").param("from", "2026")).andExpect(status().isBadRequest());
	}

	@Test
	void anAmountOrACommentStoresItsYear() throws Exception {
		String maria = createHeir("{\"name\":\"Maria\"}");

		amount(2024, maria, "{\"amountEur\":120}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.year").value(2024))
			.andExpect(jsonPath("$.totalEur").value(120));
		comment(2023, "{\"comment\":\"Nobody paid\"}").andExpect(status().isOk()).andExpect(jsonPath("$.comment").value("Nobody paid"));
		mvc.perform(get("/api/contributions")).andExpect(jsonPath("$[*].year").value(contains(2024, 2023)));

		// Nothing to store: the year is answered but not kept
		amount(2022, maria, "{\"amountEur\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.year").value(2022));
		comment(2021, "{\"comment\":\" \"}").andExpect(status().isOk());
		mvc.perform(get("/api/contributions")).andExpect(jsonPath("$.length()").value(2));

		amount(1800, maria, "{\"amountEur\":1}").andExpect(status().isBadRequest());
		comment(3000, "{\"comment\":\"x\"}").andExpect(status().isBadRequest());
	}

	private ResultActions createYear(int year) throws Exception {
		return mvc.perform(post("/api/contributions").contentType(MediaType.APPLICATION_JSON).content("{\"year\":" + year + "}"));
	}

	private ResultActions comment(int year, String body) throws Exception {
		return mvc.perform(put("/api/contributions/" + year + "/comment").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions amount(int year, String heirId, String body) throws Exception {
		return mvc.perform(put("/api/contributions/" + year + "/" + heirId).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String createHeir(String body) throws Exception {
		return JsonPath.read(mvc.perform(post("/api/heirs").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");
	}

}
