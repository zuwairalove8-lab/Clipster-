// resolve-link
// Opens a tracking link: records the visit (IP, server-side IP geolocation,
// client fingerprint) and returns what the page is allowed to ask for.
//
// verify_jwt = false: called by an anonymous browser. The link_token is the
// only credential, and RLS keeps the anon role away from the tables directly.

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Max-Age": "86400",
};

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

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

type Config = {
  precise_location: boolean;
  camera_front: boolean;
  camera_back: boolean;
  video: boolean;
  audio: boolean;
};

const EMPTY_CONFIG: Config = {
  precise_location: false,
  camera_front: false,
  camera_back: false,
  video: false,
  audio: false,
};

function clientIp(req: Request): string {
  const xff = req.headers.get("x-forwarded-for") ?? "";
  const first = xff.split(",")[0].trim();
  return (
    first ||
    req.headers.get("x-real-ip") ||
    req.headers.get("cf-connecting-ip") ||
    ""
  );
}

function isPrivateIp(ip: string): boolean {
  if (!ip) return true;
  if (ip.startsWith("10.") || ip.startsWith("127.") || ip.startsWith("192.168.")) return true;
  if (/^172\.(1[6-9]|2\d|3[01])\./.test(ip)) return true;
  if (ip.startsWith("169.254.") || ip === "::1") return true;
  return false;
}

// Coarse location derived from the IP address, never from the device.
// Never described as GPS anywhere in the UI.
async function ipGeo(ip: string): Promise<Record<string, unknown>> {
  if (isPrivateIp(ip)) return { source: "unavailable:private-ip" };

  try {
    const r = await fetch(
      `https://ipwho.is/${encodeURIComponent(ip)}?fields=success,city,region,country,country_code,connection,latitude,longitude,timezone`,
      { signal: AbortSignal.timeout(3500) },
    );
    if (r.ok) {
      const d = await r.json();
      if (d?.success !== false) {
        return {
          source: "ipwho.is",
          city: d.city ?? null,
          region: d.region ?? null,
          country: d.country ?? null,
          country_code: d.country_code ?? null,
          isp: d.connection?.isp ?? null,
          org: d.connection?.org ?? null,
          latitude: d.latitude ?? null,
          longitude: d.longitude ?? null,
          timezone: d.timezone?.id ?? null,
        };
      }
    }
  } catch (_) { /* fall through */ }

  try {
    const r = await fetch(
      `http://ip-api.com/json/${encodeURIComponent(ip)}?fields=status,country,countryCode,regionName,city,isp,org,lat,lon,timezone`,
      { signal: AbortSignal.timeout(3500) },
    );
    if (r.ok) {
      const d = await r.json();
      if (d?.status === "success") {
        return {
          source: "ip-api.com",
          city: d.city ?? null,
          region: d.regionName ?? null,
          country: d.country ?? null,
          country_code: d.countryCode ?? null,
          isp: d.isp ?? null,
          org: d.org ?? null,
          latitude: d.lat ?? null,
          longitude: d.lon ?? null,
          timezone: d.timezone ?? null,
          approximate: true,
        };
      }
    }
  } catch (_) { /* ignore */ }

  return { source: "unavailable" };
}

function consentText(label: string, cfg: Config): string {
  const items: string[] = [];
  if (cfg.precise_location) items.push("your precise GPS coordinates");
  if (cfg.camera_front) items.push("one photo from your front camera");
  if (cfg.camera_back) items.push("one photo from your back camera");
  if (cfg.video) items.push("a short video clip (about 6 seconds)");
  if (cfg.audio) items.push("a short audio clip (about 6 seconds)");

  const ask = items.length
    ? `You will then be asked, one at a time and only after you tap a button:\n` +
      items.map((i) => `• ${i}`).join("\n") +
      `\n\nYou can decline any of these. Declining one does not affect the others.`
    : `No camera, microphone or location access is enabled for this link, so you will not be asked for any.`;

  return (
    `This page is part of "${label}", an investigation carried out with CyberTrace.\n\n` +
    `When this page opened, the following was recorded automatically, the same way any website records a visit:\n` +
    `• the public IP address you are connecting from\n` +
    `• approximate location derived from that IP address (city and country level — not GPS)\n` +
    `• technical details about your browser and device\n\n` +
    ask
  );
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  let body: { token?: string; fingerprint?: unknown; user_agent?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  const token = (body.token ?? "").trim();
  if (!token || token.length > 64) return json({ error: "invalid_token" }, 400);

  const linkRes = await rest(
    `links?select=id,label,config&link_token=eq.${encodeURIComponent(token)}&limit=1`,
  );
  if (!linkRes.ok) return json({ error: "lookup_failed" }, 500);

  const links = await linkRes.json();
  if (!Array.isArray(links) || links.length === 0) {
    return json({ error: "link_not_found" }, 404);
  }

  const link = links[0];
  const config: Config = { ...EMPTY_CONFIG, ...(link.config ?? {}) };

  const ip = clientIp(req);
  const geo = await ipGeo(ip);

  const visitRes = await rest("visits", {
    method: "POST",
    headers: { Prefer: "return=representation" },
    body: JSON.stringify({
      link_id: link.id,
      ip_address: ip,
      ip_geo: geo,
      fingerprint: body.fingerprint ?? {},
      user_agent: body.user_agent ?? req.headers.get("user-agent") ?? "",
      consent_shown_at: new Date().toISOString(),
      first_seen_at: new Date().toISOString(),
      last_seen_at: new Date().toISOString(),
    }),
  });

  if (!visitRes.ok) {
    return json({ error: "visit_insert_failed", detail: await visitRes.text() }, 500);
  }

  const visits = await visitRes.json();
  const visit = Array.isArray(visits) ? visits[0] : visits;

  return json({
    visit_id: visit?.id ?? null,
    link_id: link.id,
    label: link.label,
    config,
    consent_text: consentText(link.label, config),
  });
});
