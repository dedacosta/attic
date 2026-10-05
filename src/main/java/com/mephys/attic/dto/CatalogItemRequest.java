package com.mephys.attic.dto;

import com.mephys.attic.model.CatalogItem;
import com.mephys.attic.model.Location;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Request body for creating or replacing an item. Omitted fields get their defaults.
 */
public record CatalogItemRequest(String name, @Nullable Integer quantity, @Nullable LocalDate date,
		@Nullable Location location, @Nullable Boolean existent, @Nullable BigDecimal valueEur,
		@Nullable String owner, @Nullable String comments) {

	public CatalogItem toItem(@Nullable UUID id) {
		return new CatalogItem(id, name, (quantity != null) ? quantity : CatalogItem.DEFAULT_QUANTITY, date,
				location, (existent != null) ? existent : true, valueEur, owner, comments);
	}

}
