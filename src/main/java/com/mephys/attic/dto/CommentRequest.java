package com.mephys.attic.dto;

import org.jspecify.annotations.Nullable;

/** A free comment, such as on a year of the annual contribution or on a renovation */
public record CommentRequest(@Nullable String comment) {

	public static final int MAX_LENGTH = 1000;

	/**
	 * The comment without surrounding blanks, or {@code null} for a blank one.
	 * @throws IllegalArgumentException if it is longer than {@link #MAX_LENGTH}
	 */
	public @Nullable String cleaned() {
		String cleaned = (comment != null && !comment.isBlank()) ? comment.strip() : null;
		if (cleaned != null && cleaned.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("comment must have at most " + MAX_LENGTH + " characters");
		}
		return cleaned;
	}

}
