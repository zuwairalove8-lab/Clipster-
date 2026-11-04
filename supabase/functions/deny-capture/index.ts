// deny-capture
// Records that the target was shown a permission prompt and refused it, so the
// investigator can tell "refused" apart from "never asked".
// The page must never re-prompt after this.

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Max-Age": "86400",
};

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const TYPES = ["precise_location", "camera_front", "camera_back", "video", "audio"];

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json" },
  });

const rest = (path: string, init: RequestInit = {}) =>
  fetch(`${SUPABASE_URL}/rest/v1/${path}`, {
    ...init,
    headers: {
      apikey: SERVICE_KEY,
      Authorization: `Bearer ${SERVICE_KEY}`,
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
    },
  });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  let body: { token?: string; visit_id?: string; capture_type?: string; reason?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  const token = (body.token ?? "").trim();
  const visitId = (body.visit_id ?? "").trim();
  const type = body.capture_type ?? "";

  if (!token) return json({ error: "invalid_token" }, 400);
  if (!visitId) return json({ error: "invalid_visit" }, 400);
  if (!TYPES.includes(type)) return json({ error: "invalid_capture_type" }, 400);

  const visitRes = await rest(
    `visits?select=id,link_id,links!inner(link_token)&id=eq.${encodeURIComponent(visitId)}&limit=1`,
  );
  if (!visitRes.ok) return json({ error: "visit_lookup_failed" }, 500);

  const visits = await visitRes.json();
  if (!Array.isArray(visits) || visits.length === 0) {
    return json({ error: "visit_not_found" }, 404);
  }

  const visit = visits[0];
  if (visit.links?.link_token !== token) return json({ error: "token_mismatch" }, 403);

  const ins = await rest("captures", {
    method: "POST",
    headers: { Prefer: "return=representation" },
    body: JSON.stringify({
      visit_id: visitId,
      link_id: visit.link_id,
      capture_type: type,
      status: "denied",
      payload: { reason: body.reason ?? "user_denied" },
      captured_at: new Date().toISOString(),
    }),
  });

  if (!ins.ok) return json({ error: "insert_failed", detail: await ins.text() }, 500);
  return json({ status: "denied" });
});
