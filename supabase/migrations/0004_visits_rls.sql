-- 0004_visits_rls.sql
-- RLS for visits.
-- Anon may only reach visits through resolve-link (Row-level security is
-- bypassed for Edge Function invocations, so anon cannot select visits
-- directly; this policy is a belt-and-braces guard).
-- Authenticated investigator may read all visits for the links they own.

ALTER TABLE visits ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "anon_may_select_visits" ON visits;
DROP POLICY IF EXISTS "authenticated_may_select_visits" ON visits;
DROP POLICY IF EXISTS "authenticated_may_read_visits" ON visits;

CREATE POLICY "anonymous_role_cannot_see_visits"
    ON visits FOR SELECT
    TO anon
    USING (false);

CREATE POLICY "authenticated_role_can_read_visits"
    ON visits FOR SELECT
    TO authenticated
    USING (true);
