-- 1) Timestamps : passons de TIMESTAMP (naive) à TIMESTAMPTZ (instant absolu)
ALTER TABLE "user"
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE refresh_token
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE user_api_key
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE course
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE course_material
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE user_concept_mastery
    ALTER COLUMN last_reviewed_at TYPE TIMESTAMPTZ USING last_reviewed_at AT TIME ZONE 'UTC';

ALTER TABLE quiz_question
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE quiz_attempt
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE feynman_submission
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE chat_session
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE chat_message
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE review_schedule
    ALTER COLUMN next_review_at TYPE TIMESTAMPTZ USING next_review_at AT TIME ZONE 'UTC';

ALTER TABLE ai_usage_log
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

-- 2) Refresh tokens : suivi de famille (détection de réutilisation / vol)
UPDATE refresh_token SET family_id = id WHERE family_id IS NULL;

ALTER TABLE refresh_token
    ALTER COLUMN family_id SET NOT NULL;

ALTER TABLE refresh_token
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_refresh_token_family_id ON refresh_token(family_id);

-- 3) Emails : unicité insensible à la casse + normalisation des données existantes
ALTER TABLE "user" DROP CONSTRAINT IF EXISTS "user_email_key";

UPDATE "user" SET email = lower(email) WHERE email != lower(email);

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_email_lower ON "user" (lower(email));
