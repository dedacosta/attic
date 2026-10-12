package com.mephys.attic.dto;

import com.mephys.attic.model.InventoryItem;
import com.mephys.attic.model.InventoryRoom;
import com.mephys.attic.model.PictureInfo;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for a room, with its items in their order and its photos cover first.
 */
public record InventoryRoomResponse(UUID id, UUID propertyId, String title, @Nullable String description,
		@Nullable String pdfTitle, @Nullable String comments, List<InventoryItem> items,
		List<PictureResponse> pictures) {

	public static InventoryRoomResponse of(InventoryRoom room, List<PictureInfo> pictures) {
		return new InventoryRoomResponse(room.id(), room.propertyId(), room.title(), room.description(),
				room.pdfTitle(), room.comments(), room.items(),
				PictureResponse.list(base(room.id()), pictures));
	}

	/** Where the room's photos live */
	public static String base(UUID roomId) {
		return "/api/inventory/" + roomId;
	}

}
