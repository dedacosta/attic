package com.mephys.attic.property;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A house of the heritage, such as the family house. {@code address} is its address.
 */
final class House extends Property {

	House(@Nullable UUID id, String name, @Nullable String address, @Nullable BigDecimal valueEur,
			@Nullable String comments, @Nullable List<PropertyFact> facts) {
		super(id, name, address, valueEur, comments, facts);
	}

	@Override
	PropertyKind kind() {
		return PropertyKind.HOUSE;
	}

}
