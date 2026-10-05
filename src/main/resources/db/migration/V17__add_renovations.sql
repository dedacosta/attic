-- Renovations of the house: a cost shared by those who pay the contribution in that year, and
-- which of them have paid their part
CREATE TABLE renovation (
	id          TEXT PRIMARY KEY,
	year        INTEGER NOT NULL CHECK (year BETWEEN 1900 AND 2999),
	title       TEXT NOT NULL,
	description TEXT,
	cost_cents  INTEGER NOT NULL CHECK (cost_cents >= 0),
	comment     TEXT,
	created_at  TEXT NOT NULL
);

CREATE TABLE renovation_payment (
	renovation_id TEXT NOT NULL REFERENCES renovation (id) ON DELETE CASCADE,
	heir_id       TEXT NOT NULL REFERENCES heir (id) ON DELETE CASCADE,
	PRIMARY KEY (renovation_id, heir_id)
);
