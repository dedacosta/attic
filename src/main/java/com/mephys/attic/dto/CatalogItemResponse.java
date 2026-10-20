package com.mephys.attic.dto;

import com.mephys.attic.model.CatalogItem;
import com.mephys.attic.model.Heir;
import com.mephys.attic.model.PictureInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for an item, with its owners in the order of the Heirs tab (none for the Heritage)
 * and its photos cover first.
 */
public record CatalogItemResponse(UUID id, String name, int quantity, @Nullable LocalDate date,
		@Nullable UUID propertyId, @Nullable UUID roomId, boolean existent, BigDecimal valueEur, List<Owner> owners,
		@Nullable String comments, List<PictureResponse> pictures) {

	/** An heir who owns the item */
	public record Owner(UUID heirId, String name) {
	}

	/** {@code heirs} are all the heirs, in the order of the Heirs tab */
	public static CatalogItemResponse of(CatalogItem item, List<Heir> heirs, List<PictureInfo> pictures) {
		List<Owner> owners = heirs.stream()
			.filter((heir) -> item.ownerIds().contains(heir.id()))
			.map((heir) -> new Owner(heir.id(), heir.name()))
			.toList();
		return new CatalogItemResponse(item.id(), item.name(), item.quantity(), item.date(), item.propertyId(),
				item.roomId(), item.existent(), item.valueEur(), owners, item.comments(),
				PictureResponse.list(base(item.id()), pictures));
	}

	/** Where the item's photos live */
	public static String base(UUID itemId) {
		return "/api/catalog/" + itemId;
	}

}
