-- The heritage's real estate: the family house (at most one) and land parcels. Each has
-- details as label/value lines, photos, and official documents whose files may be PDF.
CREATE TABLE property (
	id          TEXT PRIMARY KEY,
	kind        TEXT NOT NULL CHECK (kind IN ('HOUSE', 'LAND')),
	name        TEXT NOT NULL,
	address     TEXT,
	-- Estimated value; NULL when not estimated
	value_cents INTEGER CHECK (value_cents IS NULL OR value_cents >= 0),
	comments    TEXT,
	created_at  TEXT,
	updated_at  TEXT
);
CREATE UNIQUE INDEX property_one_house ON property (kind) WHERE kind = 'HOUSE';

-- Details in the order they were entered, e.g. "Artigo matricial" → "1234"
CREATE TABLE property_fact (
	property_id TEXT    NOT NULL REFERENCES property (id) ON DELETE CASCADE,
	position    INTEGER NOT NULL,
	label       TEXT    NOT NULL,
	value       TEXT    NOT NULL,
	PRIMARY KEY (property_id, position)
);

-- Photos, like inventory_picture: files named <id>.<extension>, position 0 is the cover
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

-- The pages of an official document: images or PDF
CREATE TABLE property_document_file (
	id           TEXT PRIMARY KEY,
	document_id  TEXT NOT NULL REFERENCES property_document (id) ON DELETE CASCADE,
	position     INTEGER NOT NULL,
	file_name    TEXT NOT NULL,
	content_type TEXT NOT NULL,
	thumbnail    BLOB
);
CREATE INDEX property_document_file_document_id ON property_document_file (document_id, position);
