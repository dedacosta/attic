-- When rows were created and last changed, as UTC ISO-8601 timestamps set by the server.
-- Rows that existed before stay NULL: their real creation time is unknown.
ALTER TABLE inventory_item ADD COLUMN created_at TEXT;
ALTER TABLE inventory_item ADD COLUMN updated_at TEXT;
ALTER TABLE person ADD COLUMN created_at TEXT;
ALTER TABLE person ADD COLUMN updated_at TEXT;
ALTER TABLE person_document ADD COLUMN created_at TEXT;
ALTER TABLE person_document ADD COLUMN updated_at TEXT;
