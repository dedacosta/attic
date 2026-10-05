-- A house is called a building from now on. The CHECK on the kind column cannot be changed, so
-- the column is replaced: SQLite can add, drop and rename a column without rebuilding the table,
-- which keeps the rows of the tables that refer to a property. The DEFAULT is only there
-- because a NOT NULL column cannot be added without one; the application always gives the kind.
ALTER TABLE property ADD COLUMN new_kind TEXT NOT NULL DEFAULT 'LAND' CHECK (new_kind IN ('BUILDING', 'LAND'));
UPDATE property SET new_kind = CASE kind WHEN 'HOUSE' THEN 'BUILDING' ELSE 'LAND' END;
ALTER TABLE property DROP COLUMN kind;
ALTER TABLE property RENAME COLUMN new_kind TO kind;
