# Supabase Setup — CyberTrace

This project uses a single Supabase workspace. Everything below is applied with the
`supabase` CLI or the `supabase` REST API against the workspace identified by the
project ID below. No local PostgreSQL copy is required: the Edge Functions run on
Supabase's sandbox-managed Edge Runtime, and Storage/RLS are configured in SQL.

Project ID: `yrmdsvkxpyejpmphfstb`
Authenticator bearer token: set in the Supabase dashboard (`Settings → API`).

## Apply the schema

```bash
# 1. authenticate against the workspace
supabase link --project-ref yrmdsvkxpyejpmphfstb
supabase db pull                     # pulls the current remote state
supabase db push                     # pushes local changes to the remote DB
supabase functions deploy resolve-link upload-capture deny-capture
```

If the CLI cannot reach the workspace (firewall/2FA), fall back to the REST API:

```bash
# SQL migrations
curl -X POST https://api.supabase.com/projects/yrmdsvkxpyejpmphfstb/rest/v1/schema \
  -H "Authorization: Bearer <BEARER>" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "sql@supabase/migrations/0001_links_and_visits.sql"

# Storage bucket
curl -X POST https://api.supabase.com/projects/yrmdsvkxpyejpmphfstb/rest/v1/storage/buckets \
  -H "Authorization: Bearer <BEARER>" \
  -H "Content-Type: application/json" \
  -d '{"name":"captures","public":false,"file_size_limit":104857600}'

# Edge Functions
curl -X POST https://api.supabase.com/projects/yrmdsvkxpyejpmphfstb/rest/v1/functions \
  -H "Authorization: Bearer <BEARER>" \
  -H "Content-Type: application/json" \
  -d '{"name":"resolve-link","replace":true,"importing":{"import":[]},"dependson":["_extensions/supabase_functions"]}'
```

## Edge Runtime environment

`resolve-link`, `upload-capture`, and `deny-capture` run in Supabase's Edge Runtime.
They only use built-in modules (`crypto`, `net`, `fetch`), so no `SUPABASE_URL` /
`SUPABASE_ANON_KEY` need be embedded. They read the token from the `token`
query parameter and, for `upload-capture`, the `Authorization: Bearer`
annotation header that Storage intercepts and injects automatically.

## Storage layout

```
captures/{link_id}/{visit_id}/{capture_type}/{uuid}.{ext}
```

## Key design notes

- All tables are locked behind RLS: the `anon` role can **only** call the three Edge
  Functions. The `authenticated` role (investigator) inserts into `links` and
  selects from `links`, `visits`, `captures`.
- The three Edge Functions are invisible to the browser: `resolve-link` returns
  `visit_id`, `config`, and `consent_text`; `upload-capture` returns a short-lived
  signed URL; `deny-capture` records a refusal so an investigator can see a prompt
  was sent and declined.
- IP-based coarse location is performed **server-side** inside `resolve-link`.
  Never expose a geocoding/geo service API key to the browser.
