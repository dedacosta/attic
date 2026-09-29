package com.mephys.attic.document;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Request body for creating or replacing a document.
 */
record DocumentRequest(@Nullable UUID heirId, DocumentType type, @Nullable LocalDate validUntil, @Nullable String comments) {

	HeirDocument toDocument(@Nullable UUID id) {
		return new HeirDocument(id, heirId, type, validUntil, comments);
	}

}
