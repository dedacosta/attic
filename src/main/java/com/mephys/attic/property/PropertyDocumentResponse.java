package com.mephys.attic.property;

import com.mephys.attic.picture.PictureInfo;
import com.mephys.attic.picture.PictureResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for an official document, with its files in order.
 */
record PropertyDocumentResponse(UUID id, UUID propertyId, PropertyDocumentType type, @Nullable LocalDate date,
		@Nullable String notes, List<PictureResponse> files) {

	static PropertyDocumentResponse of(PropertyDocument document, List<PictureInfo> files) {
		return new PropertyDocumentResponse(document.id(), document.propertyId(), document.type(), document.date(),
				document.notes(), PictureResponse.list(base(document.id()), files));
	}

	/** Where the document's files live */
	static String base(UUID documentId) {
		return "/api/property-documents/" + documentId;
	}

}
