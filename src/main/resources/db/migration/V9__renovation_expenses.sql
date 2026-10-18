-- A renovation is a list of expenses: materials, workmanship or anything else, each with its
-- amount and whether it is paid. Workmanship can be done by an heir. The cost of a renovation is
-- the total of its expenses, so the cost entered so far becomes its one expense.

CREATE TABLE renovation_expense (
	id            INTEGER PRIMARY KEY,
	renovation_id TEXT    NOT NULL REFERENCES renovation (id) ON DELETE CASCADE,
	position      INTEGER NOT NULL,
	description   TEXT    NOT NULL,
	kind          TEXT    NOT NULL CHECK (kind IN ('MATERIALS', 'WORKMANSHIP', 'OTHER')),
	amount_cents  INTEGER NOT NULL CHECK (amount_cents >= 0),
	paid          INTEGER NOT NULL DEFAULT 0 CHECK (paid IN (0, 1)),
	-- The heir who did the workmanship, if one did
	heir_id       TEXT    REFERENCES heir (id) ON DELETE SET NULL
);

CREATE INDEX renovation_expense_renovation_id ON renovation_expense (renovation_id, position);

INSERT INTO renovation_expense (renovation_id, position, description, kind, amount_cents)
SELECT id, 0, title, 'OTHER', cost_cents FROM renovation WHERE cost_cents > 0;

ALTER TABLE renovation DROP COLUMN cost_cents;
