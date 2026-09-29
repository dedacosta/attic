package com.mephys.attic.property;

import org.jspecify.annotations.Nullable;

/**
 * One detail of a property, such as "Artigo matricial" → "1234". Label and value are stripped.
 */
record PropertyFact(String label, String value) {

	PropertyFact(@Nullable String label, @Nullable String value) {
		this.label = (label != null) ? label.strip() : "";
		this.value = (value != null) ? value.strip() : "";
	}

	boolean isBlank() {
		return label.isEmpty() && value.isEmpty();
	}

}
