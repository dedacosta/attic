-- The inventory becomes a list of rooms, each with a title, photos, comments and its items.
-- An item is a line of its room: a description, a quantity and whether it is still there;
-- the items without a room are the "other items". The former inventory, a second catalog,
-- is not carried over.

DROP TABLE inventory_picture;
DROP TABLE inventory_item;

CREATE TABLE inventory_room (
	id         TEXT PRIMARY KEY,
	title      TEXT NOT NULL,
	comments   TEXT,
	created_at TEXT,
	updated_at TEXT
);

CREATE TABLE inventory_picture (
	id           TEXT PRIMARY KEY,
	room_id      TEXT NOT NULL REFERENCES inventory_room (id) ON DELETE CASCADE,
	position     INTEGER NOT NULL,
	file_name    TEXT NOT NULL,
	content_type TEXT NOT NULL,
	thumbnail    BLOB
);

CREATE INDEX inventory_picture_room_id ON inventory_picture (room_id, position);

CREATE TABLE inventory_item (
	id          INTEGER PRIMARY KEY,
	-- NULL for the other items
	room_id     TEXT REFERENCES inventory_room (id) ON DELETE CASCADE,
	position    INTEGER NOT NULL,
	description TEXT    NOT NULL,
	quantity    INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0),
	existent    INTEGER NOT NULL DEFAULT 1 CHECK (existent IN (0, 1))
);

CREATE INDEX inventory_item_room_id ON inventory_item (room_id, position);
