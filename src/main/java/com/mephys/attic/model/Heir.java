package com.mephys.attic.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * An heir in the house. {@code filiation} holds the parents' names, one per line, as on
 * identity cards. {@code parentId} is the heir this one is a child of, if any. An heir who has
 * died is {@code deceased}, with a {@code deathDate} if it is known. Only heirs without a parent
 * have a {@code heritageShare} of their own: children receive theirs through {@link HeritageFlow}.
 * {@code createdAt} and {@code updatedAt} are set by the database; they are {@code null} before
 * saving and for heirs added before timestamps were recorded.
 */
public record Heir(UUID id, String name, @Nullable LocalDate birthDate, boolean deceased,
		@Nullable LocalDate deathDate, @Nullable String address,
		@Nullable String filiation, @Nullable Sex sex, @Nullable HeritageShare heritageShare,
		@Nullable String comments, @Nullable UUID parentId, @Nullable Instant createdAt,
		@Nullable Instant updatedAt) {

	public Heir {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		id = (id != null) ? id : UUID.randomUUID();
		name = name.strip();
		address = blankToNull(address);
		filiation = blankToNull(filiation);
		comments = blankToNull(comments);
		// A date of death means the heir has died
		deceased = deceased || deathDate != null;
		if (deathDate != null && birthDate != null && deathDate.isBefore(birthDate)) {
			throw new IllegalArgumentException("date of death must not be before the date of birth");
		}
		if (parentId != null) {
			heritageShare = null;
		}
		if (parentId != null && parentId.equals(id)) {
			throw new IllegalArgumentException("an heir cannot be their own parent");
		}
	}

	private static @Nullable String blankToNull(@Nullable String text) {
		return (text != null && !text.isBlank()) ? text.strip() : null;
	}

}
