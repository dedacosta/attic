package com.mephys.attic.security;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Signs out a session whose account was deleted or whose role changed since signing in, so that
 * such changes by an administrator take effect at once rather than when the session ends.
 */
class AccountStillValidFilter extends OncePerRequestFilter {

	private final UserRepository users;

	AccountStillValidFilter(UserRepository users) {
		this.users = users;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken)
				&& !stillValid(authentication)) {
			SecurityContextHolder.clearContext();
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
		}
		chain.doFilter(request, response);
	}

	private boolean stillValid(Authentication authentication) {
		Optional<UserRepository.StoredUser> user = users.find(authentication.getName());
		// Only the roles: Spring Security adds further authorities, e.g. for how one signed in
		Set<String> granted = authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.filter((authority) -> authority.startsWith("ROLE_"))
			.collect(Collectors.toSet());
		return user.isPresent() && granted.equals(Set.copyOf(user.get().role().authorities()));
	}

}
