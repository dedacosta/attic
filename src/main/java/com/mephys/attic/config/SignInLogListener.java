package com.mephys.attic.config;

import com.mephys.attic.repository.UserRepository;
import com.mephys.attic.service.SignInLog;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.stereotype.Component;

/**
 * Writes every sign-in, every failed attempt and every sign-out to the {@link SignInLog}.
 */
@Component
class SignInLogListener {

	private final SignInLog log;

	private final UserRepository users;

	SignInLogListener(SignInLog log, UserRepository users) {
		this.log = log;
		this.users = users;
	}

	/** Signed in on the sign-in screen, or again by the cookie of "stay signed in" */
	@EventListener
	void signedIn(InteractiveAuthenticationSuccessEvent event) {
		boolean automatic = RememberMeAuthenticationFilter.class.isAssignableFrom(event.getGeneratedBy());
		log.record(automatic ? SignInLog.Event.SIGNED_IN_AUTOMATICALLY : SignInLog.Event.SIGNED_IN,
				event.getAuthentication().getName());
	}

	@EventListener
	void failed(AbstractAuthenticationFailureEvent event) {
		String username = event.getAuthentication().getName();
		SignInLog.Event what = SignInLog.Event.SIGN_IN_REFUSED;
		if (event instanceof AuthenticationFailureBadCredentialsEvent) {
			// The answer to the browser is the same for both; the log tells them apart
			what = users.find(username).isPresent() ? SignInLog.Event.WRONG_PASSWORD : SignInLog.Event.UNKNOWN_USER;
		}
		log.record(what, username);
	}

	@EventListener
	void signedOut(LogoutSuccessEvent event) {
		log.record(SignInLog.Event.SIGNED_OUT, event.getAuthentication().getName());
	}

}
