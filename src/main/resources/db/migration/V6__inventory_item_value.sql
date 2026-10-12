-- An item of the inventory has a value. Whether it is still in the house is no longer said
-- item by item: a building has a list of missing items, next to its other items. The flag
-- "existent" now tells those two lists apart; in a room it is always 1. Items of a room that
-- were marked as missing move to the missing items of the room's building.

ALTER TABLE inventory_item ADD COLUMN value_cents INTEGER NOT NULL DEFAULT 0 CHECK (value_cents >= 0);

UPDATE inventory_item
SET property_id = (SELECT property_id FROM inventory_room WHERE inventory_room.id = inventory_item.room_id),
	room_id = NULL
WHERE existent = 0 AND room_id IS NOT NULL;
