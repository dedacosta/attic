-- A catalog item is in a building and one of its rooms, those entered on the building as for the
-- inventory, instead of a room of a fixed list. A room or a building that is deleted leaves its
-- items where they are, without room or building. A room of the fixed list is kept in the comments.

ALTER TABLE catalog_item ADD COLUMN property_id TEXT REFERENCES property (id) ON DELETE SET NULL;
ALTER TABLE catalog_item ADD COLUMN room_id TEXT REFERENCES inventory_room (id) ON DELETE SET NULL;

UPDATE catalog_item SET comments = coalesce(comments || char(10), '') || 'Room: ' || location
WHERE location IS NOT NULL;

ALTER TABLE catalog_item DROP COLUMN location;

CREATE INDEX catalog_item_room_id ON catalog_item (room_id);
CREATE INDEX catalog_item_property_id ON catalog_item (property_id);
