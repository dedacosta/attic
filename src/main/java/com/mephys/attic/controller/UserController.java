package com.mephys.attic.controller;

import com.mephys.attic.dto.HeirLink;
import com.mephys.attic.dto.NewUser;
import com.mephys.attic.dto.PasswordReset;
import com.mephys.attic.dto.RoleChange;
import com.mephys.attic.dto.UserResponse;
import com.mephys.attic.model.Contact;
import com.mephys.attic.model.Role;
import com.mephys.attic.repository.UserRepository;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account management, for administrators (see {@code SecurityConfiguration}). Administrators may
 * create user accounts and reset the passwords of users. Everything else, above all deleting
 * accounts and changing roles, is for super-administrators. There is always at least one
 * super-administrator, and nobody can delete their own account.
 */
@RestController
class UserController {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	UserController(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping("/users")
	List<UserResponse> list() {
		return users.findAll().stream().map(UserResponse::of).toList();
	}

	@PostMapping("/users")
	@Transactional
	synchronized ResponseEntity<UserResponse> create(@RequestBody NewUser request, Principal principal) {
		Role role = role(request.role());
		if (role != Role.USER) {
			requireSuperAdmin(users, principal);
		}
		String username = AccountController.validUsername(request.username());
		if (users.find(username).isPresent()) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		Contact contact = new Contact(request.email(), request.phone());
		users.create(username, passwordEncoder.encode(AccountController.validPassword(request.password())), role);
		users.updateContact(username, contact);
		if (request.heirId() != null) {
			users.updateHeir(username, validHeir(users, request.heirId(), username));
		}
		return ResponseEntity.created(URI.create("/api/users/" + username))
			.body(UserResponse.of(users.find(username).orElseThrow()));
	}

	@PutMapping("/users/{username}/role")
	@Transactional
	synchronized ResponseEntity<UserResponse> changeRole(@PathVariable String username, @RequestBody RoleChange change,
			Principal principal) {
		requireSuperAdmin(users, principal);
		UserRepository.StoredUser user = users.find(username).orElse(null);
		if (user == null) {
			return ResponseEntity.notFound().build();
		}
		Role role = role(change.role());
		if (user.role() == Role.SUPER_ADMIN && role != Role.SUPER_ADMIN && users.count(Role.SUPER_ADMIN) == 1) {
			throw new IllegalArgumentException("there must be at least one super-administrator");
		}
		users.updateRole(user.username(), role);
		return ResponseEntity.ok(UserResponse.of(users.find(username).orElseThrow()));
	}

	/**
	 * Link the account to one of the heirs, or unlink it with a {@code null} heir.
	 */
	@PutMapping("/users/{username}/heir")
	@Transactional
	synchronized ResponseEntity<UserResponse> linkHeir(@PathVariable String username, @RequestBody HeirLink link,
			Principal principal) {
		UserRepository.StoredUser user = users.find(username).orElse(null);
		if (user == null) {
			return ResponseEntity.notFound().build();
		}
		if (user.role() != Role.USER) {
			requireSuperAdmin(users, principal);
		}
		users.updateHeir(user.username(), (link.heirId() != null) ? validHeir(users, link.heirId(), user.username()) : null);
		return ResponseEntity.ok(UserResponse.of(users.find(username).orElseThrow()));
	}

	/**
	 * Change the email and phone of an account. Like passwords: administrators for user accounts,
	 * super-administrators for all.
	 */
	@PutMapping("/users/{username}/contact")
	ResponseEntity<UserResponse> changeContact(@PathVariable String username, @RequestBody Contact contact,
			Principal principal) {
		UserRepository.StoredUser user = users.find(username).orElse(null);
		if (user == null) {
			return ResponseEntity.notFound().build();
		}
		if (user.role() != Role.USER) {
			requireSuperAdmin(users, principal);
		}
		users.updateContact(user.username(), contact);
		return ResponseEntity.ok(UserResponse.of(users.find(username).orElseThrow()));
	}

	@PutMapping("/users/{username}/password")
	ResponseEntity<Void> resetPassword(@PathVariable String username, @RequestBody PasswordReset reset,
			Principal principal) {
		UserRepository.StoredUser user = users.find(username).orElse(null);
		if (user == null) {
			return ResponseEntity.notFound().build();
		}
		if (user.role() != Role.USER) {
			requireSuperAdmin(users, principal);
		}
		users.updatePassword(user.username(),
				passwordEncoder.encode(AccountController.validPassword(reset.password())));
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/users/{username}")
	@Transactional
	synchronized ResponseEntity<Void> delete(@PathVariable String username, Principal principal) {
		requireSuperAdmin(users, principal);
		UserRepository.StoredUser user = users.find(username).orElse(null);
		if (user == null) {
			return ResponseEntity.notFound().build();
		}
		if (user.username().equalsIgnoreCase(principal.getName())) {
			throw new IllegalArgumentException("you cannot delete your own account");
		}
		if (user.role() == Role.SUPER_ADMIN && users.count(Role.SUPER_ADMIN) == 1) {
			throw new IllegalArgumentException("there must be at least one super-administrator");
		}
		users.delete(user.username());
		return ResponseEntity.noContent().build();
	}

	static void requireSuperAdmin(UserRepository users, Principal principal) {
		boolean superAdmin = users.find(principal.getName()).map((user) -> user.role() == Role.SUPER_ADMIN).orElse(false);
		if (!superAdmin) {
			throw new AccessDeniedException("only a super-administrator may do this");
		}
	}

	/**
	 * The heir, if it exists and no account other than {@code username} (if any) is linked to it.
	 */
	static UUID validHeir(UserRepository users, UUID heirId, @Nullable String username) {
		if (!users.heirExists(heirId)) {
			throw new IllegalArgumentException("heir does not exist");
		}
		users.findByHeir(heirId)
			.filter((other) -> !other.username().equalsIgnoreCase(username))
			.ifPresent((other) -> {
				throw new IllegalArgumentException("heir is already linked to another account");
			});
		return heirId;
	}

	static Role role(@Nullable Role role) {
		if (role == null) {
			throw new IllegalArgumentException("role must not be null");
		}
		return role;
	}

}
