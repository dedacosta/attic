package com.mephys.attic.dto;

import com.mephys.attic.model.InventoryRoom;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Request body for creating or replacing a room. The items replace all existing ones. The
 * building is given when the room is created and never changes; the description is entered on
 * the building and kept.
 */
public record InventoryRoomRequest(@Nullable UUID propertyId, String title, @Nullable String pdfTitle, @Nullable String comments,
		@Nullable List<InventoryItemRequest> items) {

	public InventoryRoom toRoom(@Nullable UUID id, @Nullable UUID propertyId) {
		return new InventoryRoom(id, propertyId, title, null, pdfTitle, comments, InventoryItemRequest.toItems(items));
	}

}
