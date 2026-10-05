-- An heir can be the child of another heir. Deleting the parent keeps the children, without a parent.
ALTER TABLE heir ADD COLUMN parent_id TEXT REFERENCES heir (id) ON DELETE SET NULL;
CREATE INDEX heir_parent_id ON heir (parent_id);
