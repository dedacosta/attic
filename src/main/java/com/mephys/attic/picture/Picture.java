package com.mephys.attic.picture;

import java.util.Map;

public record Picture(String contentType, byte[] data) {

	// PDF only arrives through PictureUploads.readImageOrPdf, for files of official documents
	private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png", "image/gif",
			"gif", "image/webp", "webp", "image/heic", "heic", "image/avif", "avif", "application/pdf", "pdf");

	public Picture {
		if (contentType == null || !EXTENSIONS.containsKey(contentType)) {
			throw new IllegalArgumentException("contentType must be one of " + EXTENSIONS.keySet());
		}
		if (data == null || data.length == 0) {
			throw new IllegalArgumentException("data must not be empty");
		}
	}

	String fileExtension() {
		return EXTENSIONS.get(contentType);
	}

}
