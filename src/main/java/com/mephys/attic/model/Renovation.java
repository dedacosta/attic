package com.mephys.attic.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A renovation of the house in a year. Its cost, the total of its {@linkplain RenovationExpense
 * expenses}, is shared by those who pay the contribution that year, see {@link Contributors#shares}.
 */
public record Renovation(UUID id, int year, String title, @Nullable String description, @Nullable String comment) {

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
		id = (id != null) ? id : UUID.randomUUID();
	}

}
