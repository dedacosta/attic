-- A super-administrator can additionally delete accounts, change roles and manage
-- administrators. The allowed roles are part of a CHECK constraint, which SQLite can only change by
-- rebuilding the table; no other table refers to app_user.
CREATE TABLE app_user_new (
	username      TEXT PRIMARY KEY COLLATE NOCASE,
	password_hash TEXT NOT NULL,
	created_at    TEXT NOT NULL,
	role          TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'USER'))
);
INSERT INTO app_user_new (username, password_hash, created_at, role)
SELECT username, password_hash, created_at, role FROM app_user;
DROP TABLE app_user;
ALTER TABLE app_user_new RENAME TO app_user;

-- The oldest administrator (the account created at setup) becomes the super-administrator
UPDATE app_user SET role = 'SUPER_ADMIN'
WHERE username = (SELECT username FROM app_user WHERE role = 'ADMIN' ORDER BY created_at LIMIT 1);
