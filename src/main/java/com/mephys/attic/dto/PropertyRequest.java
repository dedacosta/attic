package com.mephys.attic.dto;

import com.mephys.attic.model.Property;
import com.mephys.attic.model.PropertyFact;
import com.mephys.attic.model.PropertyKind;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Request body for creating or replacing a property. The facts replace all existing ones.
 */
public record PropertyRequest(@Nullable PropertyKind kind, String name, @Nullable String address,
		@Nullable BigDecimal valueEur, @Nullable String comments, @Nullable List<PropertyFact> facts) {

	public Property toProperty(@Nullable UUID id) {
		return Property.of(id, kind, name, address, valueEur, comments, facts);
	}

}
