package com.mephys.attic.property;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A building of the estate, such as the family building. {@code address} is its address.
 */
final class Building extends Property {

	Building(@Nullable UUID id, String name, @Nullable String address, @Nullable BigDecimal valueEur,
			@Nullable String comments, @Nullable List<PropertyFact> facts) {
		super(id, name, address, valueEur, comments, facts);
	}

	@Override
	PropertyKind kind() {
		return PropertyKind.BUILDING;
	}

}
