-- "person" was too vague: these are the heirs. SQLite renames tables and columns in place and
-- updates the references from other tables (document_picture, app_user) along with them.
ALTER TABLE person RENAME TO heir;
ALTER TABLE person_document RENAME TO heir_document;
ALTER TABLE heir_document RENAME COLUMN person_id TO heir_id;
ALTER TABLE app_user RENAME COLUMN person_id TO heir_id;

-- Indexes cannot be renamed: recreate them under the new names
DROP INDEX person_document_person_id;
CREATE INDEX heir_document_heir_id ON heir_document (heir_id);
DROP INDEX app_user_person_id;
CREATE UNIQUE INDEX app_user_heir_id ON app_user (heir_id) WHERE heir_id IS NOT NULL;
