-- Administrators edit everything and manage accounts; users can only look.
-- Accounts that existed before are administrators (there was only one kind of account).
ALTER TABLE app_user ADD COLUMN role TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('ADMIN', 'USER'));
UPDATE app_user SET role = 'ADMIN';
