package com.mephys.attic.model;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A document belonging to an heir in the house. {@code validUntil} is {@code null} for
 * documents that do not expire.
 */
public record HeirDocument(UUID id, UUID heirId, DocumentType type, @Nullable LocalDate validUntil,
		@Nullable String comments) {

	public HeirDocument {
		if (heirId == null) {
			throw new IllegalArgumentException("heirId must not be null");
		}
		if (type == null) {
			throw new IllegalArgumentException("type must not be null");
		}
		id = (id != null) ? id : UUID.randomUUID();
		comments = (comments != null && !comments.isBlank()) ? comments.strip() : null;
	}

}
