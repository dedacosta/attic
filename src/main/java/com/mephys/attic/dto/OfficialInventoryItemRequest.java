package com.mephys.attic.dto;

import com.mephys.attic.model.OfficialInventoryItem;
import com.mephys.attic.model.Location;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Request body for creating or replacing an item. Omitted fields get their defaults.
 */
public record OfficialInventoryItemRequest(String name, @Nullable Integer quantity, @Nullable LocalDate date,
		@Nullable Location location, @Nullable Boolean existent, @Nullable BigDecimal valueEur,
		@Nullable String owner, @Nullable String comments) {

	public OfficialInventoryItem toItem(@Nullable UUID id) {
		return new OfficialInventoryItem(id, name, (quantity != null) ? quantity : OfficialInventoryItem.DEFAULT_QUANTITY, date,
				location, (existent != null) ? existent : true, valueEur, owner, comments);
	}

}
