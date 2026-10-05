package com.mephys.attic.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A land parcel of the estate. {@code address} is its location, in free text.
 */
public final class Land extends Property {

	public Land(@Nullable UUID id, String name, @Nullable String address, @Nullable BigDecimal valueEur,
			@Nullable String comments, @Nullable List<PropertyFact> facts) {
		super(id, name, address, valueEur, comments, facts);
	}

	@Override
	public PropertyKind kind() {
		return PropertyKind.LAND;
	}

}
