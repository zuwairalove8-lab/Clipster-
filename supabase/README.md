# Backend notes

Project: `yrmdsvkxpyejpmphfstb` → `https://yrmdsvkxpyejpmphfstb.supabase.co`

## Objects

| Object | Purpose |
| --- | --- |
| `links` | one row per generated tracking link; `config` jsonb holds the five toggles |
| `visits` | one row per link open; IP, `ip_geo`, `fingerprint`, consent timestamp |
| `captures` | one row per item asked for; `status` is `granted` or `denied` |
| `storage.buckets/captures` | private media bucket, `{link_id}/{visit_id}/{type}/{uuid}.{ext}` |
| `resolve-link` | creates the visit, does the IP lookup server-side, returns consent text |
| `upload-capture` | validates token+visit, records the row, stores direct or signed uploads |
| `deny-capture` | records a refusal so it is never re-prompted |

## Rules baked into the SQL

- anon has no grants on the three tables — direct PostgREST calls fail with `42501`.
- authenticated reads/writes only its own links, and only the visits/captures of those links.
- `storage.objects` read is scoped to the caller's own link folders.
- `visits` and `captures` are in the `supabase_realtime` publication.

## Deploying the functions

```bash
supabase functions deploy resolve-link upload-capture deny-capture --no-verify-jwt
```

`verify_jwt` must be **false**: the browser calling them is anonymous. They authenticate
with the `link_token` and use the service-role key server-side.

## Environment inside the functions

Supabase injects `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` automatically. The
functions import nothing beyond Deno built-ins, so there is no import map and no
bundling step.

## Re-applying the schema

`supabase/migrations/0001_init.sql` is idempotent (`create table if not exists`,
`drop policy if exists` before each `create policy`, guarded publication inserts), so it
can be re-run against an existing project without data loss. It does **not** drop tables.
