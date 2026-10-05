package com.mephys.attic.dto;

import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.model.PropertyDocument;
import com.mephys.attic.model.PropertyDocumentType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for an official document, with its files in order.
 */
public record PropertyDocumentResponse(UUID id, UUID propertyId, PropertyDocumentType type, @Nullable LocalDate date,
		@Nullable String notes, List<PictureResponse> files) {

	public static PropertyDocumentResponse of(PropertyDocument document, List<PictureInfo> files) {
		return new PropertyDocumentResponse(document.id(), document.propertyId(), document.type(), document.date(),
				document.notes(), PictureResponse.list(base(document.id()), files));
	}

	/** Where the document's files live */
	public static String base(UUID documentId) {
		return "/api/property-documents/" + documentId;
	}

}
