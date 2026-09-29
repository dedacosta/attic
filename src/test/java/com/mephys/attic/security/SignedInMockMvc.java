package com.mephys.attic.security;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Makes every MockMvc request of a test come from a signed-in administrator with a valid CSRF
 * token, for tests about the API itself rather than about security.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SignedInMockMvc {

	static final String USERNAME = "tester";

	@Bean
	MockMvcBuilderCustomizer signedInRequests() {
		return (builder) -> builder.defaultRequest(get("/").with(user(USERNAME).roles(Role.ADMIN.name())).with(csrf()));
	}

	/** The account must exist: sessions of deleted accounts are signed out */
	@Bean
	ApplicationRunner testAccount(UserRepository users) {
		return (args) -> {
			if (users.find(USERNAME).isEmpty()) {
				users.create(USERNAME, "{noop}unused", Role.ADMIN);
			}
		};
	}

}
