package com.mephys.attic.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A renovation of the house in a year. Its cost is shared by those who pay the contribution that
 * year, see {@link Contributors#shares}.
 */
public record Renovation(UUID id, int year, String title, @Nullable String description, BigDecimal costEur,
		@Nullable String comment) {

	public static final int MAX_TITLE_LENGTH = 200;

	public Renovation {
		if (year < 1900 || year > 2999) {
			throw new IllegalArgumentException("year must be between 1900 and 2999");
		}
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("title must not be blank");
		}
		title = title.strip();
		if (title.length() > MAX_TITLE_LENGTH) {
			throw new IllegalArgumentException("title must have at most " + MAX_TITLE_LENGTH + " characters");
		}
		description = (description != null && !description.isBlank()) ? description.strip() : null;
		costEur = (costEur != null) ? costEur.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
		if (costEur.signum() < 0) {
			throw new IllegalArgumentException("costEur must not be negative");
		}
		id = (id != null) ? id : UUID.randomUUID();
	}

}
