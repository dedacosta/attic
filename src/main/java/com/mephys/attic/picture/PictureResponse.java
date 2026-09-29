package com.mephys.attic.picture;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A photo in a response body. {@code thumbnailUrl} is {@code null} for formats without thumbnail.
 */
public record PictureResponse(UUID id, String url, @Nullable String thumbnailUrl, String contentType) {

	public static PictureResponse of(String base, PictureInfo info) {
		return new PictureResponse(info.id(), info.url(base), info.thumbnailUrl(base), info.contentType());
	}

	public static List<PictureResponse> list(String base, List<PictureInfo> pictures) {
		return pictures.stream().map((info) -> of(base, info)).toList();
	}

}
