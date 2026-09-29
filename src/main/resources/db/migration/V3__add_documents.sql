-- Documents of the people in the house (ID cards, passports, ...), one picture each
CREATE TABLE person_document (
	id          TEXT PRIMARY KEY,
	person      TEXT NOT NULL,
	type        TEXT NOT NULL,
	valid_until TEXT,
	comments    TEXT
);

CREATE TABLE document_picture (
	id           TEXT PRIMARY KEY,
	document_id  TEXT NOT NULL UNIQUE REFERENCES person_document (id) ON DELETE CASCADE,
	file_name    TEXT NOT NULL,
	content_type TEXT NOT NULL,
	thumbnail    BLOB
);
