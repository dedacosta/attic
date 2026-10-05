-- An item or a document can have several photos, in order; position 0 is the cover shown on
-- cards. SQLite cannot drop the UNIQUE constraint on the owner column, so both tables are
-- rebuilt. No other table refers to them. Existing pictures become covers.
CREATE TABLE inventory_picture_new (
	id           TEXT PRIMARY KEY,
	item_id      TEXT NOT NULL REFERENCES inventory_item (id) ON DELETE CASCADE,
	position     INTEGER NOT NULL,
	file_name    TEXT NOT NULL,
	content_type TEXT NOT NULL,
	thumbnail    BLOB
);
INSERT INTO inventory_picture_new (id, item_id, position, file_name, content_type, thumbnail)
SELECT id, item_id, 0, file_name, content_type, thumbnail FROM inventory_picture;
DROP TABLE inventory_picture;
ALTER TABLE inventory_picture_new RENAME TO inventory_picture;
CREATE INDEX inventory_picture_item_id ON inventory_picture (item_id, position);

CREATE TABLE document_picture_new (
	id           TEXT PRIMARY KEY,
	document_id  TEXT NOT NULL REFERENCES heir_document (id) ON DELETE CASCADE,
	position     INTEGER NOT NULL,
	file_name    TEXT NOT NULL,
	content_type TEXT NOT NULL,
	thumbnail    BLOB
);
INSERT INTO document_picture_new (id, document_id, position, file_name, content_type, thumbnail)
SELECT id, document_id, 0, file_name, content_type, thumbnail FROM document_picture;
DROP TABLE document_picture;
ALTER TABLE document_picture_new RENAME TO document_picture;
CREATE INDEX document_picture_document_id ON document_picture (document_id, position);
