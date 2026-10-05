-- The annual contribution: the years added so far, and what each heir contributed in a year
CREATE TABLE contribution_year (
	year INTEGER PRIMARY KEY CHECK (year BETWEEN 1900 AND 2999)
);

CREATE TABLE contribution (
	year         INTEGER NOT NULL REFERENCES contribution_year (year) ON DELETE CASCADE,
	heir_id      TEXT NOT NULL REFERENCES heir (id) ON DELETE CASCADE,
	amount_cents INTEGER NOT NULL CHECK (amount_cents >= 0),
	PRIMARY KEY (year, heir_id)
);
