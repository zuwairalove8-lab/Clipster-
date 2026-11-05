/* Talk to this project's Edge Functions.
 *
 * The anon key is a publishable key: it ships in the page on purpose and grants
 * nothing on its own, because every table is behind RLS. The service_role key
 * lives only inside the functions.
 */

export const SUPABASE_URL = "https://yrmdsvkxpyejpmphfstb.supabase.co";
export const ANON_KEY =
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlybWRzdmt4cHllanBtcGhmc3RiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM3NDMxMjUsImV4cCI6MjEwOTMxOTEyNX0.g8VO892KEg5n9Lc3bjYbDCxEti-WI6FqKCSoYvnaIvg";

/** Base64 above this goes through a signed upload URL instead. */
export const DIRECT_LIMIT = 4 * 1024 * 1024;

export type StepKey =
  | "precise_location"
  | "camera_front"
  | "camera_back"
  | "video"
  | "audio";

export interface VisitTicket {
  token: string;
  visitId: string;
  label: string;
  consentText: string;
  config: Record<string, boolean>;
}

export interface ResolveResponse {
  visit_id?: string;
  label?: string;
  consent_text?: string;
  config?: Record<string, boolean>;
}

export class ApiError extends Error {
  status?: number;

  constructor(message: string, status?: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

export async function post<T>(fn: string, body: unknown, timeoutMs = 25000): Promise<T> {
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), timeoutMs);
  try {
    const res = await fetch(`${SUPABASE_URL}/functions/v1/${fn}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        apikey: ANON_KEY,
        Authorization: `Bearer ${ANON_KEY}`,
      },
      body: JSON.stringify(body),
      signal: ctl.signal,
      mode: "cors",
      credentials: "omit",
    });

    const text = await res.text();
    let data: unknown = null;
    try {
      data = JSON.parse(text);
    } catch {
      data = { raw: text };
    }
    if (!res.ok) {
      const message =
        (data as { error?: string } | null)?.error || `HTTP ${res.status}`;
      throw new ApiError(message, res.status);
    }
    return data as T;
  } finally {
    clearTimeout(timer);
  }
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result);
      resolve(result.slice(result.indexOf(",") + 1));
    };
    reader.onerror = () => reject(new Error("could not read file"));
    reader.readAsDataURL(blob);
  });
}

/** Uploads immediately: direct base64 for small files, signed URL for large. */
export async function sendGranted(
  ticket: VisitTicket,
  type: StepKey,
  blob: Blob | null,
  payload: Record<string, unknown>,
): Promise<void> {
  const base: Record<string, unknown> = {
    token: ticket.token,
    visit_id: ticket.visitId,
    capture_type: type,
    status: "granted",
    payload,
  };

  if (!blob) {
    await post("upload-capture", base);
    return;
  }

  const contentType = blob.type || "application/octet-stream";
  base.content_type = contentType;

  if (blob.size <= DIRECT_LIMIT) {
    base.data_base64 = await blobToBase64(blob);
    await post("upload-capture", base, 60000);
    return;
  }

  const signed = await post<{ upload_url?: string }>("upload-capture", base, 30000);
  if (!signed.upload_url) return;

  const res = await fetch(signed.upload_url, {
    method: "PUT",
    headers: { "Content-Type": contentType, apikey: ANON_KEY },
    body: blob,
  });
  if (!res.ok) throw new ApiError(`upload failed (${res.status})`, res.status);
}

/** A lost denial record must never block the recipient. */
export async function recordDenial(
  ticket: VisitTicket,
  type: StepKey,
  reason: string,
): Promise<void> {
  try {
    await post("deny-capture", {
      token: ticket.token,
      visit_id: ticket.visitId,
      capture_type: type,
      reason,
    });
  } catch {
    /* ignored on purpose */
  }
}
