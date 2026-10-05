package com.mephys.attic.model;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A document, usually belonging to an heir in the house. {@code heirId} is {@code null} for
 * documents of nobody in particular, and {@code validUntil} for documents that do not expire.
 */
public record HeirDocument(UUID id, @Nullable UUID heirId, DocumentType type, @Nullable LocalDate validUntil,
		@Nullable String comments) {

	public HeirDocument {
		if (type == null) {
			throw new IllegalArgumentException("type must not be null");
		}
		id = (id != null) ? id : UUID.randomUUID();
		comments = (comments != null && !comments.isBlank()) ? comments.strip() : null;
	}

}
