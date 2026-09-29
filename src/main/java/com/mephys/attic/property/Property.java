package com.mephys.attic.property;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The family house or a land parcel of the heritage. {@code valueEur} is {@code null} when not
 * estimated; {@code facts} are its details in order, without blank lines.
 */
record Property(UUID id, PropertyKind kind, String name, @Nullable String address, @Nullable BigDecimal valueEur,
		@Nullable String comments, List<PropertyFact> facts) {

	Property {
		if (kind == null) {
			throw new IllegalArgumentException("kind must not be null");
		}
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		if (valueEur != null) {
			valueEur = valueEur.setScale(2, RoundingMode.HALF_UP);
			if (valueEur.signum() < 0) {
				throw new IllegalArgumentException("valueEur must not be negative");
			}
		}
		id = (id != null) ? id : UUID.randomUUID();
		name = name.strip();
		address = blankToNull(address);
		comments = blankToNull(comments);
		facts = (facts != null) ? facts.stream().filter((fact) -> !fact.isBlank()).toList() : List.of();
	}

	private static @Nullable String blankToNull(@Nullable String text) {
		return (text != null && !text.isBlank()) ? text.strip() : null;
	}

}
