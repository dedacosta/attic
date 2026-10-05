package com.mephys.attic.model;

import java.util.Map;

/**
 * A stored file: a picture, or for documents also a PDF.
 */
public record Picture(String contentType, byte[] data) {

	public static final String PDF = "application/pdf";

	private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png", "image/gif",
			"gif", "image/webp", "webp", "image/heic", "heic", "image/avif", "avif", PDF, "pdf");

	public Picture {
		if (contentType == null || !EXTENSIONS.containsKey(contentType)) {
			throw new IllegalArgumentException("contentType must be one of " + EXTENSIONS.keySet());
		}
		if (data == null || data.length == 0) {
			throw new IllegalArgumentException("data must not be empty");
		}
	}

	public boolean isPdf() {
		return PDF.equals(contentType);
	}

	public String fileExtension() {
		return EXTENSIONS.get(contentType);
	}

}
