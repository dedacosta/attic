package com.mephys.attic.property;

import java.util.List;

/**
 * The real estate of the estate: its buildings and its land parcels, each list in the order given.
 */
record Estate(List<Building> buildings, List<Land> lands) {

	static Estate of(List<? extends Property> properties) {
		return new Estate(properties.stream().filter(Building.class::isInstance).map(Building.class::cast).toList(),
				properties.stream().filter(Land.class::isInstance).map(Land.class::cast).toList());
	}

}
