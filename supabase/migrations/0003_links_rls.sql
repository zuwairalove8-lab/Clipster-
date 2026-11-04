-- 0003_links_rls.sql
-- RLS for links: anonymous (visitor) must not touch links directly;
-- only the authenticated investigator can insert/read links.

ALTER TABLE links ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "anon_may_select_links" ON links;
DROP POLICY IF EXISTS "authenticated_may_select_links" ON links;
DROP POLICY IF EXISTS "anon_may_insert_links" ON links;
DROP POLICY IF EXISTS "authenticated_may_insert_links" ON links;
DROP POLICY IF EXISTS "authenticated_may_update_links" ON links;
DROP POLICY IF EXISTS "authenticated_may_delete_links" ON links;

CREATE POLICY "anonymous_role_cannot_see_links"
    ON links FOR SELECT
    TO anon
    USING (false);

CREATE POLICY "authenticated_role_can_read_links"
    ON links FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "authenticated_role_can_insert_links"
    ON links FOR INSERT
    TO authenticated
    WITH CHECK (true);

CREATE POLICY "authenticated_role_can_update_links"
    ON links FOR UPDATE
    TO authenticated
    USING (true)
    WITH CHECK (true);

CREATE POLICY "authenticated_role_can_delete_links"
    ON links FOR DELETE
    TO authenticated
    USING (true);
