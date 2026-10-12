package com.mephys.attic.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.jspecify.annotations.Nullable;

/**
 * A line of the inventory, in a room or in one of the lists of a building (its other items,
 * its missing items): what it is, how many there are and what the line is worth.
 */
public record InventoryItem(String description, int quantity, BigDecimal valueEur) {

	public static final int DEFAULT_QUANTITY = 1;

	public static final BigDecimal DEFAULT_VALUE_EUR = new BigDecimal("0.00");

	public InventoryItem {
		if (description == null || description.isBlank()) {
			throw new IllegalArgumentException("description must not be blank");
		}
		if (quantity < 0) {
			throw new IllegalArgumentException("quantity must not be negative");
		}
		description = description.strip();
		valueEur = (valueEur != null) ? valueEur.setScale(2, RoundingMode.HALF_UP) : DEFAULT_VALUE_EUR;
		if (valueEur.signum() < 0) {
			throw new IllegalArgumentException("valueEur must not be negative");
		}
	}

	/**
	 * An item from a request body; an omitted quantity is 1 and an omitted value 0.
	 */
	public static InventoryItem of(String description, @Nullable Integer quantity, @Nullable BigDecimal valueEur) {
		return new InventoryItem(description, (quantity != null) ? quantity : DEFAULT_QUANTITY, valueEur);
	}

}
