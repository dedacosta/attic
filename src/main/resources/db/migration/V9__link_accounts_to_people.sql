-- An account can belong to one of the people. A user then sees and edits only that person's card
-- and sees only their documents. Deleting the person unlinks the account.
ALTER TABLE app_user ADD COLUMN person_id TEXT REFERENCES person (id) ON DELETE SET NULL;
CREATE UNIQUE INDEX app_user_person_id ON app_user (person_id) WHERE person_id IS NOT NULL;
