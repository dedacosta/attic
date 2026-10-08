package com.mephys.attic.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Everybody who gets into the app, and every attempt, leaves a line in the sign-in log.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SignInLoggingTests {

	private static final String PASSWORD = "correct horse battery";

	private static final String PHONE = "Mozilla/5.0 (Linux; Android 15) Chrome/140";

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
	void noAccountsAndNoLog() throws Exception {
		jdbc.sql("DELETE FROM invitation").update();
		jdbc.sql("DELETE FROM app_user").update();
		Path logs = tempDir.resolve("logs");
		if (Files.isDirectory(logs)) {
			try (Stream<Path> files = Files.list(logs)) {
				for (Path file : files.toList()) {
					Files.delete(file);
				}
			}
		}
	}

	@Test
	void signInsAttemptsAndSignOutsAreLogged() throws Exception {
		mvc.perform(json(post("/api/setup"), "{\"username\":\"david\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isNoContent());
		mvc.perform(login("david", "wrong password", false)).andExpect(status().isUnauthorized());
		mvc.perform(login("nobody", PASSWORD, false)).andExpect(status().isUnauthorized());
		MockHttpSession session = (MockHttpSession) mvc.perform(login("DAVID", PASSWORD, false))
			.andExpect(status().isNoContent())
			.andReturn()
			.getRequest()
			.getSession();
		mvc.perform(post("/api/logout").session(session).with(csrf()).with(from("100.64.0.7")))
			.andExpect(status().isNoContent());

		List<String[]> lines = log();
		assertThat(lines).extracting((line) -> line[1] + " " + line[2])
			.containsExactly("ACCOUNT_CREATED david", "WRONG_PASSWORD david", "UNKNOWN_USER nobody", "SIGNED_IN david",
					"SIGNED_OUT david");
		// Where from and with what
		assertThat(lines).allSatisfy((line) -> {
			assertThat(line).hasSize(5);
			assertThat(line[0]).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}|.*Z");
			assertThat(line[3]).isEqualTo("100.64.0.7");
		});
		assertThat(lines.get(3)[4]).isEqualTo(PHONE);
		// Never the password, right or wrong
		assertThat(Files.readString(logFile())).doesNotContain(PASSWORD).doesNotContain("wrong password");
	}

	@Test
	void signingInByTheRememberedCookieIsLoggedAsAutomatic() throws Exception {
		mvc.perform(json(post("/api/setup"), "{\"username\":\"david\",\"password\":\"" + PASSWORD + "\"}"));
		Cookie rememberMe = mvc.perform(login("david", PASSWORD, true)).andReturn().getResponse().getCookie("remember-me");

		// A new visit, without the old session
		mvc.perform(get("/api/catalog").cookie(rememberMe).with(from("100.64.0.9"))).andExpect(status().isOk());

		assertThat(log()).extracting((line) -> line[1] + " " + line[2] + " " + line[3])
			.containsExactly("ACCOUNT_CREATED david 100.64.0.7", "SIGNED_IN david 100.64.0.7",
					"SIGNED_IN_AUTOMATICALLY david 100.64.0.9");
	}

	@Test
	void registeringWithAnInvitationIsLoggedAndSoIsARefusedOne() throws Exception {
		mvc.perform(json(post("/api/setup"), "{\"username\":\"david\",\"password\":\"" + PASSWORD + "\"}"));
		MockHttpSession admin = (MockHttpSession) mvc.perform(login("david", PASSWORD, false)).andReturn().getRequest().getSession();
		String invitation = mvc.perform(json(post("/api/invitations"), "{\"role\":\"USER\"}").session(admin))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String token = com.jayway.jsonpath.JsonPath.read(invitation, "$.token");

		mvc.perform(json(post("/api/register"),
				"{\"token\":\"not a token\",\"username\":\"mallory\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/register"),
				"{\"token\":\"" + token + "\",\"username\":\"ana\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isNoContent());

		assertThat(log()).extracting((line) -> line[1] + " " + line[2])
			.containsExactly("ACCOUNT_CREATED david", "SIGNED_IN david", "REGISTRATION_REFUSED mallory", "REGISTERED ana");
		assertThat(Files.readString(logFile())).doesNotContain(token).doesNotContain("not a token");
	}

	private static MockHttpServletRequestBuilder login(String username, String password, boolean remember) {
		MockHttpServletRequestBuilder request = post("/api/login").with(csrf())
			.with(from("100.64.0.7"))
			.header("User-Agent", PHONE)
			.param("username", username)
			.param("password", password);
		return remember ? request.param("remember", "true") : request;
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.with(csrf()).with(from("100.64.0.7")).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String address) {
		return (request) -> {
			request.setRemoteAddr(address);
			return request;
		};
	}

	private Path logFile() throws Exception {
		try (Stream<Path> files = Files.list(tempDir.resolve("logs"))) {
			List<Path> all = files.toList();
			assertThat(all).hasSize(1);
			assertThat(all.get(0).getFileName().toString()).matches("sign-ins-\\d{4}-\\d{2}\\.log");
			return all.get(0);
		}
	}

	private List<String[]> log() throws Exception {
		return Files.readAllLines(logFile()).stream().map((line) -> line.split("\t")).toList();
	}

}
