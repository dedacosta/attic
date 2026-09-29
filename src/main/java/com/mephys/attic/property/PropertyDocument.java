package com.mephys.attic.property;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * An official document of a property, such as its title deed. Its pages are files (images or
 * PDF) stored like photos.
 */
record PropertyDocument(UUID id, UUID propertyId, PropertyDocumentType type, @Nullable LocalDate date,
		@Nullable String notes) {

	PropertyDocument {
		if (propertyId == null) {
			throw new IllegalArgumentException("propertyId must not be null");
		}
		if (type == null) {
			throw new IllegalArgumentException("type must not be null");
		}
		id = (id != null) ? id : UUID.randomUUID();
		notes = (notes != null && !notes.isBlank()) ? notes.strip() : null;
	}

}
