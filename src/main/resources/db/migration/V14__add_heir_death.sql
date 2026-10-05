-- Heirs who have died. The date of death is optional: it may be unknown.
ALTER TABLE heir ADD COLUMN deceased INTEGER NOT NULL DEFAULT 0 CHECK (deceased IN (0, 1));
ALTER TABLE heir ADD COLUMN death_date TEXT;
