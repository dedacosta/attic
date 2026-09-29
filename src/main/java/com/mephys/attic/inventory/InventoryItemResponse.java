package com.mephys.attic.inventory;

import com.mephys.attic.picture.PictureInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for an item. {@code pictureUrl} and {@code thumbnailUrl} are {@code null}
 * when the item has no picture or no thumbnail.
 */
record InventoryItemResponse(UUID id, String name, int quantity, @Nullable LocalDate date,
		@Nullable Location location, boolean existent, BigDecimal valueEur, String owner, @Nullable String comments,
		@Nullable String pictureUrl, @Nullable String thumbnailUrl) {

	static InventoryItemResponse of(InventoryItem item, @Nullable PictureInfo picture) {
		String base = "/api/items/" + item.id();
		return new InventoryItemResponse(item.id(), item.name(), item.quantity(), item.date(), item.location(),
				item.existent(), item.valueEur(), item.owner(), item.comments(), PictureInfo.pictureUrl(base, picture),
				PictureInfo.thumbnailUrl(base, picture));
	}

}
