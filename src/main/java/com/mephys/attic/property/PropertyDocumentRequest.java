package com.mephys.attic.property;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Request body for creating or replacing an official document of a property.
 */
record PropertyDocumentRequest(@Nullable PropertyDocumentType type, @Nullable LocalDate date, @Nullable String notes) {

	PropertyDocument toDocument(@Nullable UUID id, UUID propertyId) {
		return new PropertyDocument(id, propertyId, type, date, notes);
	}

}
