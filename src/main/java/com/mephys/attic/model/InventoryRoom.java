package com.mephys.attic.model;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A room of a building in the inventory: its title and description, which are the name and the
 * description it has on the building, the title it has in the PDF if that is another, its
 * comments and its items in their order. Its photos are kept
 * apart, as for the other things with photos.
 */
public record InventoryRoom(UUID id, UUID propertyId, String title, @Nullable String description,
		@Nullable String pdfTitle,
		@Nullable String comments, List<InventoryItem> items) {

	public InventoryRoom {
		if (propertyId == null) {
			throw new IllegalArgumentException("propertyId is required");
		}
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("title must not be blank");
		}
		id = (id != null) ? id : UUID.randomUUID();
		title = title.strip();
		description = (description != null && !description.isBlank()) ? description.strip() : null;
		pdfTitle = (pdfTitle != null && !pdfTitle.isBlank()) ? pdfTitle.strip() : null;
		comments = (comments != null && !comments.isBlank()) ? comments.strip() : null;
		items = (items != null) ? List.copyOf(items) : List.of();
	}

}
