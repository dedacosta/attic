-- Schema as it was before Flyway (created by schema.sql). Existing databases are baselined at this version.

CREATE TABLE inventory_item (
	id          TEXT    PRIMARY KEY,
	name        TEXT    NOT NULL,
	quantity    INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0),
	date        TEXT,
	location    TEXT,
	existent    INTEGER NOT NULL DEFAULT 1 CHECK (existent IN (0, 1)),
	value_cents INTEGER NOT NULL DEFAULT 0 CHECK (value_cents >= 0),
	owner       TEXT    NOT NULL DEFAULT 'Heritage'
);

-- Picture files live in the picture directory, named <id>.<extension>;
-- thumbnail is a small JPEG, or NULL when the format could not be decoded
CREATE TABLE inventory_picture (
	id           TEXT PRIMARY KEY,
	item_id      TEXT NOT NULL UNIQUE REFERENCES inventory_item (id) ON DELETE CASCADE,
	file_name    TEXT NOT NULL,
	content_type TEXT NOT NULL,
	thumbnail    BLOB
);
