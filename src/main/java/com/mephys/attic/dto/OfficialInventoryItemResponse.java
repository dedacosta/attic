package com.mephys.attic.dto;

import com.mephys.attic.model.OfficialInventoryItem;
import com.mephys.attic.model.Location;
import com.mephys.attic.model.PictureInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for an item, with its photos cover first.
 */
public record OfficialInventoryItemResponse(UUID id, String name, int quantity, @Nullable LocalDate date,
		@Nullable Location location, boolean existent, BigDecimal valueEur, String owner, @Nullable String comments,
		List<PictureResponse> pictures) {

	public static OfficialInventoryItemResponse of(OfficialInventoryItem item, List<PictureInfo> pictures) {
		return new OfficialInventoryItemResponse(item.id(), item.name(), item.quantity(), item.date(), item.location(),
				item.existent(), item.valueEur(), item.owner(), item.comments(),
				PictureResponse.list(base(item.id()), pictures));
	}

	/** Where the item's photos live */
	public static String base(UUID itemId) {
		return "/api/official-inventory/" + itemId;
	}

}
