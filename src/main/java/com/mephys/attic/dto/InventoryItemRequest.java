package com.mephys.attic.dto;

import com.mephys.attic.model.InventoryItem;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * An item in a request body. Omitted fields get their defaults.
 */
public record InventoryItemRequest(String description, @Nullable Integer quantity, @Nullable BigDecimal valueEur) {

	public InventoryItem toItem() {
		return InventoryItem.of(description, quantity, valueEur);
	}

	public static List<InventoryItem> toItems(@Nullable List<InventoryItemRequest> items) {
		return (items != null) ? items.stream().map(InventoryItemRequest::toItem).toList() : List.of();
	}

}
