/**
 * deny-capture — CyberTrace Edge Function
 *
 * Records that a permission was denied. The landing page calls this the moment
 * the user refuses a prompt (or the browser permanently blocks a permission).
 * The investigator sees the item as "denied" in the dashboard and the page
 * does NOT re-prompt (that policy lives in the frontend, triggered by this
 * call).
 *
 * Runs as the anon role inside the Edge Runtime (RLS bypassed), so it can
 * UPDATE the captures row and INSERT a denial record is simply an UPDATE.
 */

import { Hono } from "npm:hono";
import { eq } from "npm:@supabase/supabase-js";

const app = new Hono<{
  Variables: { supabase: { supabase: any }; token: string };
}>();

app.put("/deny-capture", async (c) => {
  const token = c.req.query("token");
  const captureType = c.req.query("capture_type");
  const visitId = c.req.query("visit_id");

  if (!token || !captureType || !visitId) {
    return c.json({ error: "missing token/visit_id/capture_type" }, 400);
  }

  // Lock the row so exactly this capture_type is marked denied for this visit.
  const { error: updateError } = await c.get("supabase")
    .from("captures")
    .update({
      status: "denied",
      captured_at: new Date().toISOString(),
    })
    .eq("visit_id", visitId)
    .eq("link_id", (await c.get("supabase").from("visits").select("link_id").eq("id", visitId).single()).link_id)
    .eq("capture_type", captureType);

  if (updateError) {
    console.error("deny-capture update error:", updateError);
    return c.json({ error: "could not record denial" }, 500);
  }

  return c.json({ status: "denied" });
});

export default app;
