package com.mephys.attic.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Request body for creating or replacing an item. Omitted fields get their defaults.
 */
record InventoryItemRequest(String name, @Nullable Integer quantity, @Nullable LocalDate date,
		@Nullable Location location, @Nullable Boolean existent, @Nullable BigDecimal valueEur,
		@Nullable String owner, @Nullable String comments) {

	InventoryItem toItem(@Nullable UUID id) {
		return new InventoryItem(id, name, (quantity != null) ? quantity : InventoryItem.DEFAULT_QUANTITY, date,
				location, (existent != null) ? existent : true, valueEur, owner, comments);
	}

}
