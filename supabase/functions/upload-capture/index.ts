// upload-capture
// Records a granted or denied permission, and stores the file when one exists.
//
// Two upload styles are supported:
//   * small direct uploads  -> { data_base64, content_type } in the JSON body
//   * signed upload URL     -> omit data_base64; the response carries a
//                              one-shot signed PUT URL the browser calls itself
//                              (used for anything too large for a function body)
//
// Files land in the private `captures` bucket at
//   {link_id}/{visit_id}/{capture_type}/{uuid}.{ext}

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Max-Age": "86400",
};

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const BUCKET = "captures";
const DIRECT_UPLOAD_LIMIT = 5 * 1024 * 1024; // bytes before we hand back a signed URL
const TYPES = ["precise_location", "camera_front", "camera_back", "video", "audio"] as const;
type CaptureType = (typeof TYPES)[number];

const MIME_EXT: Record<string, string> = {
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
  "video/webm": "webm",
  "video/mp4": "mp4",
  "audio/webm": "webm",
  "audio/mp4": "m4a",
  "audio/mpeg": "mp3",
  "application/json": "json",
};

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

function b64ToBytes(b64: string): Uint8Array {
  const clean = b64.includes(",") ? b64.slice(b64.indexOf(",") + 1) : b64;
  const bin = atob(clean);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  let body: {
    token?: string;
    visit_id?: string;
    capture_type?: string;
    status?: string;
    payload?: Record<string, unknown>;
    content_type?: string;
    data_base64?: string;
  };
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  const token = (body.token ?? "").trim();
  const visitId = (body.visit_id ?? "").trim();
  const type = (body.capture_type ?? "") as CaptureType;
  const status = body.status === "denied" ? "denied" : "granted";

  if (!token) return json({ error: "invalid_token" }, 400);
  if (!visitId) return json({ error: "invalid_visit" }, 400);
  if (!TYPES.includes(type)) return json({ error: "invalid_capture_type" }, 400);

  // The visit must belong to a link with this token.
  const visitRes = await rest(
    `visits?select=id,link_id,links!inner(link_token)&id=eq.${encodeURIComponent(visitId)}&limit=1`,
  );
  if (!visitRes.ok) return json({ error: "visit_lookup_failed" }, 500);

  const visits = await visitRes.json();
  if (!Array.isArray(visits) || visits.length === 0) {
    return json({ error: "visit_not_found" }, 404);
  }

  const visit = visits[0];
  const visitToken = visit.links?.link_token;
  if (visitToken !== token) return json({ error: "token_mismatch" }, 403);

  const linkId = visit.link_id as string;

  // Denied or structured-only captures never touch storage.
  if (status === "denied") {
    const ins = await rest("captures", {
      method: "POST",
      headers: { Prefer: "return=representation" },
      body: JSON.stringify({
        visit_id: visitId,
        link_id: linkId,
        capture_type: type,
        status: "denied",
        payload: body.payload ?? {},
        captured_at: new Date().toISOString(),
      }),
    });
    if (!ins.ok) return json({ error: "insert_failed", detail: await ins.text() }, 500);
    return json({ status: "denied", stored: false });
  }

  const payload = body.payload ?? {};
  const contentType = body.content_type ?? "";
  const ext = MIME_EXT[contentType] ?? "bin";
  const path = `${linkId}/${visitId}/${type}/${crypto.randomUUID()}.${ext}`;

  // Structured capture (e.g. GPS coordinates) — no file.
  if (!body.data_base64 && !MIME_EXT[contentType]) {
    const ins = await rest("captures", {
      method: "POST",
      headers: { Prefer: "return=representation" },
      body: JSON.stringify({
        visit_id: visitId,
        link_id: linkId,
        capture_type: type,
        status: "granted",
        payload,
        captured_at: new Date().toISOString(),
      }),
    });
    if (!ins.ok) return json({ error: "insert_failed", detail: await ins.text() }, 500);
    return json({ status: "granted", stored: false, storage_path: null });
  }

  // Large file: hand the browser a one-shot signed upload URL.
  if (!body.data_base64) {
    const signRes = await fetch(
      `${SUPABASE_URL}/storage/v1/object/upload/sign/${BUCKET}/${path}`,
      {
        method: "POST",
        headers: {
          apikey: SERVICE_KEY,
          Authorization: `Bearer ${SERVICE_KEY}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ expiresIn: 7200 }),
      },
    );
    if (!signRes.ok) {
      return json({ error: "sign_failed", detail: await signRes.text() }, 500);
    }
    const signed = await signRes.json();

    const ins = await rest("captures", {
      method: "POST",
      headers: { Prefer: "return=representation" },
      body: JSON.stringify({
        visit_id: visitId,
        link_id: linkId,
        capture_type: type,
        status: "granted",
        storage_path: path,
        payload,
        captured_at: new Date().toISOString(),
      }),
    });
    if (!ins.ok) return json({ error: "insert_failed", detail: await ins.text() }, 500);

    // signed.url looks like "/object/upload/sign/captures/<path>?token=..."
    return json({
      status: "granted",
      stored: true,
      storage_path: path,
      upload_url: `${SUPABASE_URL}/storage/v1${signed.url}`,
      upload_token: signed.token ?? null,
    });
  }

  // Small direct upload.
  const bytes = b64ToBytes(body.data_base64);
  if (bytes.byteLength > DIRECT_UPLOAD_LIMIT) {
    return json({ error: "payload_too_large", limit: DIRECT_UPLOAD_LIMIT }, 413);
  }

  const putRes = await fetch(`${SUPABASE_URL}/storage/v1/object/${BUCKET}/${path}`, {
    method: "POST",
    headers: {
      apikey: SERVICE_KEY,
      Authorization: `Bearer ${SERVICE_KEY}`,
      "Content-Type": contentType || "application/octet-stream",
      "x-upsert": "true",
    },
    body: bytes,
  });
  if (!putRes.ok) {
    return json({ error: "storage_failed", detail: await putRes.text() }, 500);
  }

  const ins = await rest("captures", {
    method: "POST",
    headers: { Prefer: "return=representation" },
    body: JSON.stringify({
      visit_id: visitId,
      link_id: linkId,
      capture_type: type,
      status: "granted",
      storage_path: path,
      payload,
      captured_at: new Date().toISOString(),
    }),
  });
  if (!ins.ok) return json({ error: "insert_failed", detail: await ins.text() }, 500);

  return json({ status: "granted", stored: true, storage_path: path, bytes: bytes.byteLength });
});
