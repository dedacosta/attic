-- The inventory is called the catalog from now on; the official inventory is the other list.
-- SQLite points the foreign key of the picture table to the renamed item table by itself.
ALTER TABLE inventory_item RENAME TO catalog_item;
ALTER TABLE inventory_picture RENAME TO catalog_picture;
DROP INDEX inventory_picture_item_id;
CREATE INDEX catalog_picture_item_id ON catalog_picture (item_id, position);
