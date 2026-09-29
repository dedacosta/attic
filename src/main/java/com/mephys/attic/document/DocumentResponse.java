package com.mephys.attic.document;

import com.mephys.attic.document.DocumentRepository.NamedDocument;
import com.mephys.attic.picture.PictureInfo;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for a document, with the name of its heir. {@code heirId} and {@code heir} are
 * {@code null} for a document without heir; {@code pictureUrl} and {@code thumbnailUrl} when the
 * document has no picture or no thumbnail.
 */
record DocumentResponse(UUID id, @Nullable UUID heirId, @Nullable String heir, DocumentType type, @Nullable LocalDate validUntil,
		@Nullable String comments, @Nullable String pictureUrl, @Nullable String thumbnailUrl) {

	static DocumentResponse of(NamedDocument named, @Nullable PictureInfo picture) {
		HeirDocument document = named.document();
		String base = "/api/documents/" + document.id();
		return new DocumentResponse(document.id(), document.heirId(), named.heirName(), document.type(),
				document.validUntil(), document.comments(), PictureInfo.pictureUrl(base, picture),
				PictureInfo.thumbnailUrl(base, picture));
	}

}
