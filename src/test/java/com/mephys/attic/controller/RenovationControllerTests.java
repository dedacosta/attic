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

		String roof = id(create("{\"year\":2026,\"title\":\" Roof \",\"description\":\"New tiles\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("Roof"))
			.andExpect(jsonPath("$.costEur").value(0))
			.andExpect(jsonPath("$.expenses").isEmpty()));
		expenses(roof, "[{\"description\":\"Tiles\",\"kind\":\"MATERIALS\",\"amountEur\":1000}]")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.costEur").value(1000.0))
			.andExpect(jsonPath("$.lines[*].heir").value(containsInAnyOrder("Ana", "Rui", "João")))
			.andExpect(jsonPath("$.lines[?(@.heir == 'Ana')].dueEur").value(contains(250.0)))
			.andExpect(jsonPath("$.lines[?(@.heir == 'João')].dueEur").value(contains(500.0)))
			.andExpect(jsonPath("$.paidEur").value(0))
			.andExpect(jsonPath("$.missingEur").value(1000.0));

		// Every heir alive that year is listed, with a part or without
		create("{\"year\":2025,\"title\":\"Windows\"}")
			.andExpect(jsonPath("$.lines[*].heir").value(containsInAnyOrder("Maria", "Ana", "Rui", "João")))
			.andExpect(jsonPath("$.lines[?(@.heir == 'Ana')].dueEur").value(contains((Object) null)));
	}

	@Test
	void anHeirDeadBeforeTheYearMakesWayForTheirChildrenAndHeirsAreOldestFirst() throws Exception {
		createHeir("{\"name\":\"Carlos\",\"birthDate\":\"1944-02-05\"}");
		createHeir("{\"name\":\"Nobody knows\"}");
		String manuela = createHeir("{\"name\":\"Manuela\",\"birthDate\":\"1954-08-11\",\"deathDate\":\"2023-11-03\"}");
		createHeir("{\"name\":\"Vasco\",\"birthDate\":\"1988-10-18\",\"parentId\":\"" + manuela + "\"}");
		createHeir("{\"name\":\"Sara\",\"birthDate\":\"1978-02-26\",\"parentId\":\"" + manuela + "\"}");
		createHeir("{\"name\":\"Ana\",\"birthDate\":\"1947-08-02\"}");

		roof(300);
		mvc.perform(get("/api/renovations"))
			.andExpect(jsonPath("$[0].lines[*].heir").value(contains("Carlos", "Ana", "Sara", "Vasco", "Nobody knows")))
			.andExpect(jsonPath("$[0].lines[?(@.heir == 'Sara')].dueEur").value(contains(37.5)))
			.andExpect(jsonPath("$[0].lines[?(@.heir == 'Vasco')].dueEur").value(contains(37.5)));

		// In the year of her death she pays, and her children do not yet
		String old = id(create("{\"year\":2023,\"title\":\"Well\"}"));
		expenses(old, "[{\"description\":\"Digging\",\"amountEur\":400}]")
			.andExpect(jsonPath("$.lines[*].heir").value(contains("Carlos", "Ana", "Manuela", "Sara", "Vasco", "Nobody knows")))
			.andExpect(jsonPath("$.lines[?(@.heir == 'Manuela')].dueEur").value(contains(100.0)))
			.andExpect(jsonPath("$.lines[?(@.heir == 'Sara')].dueEur").value(contains((Object) null)));
	}

	@Test
	void paymentsAreTickedOff() throws Exception {
		String joao = createHeir("{\"name\":\"João\"}");
		createHeir("{\"name\":\"Rui\"}");
		String roof = roof(100.10);

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
		String roof = roof(100);
		paid(roof, joao, true);
		mvc.perform(put("/api/renovations/" + roof + "/comment").contentType(MediaType.APPLICATION_JSON)
			.content("{\"comment\":\"Paid in cash\"}")).andExpect(jsonPath("$.comment").value("Paid in cash"));

		mvc.perform(put("/api/renovations/" + roof).contentType(MediaType.APPLICATION_JSON)
			.content("{\"year\":2026,\"title\":\"Roof and gutters\"}"))
			.andExpect(jsonPath("$.title").value("Roof and gutters"))
			.andExpect(jsonPath("$.comment").value("Paid in cash"))
			.andExpect(jsonPath("$.expenses.length()").value(1))
			.andExpect(jsonPath("$.paidEur").value(100.0));

		mvc.perform(delete("/api/renovations/" + roof)).andExpect(status().isNoContent());
		mvc.perform(get("/api/renovations")).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void invalidRenovationsAreRefused() throws Exception {
		create("{\"year\":2026,\"title\":\" \"}").andExpect(status().isBadRequest());
		create("{\"title\":\"Roof\"}").andExpect(status().isBadRequest());
	}

	@Test
	void expensesMakeTheCost() throws Exception {
		createHeir("{\"name\":\"João\"}");
		String roof = id(create("{\"year\":2026,\"title\":\"Roof\"}"));

		expenses(roof, """
				[{"description":" Tiles ","kind":"MATERIALS","amountEur":600,"paid":true},
				 {"description":"Roofer","kind":"WORKMANSHIP","amountEur":300},
				 {"description":"Scaffolding"}]
				""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.costEur").value(900.0))
			.andExpect(jsonPath("$.expenses[*].description").value(contains("Tiles", "Roofer", "Scaffolding")))
			.andExpect(jsonPath("$.expenses[0].paid").value(true))
			.andExpect(jsonPath("$.expenses[1].kind").value("WORKMANSHIP"))
			// An expense gets the defaults of what it leaves out
			.andExpect(jsonPath("$.expenses[2].kind").value("OTHER"))
			.andExpect(jsonPath("$.expenses[2].amountEur").value(0))
			.andExpect(jsonPath("$.expenses[2].paid").value(false))
			.andExpect(jsonPath("$.lines[?(@.heir == 'João')].dueEur").value(contains(900.0)));

		expenses(roof, "[{\"description\":\" \"}]").andExpect(status().isBadRequest());
		expenses(roof, "[{\"description\":\"Tiles\",\"amountEur\":-1}]").andExpect(status().isBadRequest());
		expenses("00000000-0000-4000-8000-000000000000", "[]").andExpect(status().isNotFound());
		mvc.perform(get("/api/renovations")).andExpect(jsonPath("$[?(@.id == '" + roof + "')].costEur").value(contains(900.0)));
	}

	@Test
	void anHeirPaysOrDoesWorkmanship() throws Exception {
		String joao = createHeir("{\"name\":\"João\"}");
		createHeir("{\"name\":\"Rui\"}");
		String roof = roof(100);

		// Workmanship settles the part, as paying does, and either is ticked off on its own
		payment(roof, joao, "{\"workmanship\":true}")
			.andExpect(jsonPath("$.lines[?(@.heir == 'João')].workmanship").value(contains(true)))
			.andExpect(jsonPath("$.lines[?(@.heir == 'João')].paid").value(contains(false)))
			.andExpect(jsonPath("$.paidEur").value(50.0))
			.andExpect(jsonPath("$.missingEur").value(50.0));
		payment(roof, joao, "{\"paid\":true}")
			.andExpect(jsonPath("$.lines[?(@.heir == 'João')].workmanship").value(contains(true)))
			.andExpect(jsonPath("$.lines[?(@.heir == 'João')].paid").value(contains(true)))
			.andExpect(jsonPath("$.paidEur").value(50.0));
		payment(roof, joao, "{\"paid\":false,\"workmanship\":false}").andExpect(jsonPath("$.paidEur").value(0));

		// Someone born after the renovation is not listed, unless ticked off
		String ana = createHeir("{\"name\":\"Ana\",\"birthDate\":\"2030-01-01\"}");
		mvc.perform(get("/api/renovations")).andExpect(jsonPath("$[0].lines[*].heir").value(containsInAnyOrder("João", "Rui")));
		payment(roof, ana, "{\"workmanship\":true}")
			.andExpect(jsonPath("$.lines[*].heir").value(containsInAnyOrder("João", "Rui", "Ana")));
	}

	/** A roof renovation in 2026, with one expense of the cost */
	private String roof(double cost) throws Exception {
		String id = id(create("{\"year\":2026,\"title\":\"Roof\"}"));
		expenses(id, "[{\"description\":\"Tiles\",\"amountEur\":" + cost + "}]").andExpect(status().isOk());
		return id;
	}

	private ResultActions expenses(String renovation, String body) throws Exception {
		return mvc.perform(put("/api/renovations/" + renovation + "/expenses").contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions create(String body) throws Exception {
		return mvc.perform(post("/api/renovations").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions paid(String renovation, String heirId, boolean paid) throws Exception {
		return payment(renovation, heirId, "{\"paid\":" + paid + "}");
	}

	private ResultActions payment(String renovation, String heirId, String body) throws Exception {
		return mvc.perform(put("/api/renovations/" + renovation + "/payments/" + heirId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String createHeir(String body) throws Exception {
		return id(mvc.perform(post("/api/heirs").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated()));
	}

}
