-- People in the house. Documents now belong to a person instead of naming one.
CREATE TABLE person (
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
	CHECK ((heritage_numerator IS NULL) = (heritage_denominator IS NULL)),
	CHECK (heritage_denominator IS NULL
		OR (heritage_denominator > 0 AND heritage_numerator BETWEEN 0 AND heritage_denominator))
);

-- Each name used on a document becomes a person, with a random (version 4) UUID
INSERT INTO person (id, name)
SELECT lower(substr(h, 1, 8) || '-' || substr(h, 9, 4) || '-4' || substr(h, 14, 3) || '-'
		|| substr('89ab', 1 + abs(random()) % 4, 1) || substr(h, 18, 3) || '-' || substr(h, 21, 12)),
	person
FROM (SELECT hex(randomblob(16)) AS h, person FROM (SELECT DISTINCT person FROM person_document));

-- Link documents to their person. ADD/DROP COLUMN instead of rebuilding the table, because
-- dropping person_document would cascade to document_picture.
ALTER TABLE person_document ADD COLUMN person_id TEXT REFERENCES person (id);
UPDATE person_document SET person_id = (SELECT id FROM person WHERE person.name = person_document.person);
ALTER TABLE person_document DROP COLUMN person;
CREATE INDEX person_document_person_id ON person_document (person_id);
