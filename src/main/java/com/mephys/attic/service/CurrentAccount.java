package com.mephys.attic.service;

import com.mephys.attic.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The signed-in account, for the parts of the application whose answers depend on who asks: users
 * see only the heir linked to their account and that heir's documents.
 */
@Component
public class CurrentAccount {

	private final UserRepository users;

	public CurrentAccount(UserRepository users) {
		this.users = users;
	}

	/** Administrators and super-administrators see and edit everything */
	public boolean isAdmin() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch("ROLE_ADMIN"::equals);
	}

	/** The heir linked to the signed-in account, if any */
	public Optional<UUID> heirId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return Optional.empty();
		}
		return users.find(authentication.getName()).map(UserRepository.StoredUser::heirId);
	}

	/** Whether the signed-in account may see this heir and their documents */
	public boolean maySee(UUID heirId) {
		return isAdmin() || heirId().filter(heirId::equals).isPresent();
	}

}
