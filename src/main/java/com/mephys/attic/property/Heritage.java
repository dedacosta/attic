package com.mephys.attic.property;

import java.util.List;

/**
 * The real estate of the heritage: its houses and its land parcels, each list in the order given.
 */
record Heritage(List<House> houses, List<Land> lands) {

	static Heritage of(List<? extends Property> properties) {
		return new Heritage(properties.stream().filter(House.class::isInstance).map(House.class::cast).toList(),
				properties.stream().filter(Land.class::isInstance).map(Land.class::cast).toList());
	}

}
