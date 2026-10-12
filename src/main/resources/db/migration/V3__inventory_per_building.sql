-- The inventory is kept per building: a room belongs to a building, and the items without a
-- room, the "other items", belong to a building directly. No inventory had been entered in
-- the shape of V2, so its tables are made anew.

DROP TABLE inventory_item;
DROP TABLE inventory_picture;
DROP TABLE inventory_room;

CREATE TABLE inventory_room (
	id          TEXT PRIMARY KEY,
	property_id TEXT NOT NULL REFERENCES property (id) ON DELETE CASCADE,
	title       TEXT NOT NULL,
	comments    TEXT,
	created_at  TEXT,
	updated_at  TEXT
);

CREATE INDEX inventory_room_property_id ON inventory_room (property_id);

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
	-- An item is in a room or, as one of the other items, directly in a building
	room_id     TEXT REFERENCES inventory_room (id) ON DELETE CASCADE,
	property_id TEXT REFERENCES property (id) ON DELETE CASCADE,
	position    INTEGER NOT NULL,
	description TEXT    NOT NULL,
	quantity    INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0),
	existent    INTEGER NOT NULL DEFAULT 1 CHECK (existent IN (0, 1)),
	CHECK ((room_id IS NULL) <> (property_id IS NULL))
);

CREATE INDEX inventory_item_room_id ON inventory_item (room_id, position);
CREATE INDEX inventory_item_property_id ON inventory_item (property_id, position);
