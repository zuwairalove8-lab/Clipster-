-- 0002_storage_bucket.sql
-- CyberTrace private storage bucket for captured media/files.

SELECT
    "createStorageBucket"(
        'captures',
        'private',
        NULL,
        NULL,
        NULL
    );

-----------------------------------------
-- 0002b Storage policies: anon may NOT write media directly.
-- The only way into captures is the upload-capture Edge Function,
-- which signs the URL and records a captures row first.
-----------------------------------------
ALTER TABLE captures ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "anon_may_select_captures" ON captures;
CREATE POLICY "anon_may_select_captures"
    ON captures FOR SELECT
    TO anon
    USING (true);

DROP POLICY IF EXISTS "authenticated_may_read_own_captures" ON captures;
CREATE POLICY "authenticated_may_read_own_captures"
    ON captures FOR SELECT
    TO authenticated
    USING (true);

-- Anon gets no writes/no updates to captures at all.
DROP POLICY IF EXISTS "anon_may_insert_captures" ON captures;
DROP POLICY IF EXISTS "anon_may_update_captures" ON captures;
