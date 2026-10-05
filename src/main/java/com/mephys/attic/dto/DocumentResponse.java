package com.mephys.attic.dto;

import com.mephys.attic.model.DocumentType;
import com.mephys.attic.model.HeirDocument;
import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.repository.DocumentRepository.NamedDocument;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for a document, with the name of its heir and its files cover first; a file
 * is a photo or a PDF. {@code heirId} and {@code heir} are {@code null} for a document
 * without heir.
 */
public record DocumentResponse(UUID id, @Nullable UUID heirId, @Nullable String heir, DocumentType type,
		@Nullable LocalDate validUntil, @Nullable String comments, List<PictureResponse> pictures) {

	public static DocumentResponse of(NamedDocument named, List<PictureInfo> pictures) {
		HeirDocument document = named.document();
		return new DocumentResponse(document.id(), document.heirId(), named.heirName(), document.type(),
				document.validUntil(), document.comments(), PictureResponse.list(base(document.id()), pictures));
	}

	/** Where the document's files live */
	public static String base(UUID documentId) {
		return "/api/documents/" + documentId;
	}

}
