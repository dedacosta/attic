package com.mephys.attic.model;

import java.util.Map;

public record Picture(String contentType, byte[] data) {

	private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png", "image/gif",
			"gif", "image/webp", "webp", "image/heic", "heic", "image/avif", "avif");

	public Picture {
		if (contentType == null || !EXTENSIONS.containsKey(contentType)) {
			throw new IllegalArgumentException("contentType must be one of " + EXTENSIONS.keySet());
		}
		if (data == null || data.length == 0) {
			throw new IllegalArgumentException("data must not be empty");
		}
	}

	public String fileExtension() {
		return EXTENSIONS.get(contentType);
	}

}
