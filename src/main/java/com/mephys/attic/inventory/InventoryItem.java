package com.mephys.attic.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

record InventoryItem(UUID id, String name, int quantity, @Nullable LocalDate date, @Nullable Location location,
		boolean existent, BigDecimal valueEur, String owner, @Nullable String comments) {

	static final int DEFAULT_QUANTITY = 1;

	static final BigDecimal DEFAULT_VALUE_EUR = new BigDecimal("0.00");

	static final String DEFAULT_OWNER = "Heritage";

	InventoryItem {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		if (quantity < 0) {
			throw new IllegalArgumentException("quantity must not be negative");
		}
		id = (id != null) ? id : UUID.randomUUID();
		valueEur = (valueEur != null) ? valueEur.setScale(2, RoundingMode.HALF_UP) : DEFAULT_VALUE_EUR;
		if (valueEur.signum() < 0) {
			throw new IllegalArgumentException("valueEur must not be negative");
		}
		owner = (owner != null && !owner.isBlank()) ? owner : DEFAULT_OWNER;
		comments = (comments != null && !comments.isBlank()) ? comments.strip() : null;
	}

	/**
	 * Create a new item with a random id and all defaults applied.
	 */
	static InventoryItem of(String name) {
		return new InventoryItem(null, name, DEFAULT_QUANTITY, null, null, true, null, null, null);
	}

}
