package com.mephys.attic.property;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PropertyTests {

	@Test
	void aHouseAndALandAreProperties() {
		Property house = Property.of(null, PropertyKind.HOUSE, "Casa", null, null, null, null);
		Property land = Property.of(null, PropertyKind.LAND, "Vinha", null, null, null, null);

		assertThat(house).isInstanceOf(House.class);
		assertThat(house.kind()).isEqualTo(PropertyKind.HOUSE);
		assertThat(land).isInstanceOf(Land.class);
		assertThat(land.kind()).isEqualTo(PropertyKind.LAND);
	}

	@Test
	void housesAndLandShareTheRulesOfAProperty() {
		Land land = new Land(null, "  Vinha ", " ", new BigDecimal("1000.456"), "", List.of(new PropertyFact("", " ")));

		assertThat(land.id()).isNotNull();
		assertThat(land.name()).isEqualTo("Vinha");
		assertThat(land.address()).isNull();
		assertThat(land.valueEur()).isEqualByComparingTo("1000.46");
		assertThat(land.comments()).isNull();
		assertThat(land.facts()).isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> new House(null, " ", null, null, null, null));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new House(null, "Casa", null, new BigDecimal("-1"), null, null));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> Property.of(null, null, "Casa", null, null, null, null));
	}

	@Test
	void theHeritageSeparatesHousesFromLand() {
		Heritage heritage = Heritage.of(List.of(new Land(null, "Vinha", null, null, null, null),
				new House(null, "Casa", null, null, null, null), new Land(null, "Olival", null, null, null, null)));

		assertThat(heritage.houses()).extracting(House::name).containsExactly("Casa");
		assertThat(heritage.lands()).extracting(Land::name).containsExactly("Vinha", "Olival");
	}

}
