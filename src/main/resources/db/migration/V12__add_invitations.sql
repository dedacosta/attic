-- Invitations to register: an administrator hands out a link with a one-time token, and whoever
-- opens it chooses their own username and password. Only a hash of the token is kept. The new
-- account gets the role and heir chosen by the administrator; deleting the heir unlinks it.
CREATE TABLE invitation (
	id         TEXT PRIMARY KEY,
	token_hash TEXT NOT NULL UNIQUE,
	role       TEXT NOT NULL CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'USER')),
	heir_id    TEXT REFERENCES heir (id) ON DELETE SET NULL,
	created_by TEXT NOT NULL,
	created_at TEXT NOT NULL,
	expires_at TEXT NOT NULL
);
