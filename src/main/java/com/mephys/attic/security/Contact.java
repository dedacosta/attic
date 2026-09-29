package com.mephys.attic.security;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Optional contact details of an account. Blank values become {@code null}.
 */
record Contact(@Nullable String email, @Nullable String phone) {

	private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

	private static final Pattern PHONE = Pattern.compile("[+0-9 ()\\-]{3,30}");

	Contact {
		email = blankToNull(email);
		phone = blankToNull(phone);
		if (email != null && (email.length() > 254 || !EMAIL.matcher(email).matches())) {
			throw new IllegalArgumentException("email is not valid");
		}
		if (phone != null && (!PHONE.matcher(phone).matches() || phone.chars().filter(Character::isDigit).count() < 3)) {
			throw new IllegalArgumentException("phone is not valid");
		}
	}

	private static @Nullable String blankToNull(@Nullable String text) {
		return (text != null && !text.isBlank()) ? text.strip() : null;
	}

}
