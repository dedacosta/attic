package com.mephys.attic.model;

import org.jspecify.annotations.Nullable;

/**
 * One detail of a property, such as "Artigo matricial" → "1234". Label and value are stripped.
 */
public record PropertyFact(String label, String value) {

	public PropertyFact(@Nullable String label, @Nullable String value) {
		this.label = (label != null) ? label.strip() : "";
		this.value = (value != null) ? value.strip() : "";
	}

	public boolean isBlank() {
		return label.isEmpty() && value.isEmpty();
	}

}
