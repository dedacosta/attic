-- The whole schema. It replaces the migrations the database grew with, which are in the history
-- of the repository: 24 at first, then 10 more. Changes from here on go in V2, V3 and so on.

-- Heirs, their documents and the accounts

CREATE TABLE heir (
	id                   TEXT PRIMARY KEY,
	name                 TEXT NOT NULL,
	birth_date           TEXT,
	address              TEXT,
	filiation            TEXT,
	sex                  TEXT,
	-- Share of the heritage as a fraction, e.g. 1/3; both NULL when not set
	heritage_numerator   INTEGER,
	heritage_denominator INTEGER,
	comments             TEXT,
	created_at           TEXT,
	updated_at           TEXT,
	parent_id            TEXT REFERENCES heir (id) ON DELETE SET NULL,
	deceased             INTEGER NOT NULL DEFAULT 0 CHECK (deceased IN (0, 1)),
	death_date           TEXT,
	CHECK ((heritage_numerator IS NULL) = (heritage_denominator IS NULL)),
	CHECK (heritage_denominator IS NULL
		OR (heritage_denominator > 0 AND heritage_numerator BETWEEN 0 AND heritage_denominator))
);

CREATE INDEX heir_parent_id ON heir (parent_id);

CREATE TABLE heir_document (
	id          TEXT PRIMARY KEY,
	type        TEXT NOT NULL,
	valid_until TEXT,
	comments    TEXT,
	heir_id     TEXT REFERENCES heir (id),
	created_at  TEXT,
	updated_at  TEXT
);

CREATE INDEX heir_document_heir_id ON heir_document (heir_id);

CREATE TABLE document_picture (
	id           TEXT PRIMARY KEY,
	document_id  TEXT NOT NULL REFERENCES heir_document (id) ON DELETE CASCADE,
	position     INTEGER NOT NULL,
	file_name    TEXT NOT NULL,
	content_type TEXT NOT NULL,
	thumbnail    BLOB
);

CREATE INDEX document_picture_document_id ON document_picture (document_id, position);

CREATE TABLE app_user (
	username      TEXT PRIMARY KEY COLLATE NOCASE,
	password_hash TEXT NOT NULL,
	created_at    TEXT NOT NULL,
	role          TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'USER')),
	heir_id       TEXT REFERENCES heir (id) ON DELETE SET NULL,
	email         TEXT,
	phone         TEXT
);

CREATE UNIQUE INDEX app_user_heir_id ON app_user (heir_id) WHERE heir_id IS NOT NULL;

CREATE TABLE invitation (
	id         TEXT PRIMARY KEY,
	token_hash TEXT NOT NULL UNIQUE,
	role       TEXT NOT NULL CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'USER')),
	heir_id    TEXT REFERENCES heir (id) ON DELETE SET NULL,
	created_by TEXT NOT NULL,
	created_at TEXT NOT NULL,
	expires_at TEXT NOT NULL
);

CREATE TABLE app_setting (
	name  TEXT PRIMARY KEY,
	value TEXT NOT NULL
);

-- The estate: buildings and land, with their facts, pictures and documents

CREATE TABLE property (
	id          TEXT PRIMARY KEY,
	name        TEXT NOT NULL,
	address     TEXT,
	-- Estimated value; NULL when not estimated
	value_cents INTEGER CHECK (value_cents IS NULL OR value_cents >= 0),
	comments    TEXT,
	created_at  TEXT,
	updated_at  TEXT,
	kind        TEXT NOT NULL DEFAULT 'LAND' CHECK (kind IN ('BUILDING', 'LAND'))
);

CREATE TABLE property_fact (
	property_id TEXT    NOT NULL REFERENCES property (id) ON DELETE CASCADE,
	position    INTEGER NOT NULL,
	label       TEXT    NOT NULL,
	value       TEXT    NOT NULL,
	PRIMARY KEY (property_id, position)
);

CREATE TABLE property_picture (
	id           TEXT PRIMARY KEY,
	property_id  TEXT NOT NULL REFERENCES property (id) ON DELETE CASCADE,
	position     INTEGER NOT NULL,
	file_name    TEXT NOT NULL,
	content_type TEXT NOT NULL,
	thumbnail    BLOB
);

CREATE INDEX property_picture_property_id ON property_picture (property_id, position);

CREATE TABLE property_document (
	id          TEXT PRIMARY KEY,
	property_id TEXT NOT NULL REFERENCES property (id) ON DELETE CASCADE,
	type        TEXT NOT NULL CHECK (type IN ('TITLE_DEED', 'LAND_REGISTRY', 'TAX_RECORD', 'PLAN',
		'HOUSING_LICENCE', 'OTHER')),
	date        TEXT,
	notes       TEXT,
	created_at  TEXT,
	updated_at  TEXT
);

CREATE INDEX property_document_property_id ON property_document (property_id);

CREATE TABLE property_document_file (
	id           TEXT PRIMARY KEY,
	document_id  TEXT NOT NULL REFERENCES property_document (id) ON DELETE CASCADE,
	position     INTEGER NOT NULL,
	file_name    TEXT NOT NULL,
	content_type TEXT NOT NULL,
	thumbnail    BLOB
);

CREATE INDEX property_document_file_document_id ON property_document_file (document_id, position);

-- Catalog and inventory. A catalog item can be in a building and one of its rooms; the inventory
-- is kept per building, as a list of rooms with their items, then the building's other items and
-- its missing items.

CREATE TABLE catalog_item (
	id          TEXT    PRIMARY KEY,
	name        TEXT    NOT NULL,
	quantity    INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0),
	date        TEXT,
	existent    INTEGER NOT NULL DEFAULT 1 CHECK (existent IN (0, 1)),
	value_cents INTEGER NOT NULL DEFAULT 0 CHECK (value_cents >= 0),
	owner       TEXT    NOT NULL DEFAULT 'Heritage',
	comments    TEXT,
	created_at  TEXT,
	updated_at  TEXT,
	-- A room or a building that is deleted leaves its items where they are, without it
	property_id TEXT REFERENCES property (id) ON DELETE SET NULL,
	room_id     TEXT REFERENCES inventory_room (id) ON DELETE SET NULL
);

CREATE INDEX catalog_item_room_id ON catalog_item (room_id);
CREATE INDEX catalog_item_property_id ON catalog_item (property_id);

CREATE TABLE catalog_picture (
	id           TEXT PRIMARY KEY,
	item_id      TEXT NOT NULL REFERENCES catalog_item (id) ON DELETE CASCADE,
	position     INTEGER NOT NULL,
	file_name    TEXT NOT NULL,
	content_type TEXT NOT NULL,
	thumbnail    BLOB
);

CREATE INDEX catalog_picture_item_id ON catalog_picture (item_id, position);

-- The rooms of a building, entered on the building in an order of the user's choosing, with a
-- description to tell them apart and, for the PDF, another title if wanted
CREATE TABLE inventory_room (
	id          TEXT PRIMARY KEY,
	property_id TEXT NOT NULL REFERENCES property (id) ON DELETE CASCADE,
	title       TEXT NOT NULL,
	comments    TEXT,
	created_at  TEXT,
	updated_at  TEXT,
	position    INTEGER NOT NULL DEFAULT 0,
	pdf_title   TEXT,
	description TEXT
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
	-- An item is in a room or, as one of the other or missing items, directly in a building
	room_id     TEXT REFERENCES inventory_room (id) ON DELETE CASCADE,
	property_id TEXT REFERENCES property (id) ON DELETE CASCADE,
	position    INTEGER NOT NULL,
	description TEXT    NOT NULL,
	quantity    INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0),
	-- Tells the two lists of a building apart: 1 for its other items, 0 for its missing ones;
	-- always 1 in a room
	existent    INTEGER NOT NULL DEFAULT 1 CHECK (existent IN (0, 1)),
	value_cents INTEGER NOT NULL DEFAULT 0 CHECK (value_cents >= 0),
	CHECK ((room_id IS NULL) <> (property_id IS NULL))
);

CREATE INDEX inventory_item_room_id ON inventory_item (room_id, position);
CREATE INDEX inventory_item_property_id ON inventory_item (property_id, position);

-- Yearly contributions and renovations

CREATE TABLE contribution_year (
	year    INTEGER PRIMARY KEY CHECK (year BETWEEN 1900 AND 2999),
	comment TEXT
);

CREATE TABLE contribution (
	year         INTEGER NOT NULL REFERENCES contribution_year (year) ON DELETE CASCADE,
	heir_id      TEXT NOT NULL REFERENCES heir (id) ON DELETE CASCADE,
	amount_cents INTEGER NOT NULL CHECK (amount_cents >= 0),
	PRIMARY KEY (year, heir_id)
);

-- A renovation costs the total of its expenses
CREATE TABLE renovation (
	id          TEXT PRIMARY KEY,
	year        INTEGER NOT NULL CHECK (year BETWEEN 1900 AND 2999),
	title       TEXT NOT NULL,
	description TEXT,
	comment     TEXT,
	created_at  TEXT NOT NULL
);

-- What an heir gave to a renovation: they paid their part, did workmanship, or both
CREATE TABLE renovation_payment (
	renovation_id TEXT NOT NULL REFERENCES renovation (id) ON DELETE CASCADE,
	heir_id       TEXT NOT NULL REFERENCES heir (id) ON DELETE CASCADE,
	paid          INTEGER NOT NULL DEFAULT 1 CHECK (paid IN (0, 1)),
	workmanship   INTEGER NOT NULL DEFAULT 0 CHECK (workmanship IN (0, 1)),
	PRIMARY KEY (renovation_id, heir_id)
);

CREATE TABLE renovation_expense (
	id            INTEGER PRIMARY KEY,
	renovation_id TEXT    NOT NULL REFERENCES renovation (id) ON DELETE CASCADE,
	position      INTEGER NOT NULL,
	description   TEXT    NOT NULL,
	kind          TEXT    NOT NULL CHECK (kind IN ('MATERIALS', 'WORKMANSHIP', 'OTHER')),
	amount_cents  INTEGER NOT NULL CHECK (amount_cents >= 0),
	paid          INTEGER NOT NULL DEFAULT 0 CHECK (paid IN (0, 1))
);

CREATE INDEX renovation_expense_renovation_id ON renovation_expense (renovation_id, position);
