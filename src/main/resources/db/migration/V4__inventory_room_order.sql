-- The rooms of a building are entered on the building, as a list in an order of the user's
-- choosing. Rooms that exist already keep the order in which they were added.

ALTER TABLE inventory_room ADD COLUMN position INTEGER NOT NULL DEFAULT 0;

UPDATE inventory_room SET position = (
	SELECT count(*) FROM inventory_room earlier
	WHERE earlier.property_id = inventory_room.property_id
		AND (earlier.created_at < inventory_room.created_at
			OR (earlier.created_at = inventory_room.created_at AND earlier.rowid < inventory_room.rowid))
);
