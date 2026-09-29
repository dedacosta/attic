package com.mephys.attic.document;

import com.mephys.attic.document.DocumentRepository.NamedDocument;
import com.mephys.attic.picture.PictureInfo;
import com.mephys.attic.picture.PictureResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for a document, with the name of its heir and its photos cover first.
 * {@code heirId} and {@code heir} are {@code null} for a document without heir.
 */
record DocumentResponse(UUID id, @Nullable UUID heirId, @Nullable String heir, DocumentType type,
		@Nullable LocalDate validUntil, @Nullable String comments, List<PictureResponse> pictures) {

	static DocumentResponse of(NamedDocument named, List<PictureInfo> pictures) {
		HeirDocument document = named.document();
		return new DocumentResponse(document.id(), document.heirId(), named.heirName(), document.type(),
				document.validUntil(), document.comments(), PictureResponse.list(base(document.id()), pictures));
	}

	/** Where the document's photos live */
	static String base(UUID documentId) {
		return "/api/documents/" + documentId;
	}

}
