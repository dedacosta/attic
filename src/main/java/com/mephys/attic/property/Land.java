package com.mephys.attic.property;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A land parcel of the estate. {@code address} is its location, in free text.
 */
final class Land extends Property {

	Land(@Nullable UUID id, String name, @Nullable String address, @Nullable BigDecimal valueEur,
			@Nullable String comments, @Nullable List<PropertyFact> facts) {
		super(id, name, address, valueEur, comments, facts);
	}

	@Override
	PropertyKind kind() {
		return PropertyKind.LAND;
	}

}
