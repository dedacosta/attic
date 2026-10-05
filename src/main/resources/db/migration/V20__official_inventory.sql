-- The official inventory: a second list of items with the structure of inventory_item and
-- inventory_picture. It starts empty.
CREATE TABLE official_inventory_item (
	id          TEXT    PRIMARY KEY,
	name        TEXT    NOT NULL,
	quantity    INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0),
	date        TEXT,
	location    TEXT,
	existent    INTEGER NOT NULL DEFAULT 1 CHECK (existent IN (0, 1)),
	value_cents INTEGER NOT NULL DEFAULT 0 CHECK (value_cents >= 0),
	owner       TEXT    NOT NULL DEFAULT 'Heritage',
	comments    TEXT,
	created_at  TEXT,
	updated_at  TEXT
);

-- Photos: files named <id>.<extension>, position 0 is the cover
CREATE TABLE official_inventory_picture (
	id           TEXT PRIMARY KEY,
	item_id      TEXT NOT NULL REFERENCES official_inventory_item (id) ON DELETE CASCADE,
	position     INTEGER NOT NULL,
	file_name    TEXT NOT NULL,
	content_type TEXT NOT NULL,
	thumbnail    BLOB
);
CREATE INDEX official_inventory_picture_item_id ON official_inventory_picture (item_id, position);
