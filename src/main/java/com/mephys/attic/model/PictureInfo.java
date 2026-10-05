package com.mephys.attic.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * What is known about a stored picture without loading it.
 */
public record PictureInfo(UUID pictureId, boolean hasThumbnail) {

	/**
	 * URL of the picture below {@code base} (e.g. {@code /items/<id>}), or {@code null} without
	 * a picture. It ends in the picture id so that a replaced picture is not served from the
	 * browser cache.
	 */
	public static @Nullable String pictureUrl(String base, @Nullable PictureInfo info) {
		return (info != null) ? base + "/picture?v=" + info.pictureId() : null;
	}

	/**
	 * URL of the thumbnail below {@code base}, or {@code null} without one.
	 */
	public static @Nullable String thumbnailUrl(String base, @Nullable PictureInfo info) {
		return (info != null && info.hasThumbnail()) ? base + "/thumbnail?v=" + info.pictureId() : null;
	}

}
