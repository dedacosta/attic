package com.mephys.attic.inventory;

import com.mephys.attic.picture.PictureInfo;
import com.mephys.attic.picture.PictureResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for an item, with its photos cover first.
 */
record InventoryItemResponse(UUID id, String name, int quantity, @Nullable LocalDate date,
		@Nullable Location location, boolean existent, BigDecimal valueEur, String owner, @Nullable String comments,
		List<PictureResponse> pictures) {

	static InventoryItemResponse of(InventoryItem item, List<PictureInfo> pictures) {
		return new InventoryItemResponse(item.id(), item.name(), item.quantity(), item.date(), item.location(),
				item.existent(), item.valueEur(), item.owner(), item.comments(),
				PictureResponse.list(base(item.id()), pictures));
	}

	/** Where the item's photos live */
	static String base(UUID itemId) {
		return "/api/items/" + itemId;
	}

}
