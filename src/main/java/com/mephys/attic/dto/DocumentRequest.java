package com.mephys.attic.dto;

import com.mephys.attic.model.DocumentType;
import com.mephys.attic.model.HeirDocument;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Request body for creating or replacing a document.
 */
public record DocumentRequest(@Nullable UUID heirId, DocumentType type, @Nullable LocalDate validUntil,
		@Nullable String comments) {

	public HeirDocument toDocument(@Nullable UUID id) {
		return new HeirDocument(id, heirId, type, validUntil, comments);
	}

}
