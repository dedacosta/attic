package com.mephys.attic.config;

import com.mephys.attic.model.Role;
import com.mephys.attic.repository.UserRepository;
import com.mephys.attic.service.CurrentAccount;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;

/**
 * Everything below {@code /api} needs a signed-in user, except the session status, the
 * one-time setup and registering with an invitation. Users may only read, apart from their own password and their own heir card
 * (checked by the heir endpoints); changing data and managing accounts is for administrators.
 * Which heirs and documents a user may read is decided by {@link CurrentAccount}. The web page itself is public: it
 * shows the sign-in screen.
 * <p>
 * Sign-in is a form post to {@code /api/login} that answers with a status code instead of a
 * redirect, as the page is a single-page app. CSRF tokens travel in the {@code XSRF-TOKEN}
 * cookie and come back in the {@code X-XSRF-TOKEN} header.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

	static final Duration REMEMBER_ME_VALIDITY = Duration.ofDays(90);

	/** Slows down password guessing */
	private static final Duration FAILED_LOGIN_DELAY = Duration.ofSeconds(1);

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService users, UserRepository repository) {
		String rememberMeKey = repository.setting("remember_me_key", SecurityConfiguration::randomKey);
		http.authorizeHttpRequests((requests) -> requests.requestMatchers("/api/session", "/api/setup", "/api/register")
			.permitAll()
			.requestMatchers("/api/account/**")
			.authenticated()
			.requestMatchers("/api/users/**", "/api/invitations/**")
			.hasRole(Role.ADMIN.name())
			.requestMatchers(HttpMethod.PUT, "/api/heirs/*")
			.authenticated()
			.requestMatchers(HttpMethod.GET, "/api/**")
			.authenticated()
			.requestMatchers("/api/**")
			.hasRole(Role.ADMIN.name())
			.anyRequest()
			.permitAll())
			.addFilterAfter(new AccountStillValidFilter(repository), RememberMeAuthenticationFilter.class)
			.csrf((csrf) -> csrf.spa())
			.formLogin((login) -> login.loginProcessingUrl("/api/login")
				.successHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value()))
				.failureHandler((request, response, exception) -> {
					sleep(FAILED_LOGIN_DELAY);
					response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
				}))
			.rememberMe((rememberMe) -> rememberMe.key(rememberMeKey)
				.userDetailsService(users)
				.rememberMeParameter("remember")
				.tokenValiditySeconds((int) REMEMBER_ME_VALIDITY.toSeconds()))
			.logout((logout) -> logout.logoutUrl("/api/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	UserDetailsService userDetailsService(UserRepository repository) {
		return (username) -> repository.find(username)
			.map((user) -> new User(user.username(), user.passwordHash(),
					user.role().authorities().stream().map(SimpleGrantedAuthority::new).toList()))
			.orElseThrow(() -> new UsernameNotFoundException(username));
	}

	private static String randomKey() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return HexFormat.of().formatHex(key);
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
