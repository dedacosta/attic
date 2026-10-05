package com.mephys.attic.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * What is known about a stored picture without loading it. {@code contentType} tells a PDF
 * from a picture.
 */
public record PictureInfo(UUID id, String contentType, boolean hasThumbnail) {

	/**
	 * URL of the picture below {@code base} (e.g. {@code /api/catalog/<id>}). Picture ids are never
	 * reused, so the browser may cache it forever.
	 */
	public String url(String base) {
		return base + "/pictures/" + id;
	}

	/**
	 * URL of the thumbnail below {@code base}, or {@code null} when the format has none.
	 */
	public @Nullable String thumbnailUrl(String base) {
		return hasThumbnail ? url(base) + "/thumbnail" : null;
	}

}
