package com.mephys.attic.dto;

import com.mephys.attic.model.CatalogItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Request body for creating or replacing an item. Omitted fields get their defaults.
 */
public record CatalogItemRequest(String name, @Nullable Integer quantity, @Nullable LocalDate date,
		@Nullable UUID propertyId, @Nullable UUID roomId, @Nullable Boolean existent, @Nullable BigDecimal valueEur,
		@Nullable List<UUID> ownerIds, @Nullable String comments) {

	/**
	 * The item, in the building and the room given, which are those of the request once checked.
	 */
	public CatalogItem toItem(@Nullable UUID id, @Nullable UUID propertyId, @Nullable UUID roomId) {
		return new CatalogItem(id, name, (quantity != null) ? quantity : CatalogItem.DEFAULT_QUANTITY, date,
				propertyId, roomId, existent == null || existent, valueEur, ownerIds, comments);
	}

}
