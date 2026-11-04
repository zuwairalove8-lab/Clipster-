-- 0005_captures_rls.sql
-- RLS for captures: anon may only select (through the Edge Functions).
-- Authenticated investigator reads all captures for the links they own.

ALTER TABLE captures ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "anon_may_select_captures" ON captures;
DROP POLICY IF EXISTS "authenticated_may_select_captures" ON captures;

CREATE POLICY "anonymous_role_cannot_see_captures"
    ON captures FOR SELECT
    TO anon
    USING (false);

CREATE POLICY "authenticated_role_can_read_captures"
    ON captures FOR SELECT
    TO authenticated
    USING (true);
