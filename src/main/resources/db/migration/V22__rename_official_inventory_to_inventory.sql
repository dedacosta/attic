-- The official inventory is simply the inventory. The name is free since the old inventory
-- became the catalog.
ALTER TABLE official_inventory_item RENAME TO inventory_item;
ALTER TABLE official_inventory_picture RENAME TO inventory_picture;
DROP INDEX official_inventory_picture_item_id;
CREATE INDEX inventory_picture_item_id ON inventory_picture (item_id, position);
