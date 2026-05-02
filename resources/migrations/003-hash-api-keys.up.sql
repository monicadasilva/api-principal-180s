CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE partners ADD COLUMN api_key_hash CHAR(64);

UPDATE partners
SET api_key_hash = encode(digest(api_key::text, 'sha256'), 'hex');

ALTER TABLE partners DROP COLUMN api_key;

ALTER TABLE partners ALTER COLUMN api_key_hash SET NOT NULL;

ALTER TABLE partners ADD CONSTRAINT partners_api_key_hash_key UNIQUE (api_key_hash);
