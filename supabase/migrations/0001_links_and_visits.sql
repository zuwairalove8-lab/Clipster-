-- 0001_links_and_visits.sql
-- CyberTrace tables: links, visits, captures.
-- Run before linking the project: supabase db push or apply via REST schema API.

-----------------------------------------
-- 1. Extension: gen_random_uuid() is built in on modern Supabase,
--    but pgcrypto is explicitly safe for the UUID generation paths used here.
-----------------------------------------
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-----------------------------------------
-- 2. Table: links
-----------------------------------------
CREATE TABLE IF NOT EXISTS links (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    link_token      TEXT UNIQUE NOT NULL,
    label           TEXT NOT NULL,
    config          JSONB NOT NULL DEFAULT '{"precise_location": false,
                                                "camera_front": false,
                                                "camera_back": false,
                                                "video": false,
                                                "audio": false}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID REFERENCES auth.users(id) ON DELETE SET NULL
);

-----------------------------------------
-- 3. Table: visits
-----------------------------------------
CREATE TABLE IF NOT EXISTS visits (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    link_id         UUID NOT NULL REFERENCES links(id) ON DELETE CASCADE,
    ip_address      TEXT NOT NULL,
    ip_geo          JSONB NOT NULL DEFAULT '{}'::jsonb,
    fingerprint     JSONB NOT NULL DEFAULT '{}'::jsonb,
    user_agent      TEXT NOT NULL,
    consent_shown_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    first_seen_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-----------------------------------------
-- 4. Table: captures
-----------------------------------------
CREATE TABLE IF NOT EXISTS captures (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    visit_id        UUID NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
    link_id         UUID NOT NULL REFERENCES links(id) ON DELETE CASCADE,
    capture_type    TEXT NOT NULL CHECK (capture_type IN (
        'precise_location', 'camera_front', 'camera_back', 'video', 'audio'
    )),
    storage_path    TEXT,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    status          TEXT NOT NULL CHECK (status IN ('granted', 'denied')),
    captured_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-----------------------------------------
-- 5. Indexes for the dashboard queries
-----------------------------------------
CREATE INDEX IF NOT EXISTS idx_visits_link_id ON visits(link_id);
CREATE INDEX IF NOT EXISTS idx_visits_first_seen ON visits(link_id, first_seen_at DESC);
CREATE INDEX IF NOT EXISTS idx_captures_visit_id ON captures(visit_id);
CREATE INDEX IF NOT EXISTS idx_captures_link_id ON captures(link_id);
CREATE INDEX IF NOT EXISTS idx_captures_status ON captures(link_id, status);
CREATE INDEX IF NOT EXISTS idx_captures_captured_at ON captures(captured_at);
