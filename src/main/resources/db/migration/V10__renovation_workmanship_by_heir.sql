-- Workmanship is ticked off on the heir, next to their payment, instead of being entered on an
-- expense: an heir pays their part, does workmanship, or both. A payment row says which; the rows
-- so far are payments.

ALTER TABLE renovation_payment ADD COLUMN paid INTEGER NOT NULL DEFAULT 1 CHECK (paid IN (0, 1));
ALTER TABLE renovation_payment ADD COLUMN workmanship INTEGER NOT NULL DEFAULT 0 CHECK (workmanship IN (0, 1));

-- The heirs of the workmanship entered on expenses did workmanship
INSERT INTO renovation_payment (renovation_id, heir_id, paid, workmanship)
SELECT DISTINCT renovation_id, heir_id, 0, 1 FROM renovation_expense WHERE heir_id IS NOT NULL
ON CONFLICT DO UPDATE SET workmanship = 1;

-- Expenses no longer have an heir. SQLite cannot drop a column with a reference, so the table is
-- made anew.
CREATE TABLE renovation_expense_new (
	id            INTEGER PRIMARY KEY,
	renovation_id TEXT    NOT NULL REFERENCES renovation (id) ON DELETE CASCADE,
	position      INTEGER NOT NULL,
	description   TEXT    NOT NULL,
	kind          TEXT    NOT NULL CHECK (kind IN ('MATERIALS', 'WORKMANSHIP', 'OTHER')),
	amount_cents  INTEGER NOT NULL CHECK (amount_cents >= 0),
	paid          INTEGER NOT NULL DEFAULT 0 CHECK (paid IN (0, 1))
);

INSERT INTO renovation_expense_new (id, renovation_id, position, description, kind, amount_cents, paid)
SELECT id, renovation_id, position, description, kind, amount_cents, paid FROM renovation_expense;

DROP TABLE renovation_expense;
ALTER TABLE renovation_expense_new RENAME TO renovation_expense;
CREATE INDEX renovation_expense_renovation_id ON renovation_expense (renovation_id, position);
