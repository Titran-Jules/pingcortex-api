CREATE UNIQUE INDEX ux_refresh_token_hash ON refresh_token(token_hash);

ALTER TABLE refresh_token ADD COLUMN family_id UUID;
