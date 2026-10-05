package com.mephys.attic.controller;

import com.mephys.attic.dto.InvitationResponse;
import com.mephys.attic.dto.NewInvitation;
import com.mephys.attic.dto.Registration;
import com.mephys.attic.model.Contact;
import com.mephys.attic.model.Role;
import com.mephys.attic.repository.InvitationRepository;
import com.mephys.attic.repository.UserRepository;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invitations to register. An administrator creates one and passes on its token, usually as a
 * link; whoever has the token may create one account with their own username and password, with
 * the role and heir the administrator chose. A token works once and for {@link #VALIDITY}. As for
 * accounts created directly, only super-administrators may invite administrators or link them to
 * an heir.
 * <p>
 * The token is shown only when the invitation is created: the database keeps its SHA-256 hash.
 */
@RestController
class InvitationController {

	static final Duration VALIDITY = Duration.ofDays(7);

	private static final SecureRandom RANDOM = new SecureRandom();

	private final InvitationRepository invitations;

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	InvitationController(InvitationRepository invitations, UserRepository users, PasswordEncoder passwordEncoder) {
		this.invitations = invitations;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping("/invitations")
	List<InvitationResponse> list() {
		return invitations.findPending(Instant.now())
			.stream()
			.map((invitation) -> InvitationResponse.of(invitation, null))
			.toList();
	}

	@PostMapping("/invitations")
	@Transactional
	ResponseEntity<InvitationResponse> create(@RequestBody NewInvitation request, Principal principal) {
		Role role = UserController.role(request.role());
		if (role != Role.USER) {
			UserController.requireSuperAdmin(users, principal);
		}
		UUID heirId = (request.heirId() != null) ? UserController.validHeir(users, request.heirId(), null) : null;
		Instant now = Instant.now();
		invitations.deleteExpired(now);
		String token = newToken();
		InvitationRepository.StoredInvitation invitation = new InvitationRepository.StoredInvitation(UUID.randomUUID(),
				role, heirId, principal.getName(), now, now.plus(VALIDITY));
		invitations.create(invitation, hash(token));
		return ResponseEntity.created(URI.create("/api/invitations/" + invitation.id()))
			.body(InvitationResponse.of(invitation, token));
	}

	@DeleteMapping("/invitations/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
		InvitationRepository.StoredInvitation invitation = invitations.find(id).orElse(null);
		if (invitation == null) {
			return ResponseEntity.notFound().build();
		}
		if (invitation.role() != Role.USER) {
			UserController.requireSuperAdmin(users, principal);
		}
		invitations.delete(id);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Create an account with the token of an invitation, which is used up. Open to everybody.
	 */
	@PostMapping("/register")
	@Transactional
	synchronized ResponseEntity<Void> register(@RequestBody Registration registration) {
		InvitationRepository.StoredInvitation invitation = (registration.token() != null)
				? invitations.findPendingByTokenHash(hash(registration.token()), Instant.now()).orElse(null) : null;
		if (invitation == null) {
			throw new IllegalArgumentException("invitation is not valid");
		}
		String username = AccountController.validUsername(registration.username());
		String password = AccountController.validPassword(registration.password());
		Contact contact = new Contact(registration.email(), registration.phone());
		if (users.find(username).isPresent()) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		try {
			users.create(username, passwordEncoder.encode(password), invitation.role());
		}
		catch (DuplicateKeyException ex) {
			// Created at the same moment by an administrator
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		users.updateContact(username, contact);
		// The heir may have been linked to another account since the invitation was made
		if (invitation.heirId() != null && users.findByHeir(invitation.heirId()).isEmpty()) {
			users.updateHeir(username, invitation.heirId());
		}
		invitations.delete(invitation.id());
		return ResponseEntity.noContent().build();
	}

	private static String newToken() {
		byte[] token = new byte[32];
		RANDOM.nextBytes(token);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
	}

	private static String hash(String token) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
