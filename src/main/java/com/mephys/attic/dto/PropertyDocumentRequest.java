package com.mephys.attic.dto;

import com.mephys.attic.model.PropertyDocument;
import com.mephys.attic.model.PropertyDocumentType;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Request body for creating or replacing an official document of a property.
 */
public record PropertyDocumentRequest(@Nullable PropertyDocumentType type, @Nullable LocalDate date, @Nullable String notes) {

	public PropertyDocument toDocument(@Nullable UUID id, UUID propertyId) {
		return new PropertyDocument(id, propertyId, type, date, notes);
	}

}
