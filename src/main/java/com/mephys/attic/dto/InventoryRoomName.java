package com.mephys.attic.dto;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A room in the list of rooms of a building: the id of an existing room, or none for a new
 * one, its title and its description.
 */
public record InventoryRoomName(@Nullable UUID id, String title, @Nullable String description) {

	public InventoryRoomName {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("title must not be blank");
		}
		title = title.strip();
		description = (description != null && !description.isBlank()) ? description.strip() : null;
	}

}
