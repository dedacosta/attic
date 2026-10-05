package com.mephys.attic.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * An heir in the house. {@code filiation} holds the parents' names, one per line, as on
 * identity cards. {@code createdAt} and {@code updatedAt} are set by the database; they are
 * {@code null} before saving and for heirs added before timestamps were recorded.
 */
public record Heir(UUID id, String name, @Nullable LocalDate birthDate, @Nullable String address,
		@Nullable String filiation, @Nullable Sex sex, @Nullable HeritageShare heritageShare,
		@Nullable String comments, @Nullable Instant createdAt, @Nullable Instant updatedAt) {

	public Heir {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		id = (id != null) ? id : UUID.randomUUID();
		name = name.strip();
		address = blankToNull(address);
		filiation = blankToNull(filiation);
		comments = blankToNull(comments);
	}

	private static @Nullable String blankToNull(@Nullable String text) {
		return (text != null && !text.isBlank()) ? text.strip() : null;
	}

}
