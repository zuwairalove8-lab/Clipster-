/**
 * resolve-link — CyberTrace Edge Function
 *
 * Called by the landing page's /c/{link_token} page when it first loads.
 *
 * 1. Creates a `visits` row (anonymous role).
 * 2. Reads the config JSONB off the linked link (as the anon role).
 * 3. Performs IP geolocation server-side (ONLY the Edge Runtime, no browser key).
 * 4. Uses ip-api.com (free, no key) as the example geocoder. Swap the URL if you
 *    prefer another provider; the important part is that the API key never leaves
 *    the Edge Function.
 * 5. Returns { visit_id, config, consent_text } to the browser.
 *
 * Anonymous role rules: anon may only INSERT into visits here
 * (Edge Functions are invoked as the anon role and bypass RLS).
 */

import { Hono } from "npm:hono";
import { and, eq, sql } from "npm:@supabase/supabase-js";
import net from "node:net";

const app = new Hono<{
  Variables: { supabase: { supabase: any }; token: string };
}>();

// ---------------------------------------------------------------------------
// GET /resolve-link?token=<link_token>
//
// Example response:
// {
//   "visit_id": "uuid",
//   "config": { "precise_location": false, ... },
//   "consent_text": "..."
// }
// ---------------------------------------------------------------------------
app.get("/resolve-link", async (c) => {
  const token = c.req.query("token");
  if (!token) {
    return c.json({ error: "missing token" }, 400);
  }

  const { supabase, token: _t } = c.get("supabase");
  const supabaseClient = supabase;

  // 1. Resolve the link (authenticated via the GET request's anon privileges).
  //    The anon role can SELECT links (see supabase/migrations/0003_links_rls.sql).
  const { data: link, error: linkError } = await supabaseClient
    .from("links")
    .select("id, label, config")
    .eq("link_token", token)
    .single();

  if (linkError || !link) {
    // Still record a visit so we know the token was probed, but with empty config.
    const { data: visit } = await supabaseClient
      .from("visits")
      .insert({
        link_id: null,
        ip_address: c.req.header("x-forwarded-for") ?? "unknown",
        user_agent: c.req.header("user-agent") ?? "unknown",
        consent_shown_at: new Date().toISOString(),
        first_seen_at: new Date().toISOString(),
        last_seen_at: new Date().toISOString(),
      })
      .select("id")
      .single();

    return c.json({
      visit_id: visit?.id ?? null,
      config: {
        precise_location: false,
        camera_front: false,
        camera_back: false,
        video: false,
        audio: false,
      },
      consent_text: "",
      error: linkError
        ? "link not found or malformed"
        : "link not found",
      status: linkError ? 404 : 404,
    });
  }

  // 2. Create the visits row (anon role, RLS bypassed by Edge Function context).
  const { data: visit, error: visitError } = await supabaseClient
    .from("visits")
    .insert({
      link_id: link.id,
      ip_address: c.req.header("x-forwarded-for") ?? "unknown",
      ip_geo: {},
      fingerprint: {},
      user_agent: c.req.header("user-agent") ?? "unknown",
      consent_shown_at: new Date().toISOString(),
      first_seen_at: new Date().toISOString(),
      last_seen_at: new Date().toISOString(),
    })
    .select("id")
    .single();

  if (visitError) {
    console.error("resolve-link insert visits error:", visitError);
    return c.json({ error: "could not create visit" }, 500);
  }

  // 3. Server-side IP geolocation (only, never expose a key to the browser).
  let ipGeo = {};
  try {
    const targetIp =
      c.req.header("x-forwarded-for")?.split(",")[0]?.trim() ??
      c.req.header("x-real-ip") ??
      "127.0.0.1";

    const geoRes = await fetch(`https://ipapi.co/${targetIp}/json/`);
    if (geoRes.ok) {
      const geo = await geoRes.json();
      ipGeo = {
        city: geo.city ?? null,
        region: geo.region ?? null,
        country: geo.country_name ?? geo.country ?? null,
        ISP: geo.org ?? null,
      };
    }
  } catch (e) {
    console.error("resolve-link geolocation fallback:", e);
    // Leave ip_geo as {} — the visit still exists and is fine.
  }

  // 3b. Update the visits row with the geo we just fetched (anon role is fine).
  await supabaseClient
    .from("visits")
    .update({
      ip_geo: ipGeo,
      last_seen_at: new Date().toISOString(),
    })
    .eq("id", visit.id);

  // 4. Build plain-language consent text from the link config (exactly the
  //    items that are enabled — nothing more).
  const cfg = link.config ?? {};
  const enabled = [];
  if (cfg.precise_location) enabled.push("your precise GPS location, on request");
  if (cfg.camera_front) enabled.push("a photo from your front camera");
  if (cfg.camera_back) enabled.push("a photo from your back camera");
  if (cfg.video) enabled.push("a short video clip from your camera");
  if (cfg.audio) enabled.push("a short audio clip from your microphone");
  const consentItems = enabled.length
    ? enabled.map((s, i) => `${i + 1}. ${s}`).join("\n")
    : "No captures enabled — only standard connection data was collected.";

  const consentText = [
    `You are visiting link: ${link.label}`,
    "When you continue, this investigation link will collect:",
    consentItems,
    "",
    "Data collected silently on page load (always, no toggle):",
    "• your public IP address",
    "• coarse location (city/region/country) derived from that IP",
    "• your device and browser fingerprint",
    "Your browser will never be asked to do anything without a clear,",
    "separate prompt, and you can refuse any single item without affecting the",
    "others.",
  ].join("\n");

  // 5. Return everything the browser needs.
  return c.json({
    visit_id: visit.id,
    config: {
      precise_location: Boolean(cfg.precise_location),
      camera_front: Boolean(cfg.camera_front),
      camera_back: Boolean(cfg.camera_back),
      video: Boolean(cfg.video),
      audio: Boolean(cfg.audio),
    },
    consent_text: consentText,
  });
});

export default app;
