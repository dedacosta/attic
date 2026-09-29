-- Accounts that may sign in; the first one is created on the setup screen
CREATE TABLE app_user (
	username      TEXT PRIMARY KEY COLLATE NOCASE,
	password_hash TEXT NOT NULL,
	created_at    TEXT NOT NULL
);

-- Values the application generates once and keeps, such as the remember-me key
CREATE TABLE app_setting (
	name  TEXT PRIMARY KEY,
	value TEXT NOT NULL
);
