# CyberTrace

Link-based evidence collection for **authorized** investigations: a consent-gated
capture page, a Supabase backend, and an Android dashboard that streams results in
real time and exports them.

> **Authorized use only.** CyberTrace is for investigations you are legally allowed to
> run — red-team engagements, fraud cases, your own accounts. You are responsible for
> the disclosure, consent and retention rules that apply where you operate. The tool
> does not read browser history, install software, or capture anything a browser
> permission prompt has not approved.

---

## What it does

1. **Landing page** (`src/`) — the recipient opens `…/c/{token}` in any browser.
   The page records a normal web visit (IP, coarse IP-derived location, device/browser
   details), then shows a plain-language **consent screen** listing exactly what this
   link may ask for. Nothing beyond the visit is requested until the recipient taps
   **Continue** and then taps each item's own button. A refusal is recorded once and
   never re-prompted.
2. **Supabase backend** (`supabase/`) — Postgres tables (`links`, `visits`, `captures`),
   a private Storage bucket, RLS, Realtime, and three Edge Functions.
3. **Android dashboard** (`android/`) — Kotlin + Jetpack Compose. Sign in, create links
   (with QR / copy / share), watch visits and captures arrive live, filter, inspect
   details, and export a ZIP.

### Capability matrix, as built

| Data | Silent on page open? | Mechanism |
| --- | --- | --- |
| Public IP address | Yes | `x-forwarded-for`, read server-side in `resolve-link` |
| Coarse location (city, region, country, ISP) | Yes | Server-side IP lookup (`ipwho.is`, `ip-api.com` fallback). Never called GPS. |
| Device/browser fingerprint | Yes | UA, platform, screen, viewport, DPR, timezone, language, memory, cores, touch, canvas hash, WebGL vendor/renderer |
| Precise GPS | No — tap + permission | `navigator.geolocation`, one request per link |
| Front / back camera still | No — tap + permission | `getUserMedia` → canvas → JPEG |
| Video clip (6 s) | No — tap + permission | `MediaRecorder` on the camera stream |
| Audio clip (6 s) | No — tap + permission | `MediaRecorder` on the microphone stream |
| Browser history | **Not built** | No web API exists |
| Silent camera / mic / GPS | **Not built** | Browsers block it; nothing here tries |

Unsupported items are **hidden** per browser rather than failing, and the consent
screen marks them as unavailable.

---

## Repository layout

```
src/                     capture page + marketing page (Vite + React)
public/404.html          routes /c/{token} → index.html?t={token}
vite.config.ts           Vite + React build → dist/
android/                 Kotlin + Compose dashboard
supabase/migrations/     schema, RLS, bucket, realtime publication
supabase/functions/      resolve-link, upload-capture, deny-capture
.github/workflows/       android-build.yml
```

---

## Backend

Live project: **`yrmdsvkxpyejpmphfstb`** (`https://yrmdsvkxpyejpmphfstb.supabase.co`).

### Schema

`links(id, link_token unique, label, config jsonb, created_at, created_by → auth.users)`
`visits(id, link_id, ip_address, ip_geo jsonb, fingerprint jsonb, user_agent, consent_shown_at, first_seen_at, last_seen_at)`
`captures(id, visit_id, link_id, capture_type, storage_path, payload jsonb, status granted|denied, captured_at)`

`config` holds the five per-link booleans: `precise_location`, `camera_front`,
`camera_back`, `video`, `audio`. IP data and fingerprint are always recorded and are
not toggleable.

### Applying it

```bash
supabase link --project-ref yrmdsvkxpyejpmphfstb
supabase db push                                    # supabase/migrations/0001_init.sql
supabase functions deploy resolve-link upload-capture deny-capture --no-verify-jwt
```

`--no-verify-jwt` matters: the browser is anonymous, so the three functions are
deployed with `verify_jwt = false`. They authenticate each request with the
`link_token` only, and hold the service-role key server-side.

Without the CLI, apply the same SQL through the Management API:

```bash
curl -X POST "https://api.supabase.com/v1/projects/yrmdsvkxpyejpmphfstb/database/query" \
  -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$(jq -Rs '{query: .}' supabase/migrations/0001_init.sql)"
```

### Security model

- `anon` has **no grants** on `links`, `visits`, `captures` — a direct PostgREST call
  returns `42501 permission denied`. The only anonymous surface is the three Edge
  Functions.
- `authenticated` sees and writes only its own rows (`created_by = auth.uid()` on links,
  and visits/captures joined to a link it owns).
- Storage bucket `captures` is **private**. Files live at
  `{link_id}/{visit_id}/{capture_type}/{uuid}.{ext}` and the dashboard reads them via
  short-lived signed URLs, scoped by a `storage.objects` policy.
- The app ships only the **publishable** anon key. The `service_role` key never leaves
  the Edge Functions.
- Realtime is enabled on `visits` and `captures`.
- Email confirmation is currently **off** for this project so a new investigator can
  sign in immediately. Re-enable it in Authentication → Providers before real use.

---

## Capture page (hosting)

The page is a small Vite + React app served by Freebuff hosting, which only builds
recognised frameworks. `vite build` emits `dist/` (gzip ≈ 75 kB of JS).

Live at **https://mtn.freebuff.app**, so a link is `https://mtn.freebuff.app/c/{token}`.

Vite and `@vitejs/plugin-react` are in `dependencies`, not `devDependencies`: hosting
installs production dependencies only, and with them listed as dev dependencies the
build dies with `sh: 1: vite: not found`.

```
freebuff-preview set-install "bun install"
freebuff-preview set-build "bun run build"
freebuff-preview set "bunx vite --host 0.0.0.0 --port ${PORT:-5173}" 5173
freebuff-preview start      # preview
freebuff-deploy check       # what production will run
```

A link looks like `https://<deployment-host>/c/{token}`. `src/App.tsx` reads the token from
`/c/{token}` or from `?t={token}`, and `public/404.html` rewrites the path form on hosts
that serve a real 404, so the same URL shape works on any static host. When the host
changes, update `PAGES_BASE` in
`android/app/src/main/java/com/cybertrace/android/data/Config.kt`.

Flow on load:

1. `resolve-link` records the visit and returns `visit_id`, `config`, `label` and the
   consent text.
2. Consent screen renders that text plus a checklist of the enabled items.
3. `Continue` starts the sequence; each item gets its own button and its own permission
   prompt.
4. Grant → capture → **upload immediately** (direct base64 for small files, signed
   upload URL for large ones) → next.
5. Deny → `deny-capture` → never asked again → next.
6. A progress bar shows `Step n of m`; a neutral closing screen ends the flow.

iOS Safari loses the camera stream when the tab loses focus; the recorder closes out
cleanly on `track.ended` with whatever it already captured instead of hanging.

No trackers, no third-party cookies, no external requests beyond the project's own
Supabase endpoints. Plain HTML/CSS/JS, no build step.

---

## Android dashboard

Kotlin 2.1.20 · AGP 8.7.3 · Gradle 8.9 · Compose BOM 2024.12.01 · Material 3 ·
supabase-kt 3.1.4 (Postgrest, Auth, Storage, Realtime) · Ktor CIO 3.1.2 · minSdk 26 ·
targetSdk 35 · **internet permission only**.

Screens: **Auth** (email/password, session persisted and refreshed on launch) ·
**Links** (newest first, enabled items as chips, visit/capture counts) · **Create**
(label, five toggles, live consent preview, compliance banner, then QR + copy + share) ·
**Live feed** (Realtime on `visits` + `captures`, filters by link / type / status /
window, image thumbnails) · **Detail** (media viewer, full fingerprint and IP-geo
tables, raw payload, coordinates) · **Export** · **Settings** (connection status, app
version, sign out).

### Export

Pick a link and a window (7 / 30 / 90 days, or everything), then export a ZIP with
`summary.json`, `visits.json`, `captures.json` and every granted media file under
`media/`.

- Android 10+: `Download/CyberTrace/cybertrace-<label>-<timestamp>.zip` via MediaStore,
  no storage permission needed.
- Android 8–9: the app-specific external directory (`Android/data/…/files/exports/`),
  because `MediaStore.Downloads` does not exist before API 29 and the app deliberately
  does not request `WRITE_EXTERNAL_STORAGE`.

### Building locally

```bash
cd android
./gradlew assembleDebug        # wrapper: Gradle 8.9, official gradlew + wrapper jar
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`.

---

## CI/CD

`.github/workflows/android-build.yml` — on push to `main`, PRs, and manual dispatch:
JDK 17, Gradle 8.9 via `gradle/actions/setup-gradle`, Android SDK 35 + build-tools 35,
`gradle assembleDebug --stacktrace`, then uploads the APK as **`cybertrace-dashboard-apk`**
(30-day retention) on every run and the reports as `build-logs` on failure.

The capture page is hosted by Freebuff (`vite build` → `dist/`), not GitHub Pages: Pages was
never enabled and the API refuses to create the site with an app token.

The build deliberately uses the Gradle *installed by the action* rather than
`./gradlew`, so a wrapper download can never take the build down. The committed wrapper
is the official Gradle 8.9 one (`java -classpath gradle/wrapper/gradle-wrapper.jar
org.gradle.wrapper.GradleWrapperMain`) for local use.

---

## Deliberately not built

- **Browser history** — no web API exposes it. Absent from the schema, UI and docs.
- **Silent camera / microphone / GPS** — browsers require a user gesture and a
  permission prompt. Nothing here tries to work around that, hide prompts, or batch
  them.
- **Embedded map tiles** — the detail view deep-links to the device's maps app instead
  (`geo:` intent). An in-app map would need a Maps SDK key, which is out of scope for a
  build that must compile in CI with no secrets.

## Notes and limits

- IP geolocation is **approximate and city-level**; it is never presented as GPS.
  Precise coordinates only exist when a GPS capture was actually granted.
- Direct uploads are capped at 5 MB per item; larger files go through a signed upload
  URL. Clips are 6 seconds at ~700 kbps so they stay small.
- The dashboard reloads all rows on a realtime event rather than patching individual
  cards. Simple and correct for an investigator's own dataset; it is the first thing to
  change if a case ever holds tens of thousands of rows.
