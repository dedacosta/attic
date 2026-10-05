package com.mephys.attic.controller;

import com.mephys.attic.dto.Credentials;
import com.mephys.attic.dto.OwnAccount;
import com.mephys.attic.dto.PasswordChange;
import com.mephys.attic.dto.SessionResponse;
import com.mephys.attic.model.Contact;
import com.mephys.attic.model.Role;
import com.mephys.attic.repository.UserRepository;

import java.security.Principal;

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AccountController {

	static final int MIN_PASSWORD_LENGTH = 8;

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	AccountController(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Who is signed in, and whether the first account still has to be created. Also hands the
	 * page a fresh CSRF token cookie.
	 */
	@GetMapping("/session")
	SessionResponse session(@Nullable Principal principal, CsrfToken csrfToken) {
		csrfToken.getToken();
		UserRepository.StoredUser user = (principal != null) ? users.find(principal.getName()).orElse(null) : null;
		return new SessionResponse(user != null, (user != null) ? user.username() : null,
				(user != null) ? user.role() : null, (user != null) ? user.heirId() : null, users.isEmpty());
	}

	/**
	 * Create the first account, the super-administrator. Only possible while there is none.
	 */
	@PostMapping("/setup")
	@Transactional
	synchronized ResponseEntity<Void> setup(@RequestBody Credentials credentials) {
		if (!users.isEmpty()) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		String username = validUsername(credentials.username());
		users.create(username, passwordEncoder.encode(validPassword(credentials.password())), Role.SUPER_ADMIN);
		return ResponseEntity.noContent().build();
	}

	/** The signed-in account's own details */
	@GetMapping("/account")
	OwnAccount account(Principal principal) {
		UserRepository.StoredUser user = users.find(principal.getName()).orElseThrow();
		return new OwnAccount(user.username(), user.role(), user.contact().email(), user.contact().phone());
	}

	/** Anybody may change their own email and phone */
	@PutMapping("/account/contact")
	OwnAccount changeContact(Principal principal, @RequestBody Contact contact) {
		UserRepository.StoredUser user = users.find(principal.getName()).orElseThrow();
		users.updateContact(user.username(), contact);
		return account(principal);
	}

	@PutMapping("/account/password")
	ResponseEntity<Void> changePassword(Principal principal, @RequestBody PasswordChange change) {
		UserRepository.StoredUser user = users.find(principal.getName()).orElseThrow();
		if (change.currentPassword() == null
				|| !passwordEncoder.matches(change.currentPassword(), user.passwordHash())) {
			throw new IllegalArgumentException("current password is wrong");
		}
		users.updatePassword(user.username(), passwordEncoder.encode(validPassword(change.newPassword())));
		return ResponseEntity.noContent().build();
	}

	static String validUsername(@Nullable String username) {
		if (username == null || username.isBlank() || username.strip().length() > 50) {
			throw new IllegalArgumentException("username must be 1 to 50 characters");
		}
		return username.strip();
	}

	static String validPassword(@Nullable String password) {
		if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalArgumentException("password must have at least " + MIN_PASSWORD_LENGTH + " characters");
		}
		return password;
	}

}
