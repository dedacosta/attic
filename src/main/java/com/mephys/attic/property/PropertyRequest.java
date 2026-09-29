package com.mephys.attic.property;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Request body for creating or replacing a property. The facts replace all existing ones.
 */
record PropertyRequest(@Nullable PropertyKind kind, String name, @Nullable String address,
		@Nullable BigDecimal valueEur, @Nullable String comments, @Nullable List<PropertyFact> facts) {

	Property toProperty(@Nullable UUID id) {
		return new Property(id, kind, name, address, valueEur, comments, facts);
	}

}
