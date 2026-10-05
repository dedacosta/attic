package com.mephys.attic.dto;

import com.mephys.attic.model.DocumentType;
import com.mephys.attic.model.HeirDocument;
import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.repository.DocumentRepository.NamedDocument;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for a document, with the name of its heir. {@code pictureUrl} and
 * {@code thumbnailUrl} are {@code null} when the document has no picture or no thumbnail.
 */
public record DocumentResponse(UUID id, UUID heirId, String heir, DocumentType type, @Nullable LocalDate validUntil,
		@Nullable String comments, @Nullable String pictureUrl, @Nullable String thumbnailUrl) {

	public static DocumentResponse of(NamedDocument named, @Nullable PictureInfo picture) {
		HeirDocument document = named.document();
		String base = "/api/documents/" + document.id();
		return new DocumentResponse(document.id(), document.heirId(), named.heirName(), document.type(),
				document.validUntil(), document.comments(), PictureInfo.pictureUrl(base, picture),
				PictureInfo.thumbnailUrl(base, picture));
	}

}
