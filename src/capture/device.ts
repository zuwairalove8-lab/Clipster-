/* Everything that touches the device, kept apart from the flow so the consent
 * rules stay readable. Nothing here runs on load.
 */

export type PreviewKind = "image" | "video" | "audio" | null;

export interface Capture {
  blob: Blob | null;
  preview: PreviewKind;
  info: Record<string, unknown>;
}

export interface Abilities {
  precise_location: boolean;
  camera_front: boolean;
  camera_back: boolean;
  video: boolean;
  audio: boolean;
}

function fnv(str: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return ("00000000" + h.toString(16)).slice(-8);
}

function canvasHash(): string | null {
  try {
    const canvas = document.createElement("canvas");
    canvas.width = 240;
    canvas.height = 60;
    const ctx = canvas.getContext("2d");
    if (!ctx) return null;
    ctx.textBaseline = "top";
    ctx.font = "16px 'Arial'";
    ctx.fillStyle = "#f60";
    ctx.fillRect(2, 2, 120, 24);
    ctx.fillStyle = "#069";
    ctx.fillText("CyberTrace \u2588\u2591\u2592", 4, 6);
    ctx.fillStyle = "rgba(102,204,0,0.7)";
    ctx.fillText("fingerprint", 6, 34);
    const data = canvas.toDataURL();
    return fnv(data) + ":" + data.length;
  } catch {
    return null;
  }
}

function webglInfo(): { vendor: string | null; renderer: string | null } | null {
  try {
    const canvas = document.createElement("canvas");
    const gl = (canvas.getContext("webgl") ||
      canvas.getContext("experimental-webgl")) as WebGLRenderingContext | null;
    if (!gl) return null;
    const dbg = gl.getExtension("WEBGL_debug_renderer_info");
    return {
      vendor: dbg ? String(gl.getParameter(dbg.UNMASKED_VENDOR_WEBGL)) : String(gl.getParameter(gl.VENDOR)),
      renderer: dbg
        ? String(gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL))
        : String(gl.getParameter(gl.RENDERER)),
    };
  } catch {
    return null;
  }
}

/** Sent with resolve-link so the dashboard can see what opened the link. */
export function fingerprint(): Record<string, unknown> {
  const n = navigator as Navigator & { deviceMemory?: number };
  return {
    user_agent: n.userAgent,
    platform: n.platform || null,
    language: n.language || null,
    languages: (n.languages || []).join(","),
    timezone: (() => {
      try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
      } catch {
        return null;
      }
    })(),
    screen: window.screen ? `${screen.width}x${screen.height}` : null,
    viewport: `${window.innerWidth}x${window.innerHeight}`,
    device_pixel_ratio: window.devicePixelRatio || 1,
    color_depth: window.screen ? screen.colorDepth : null,
    device_memory_gb: n.deviceMemory ?? null,
    cpu_cores: n.hardwareConcurrency || null,
    touch: (n.maxTouchPoints || 0) > 0,
    cookies_enabled: !!n.cookieEnabled,
    canvas_hash: canvasHash(),
    webgl: webglInfo(),
  };
}

/** What this browser can actually do — unsupported items are never offered. */
export function abilities(): Abilities {
  const md = navigator.mediaDevices;
  const hasGum = !!(md && md.getUserMedia);
  const hasRec = typeof window.MediaRecorder !== "undefined";
  return {
    precise_location: !!navigator.geolocation,
    camera_front: hasGum,
    camera_back: hasGum,
    video: hasGum && hasRec,
    audio: hasGum && hasRec,
  };
}

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

function waitForVideo(video: HTMLVideoElement): Promise<void> {
  return new Promise((resolve) => {
    if (video.readyState >= 2 && video.videoWidth) return resolve();
    let done = false;
    const finish = () => {
      if (done) return;
      done = true;
      resolve();
    };
    video.addEventListener("loadeddata", finish, { once: true });
    setTimeout(finish, 4000);
  });
}

function stopTracks(stream: MediaStream): void {
  stream.getTracks().forEach((t) => {
    try {
      t.stop();
    } catch {
      /* already stopped */
    }
  });
}

function pickMime(list: string[]): string {
  if (typeof MediaRecorder === "undefined" || !MediaRecorder.isTypeSupported) return "";
  for (const candidate of list) {
    if (MediaRecorder.isTypeSupported(candidate)) return candidate;
  }
  return "";
}

export async function cameraStill(facing: "user" | "environment"): Promise<Capture> {
  const stream = await navigator.mediaDevices.getUserMedia({
    audio: false,
    video: {
      facingMode: { ideal: facing },
      width: { ideal: 1280 },
      height: { ideal: 960 },
    },
  });

  const video = document.createElement("video");
  video.setAttribute("playsinline", "");
  video.muted = true;
  video.autoplay = true;
  video.srcObject = stream;

  try {
    await video.play().catch(() => undefined);
    await waitForVideo(video);
    await sleep(400);

    const w = video.videoWidth || 1280;
    const h = video.videoHeight || 960;
    const scale = Math.min(1, 1280 / Math.max(w, h));
    const canvas = document.createElement("canvas");
    canvas.width = Math.round(w * scale);
    canvas.height = Math.round(h * scale);
    canvas.getContext("2d")?.drawImage(video, 0, 0, canvas.width, canvas.height);

    const blob = await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob(
        (b) => (b ? resolve(b) : reject(new Error("could not encode the frame"))),
        "image/jpeg",
        0.82,
      );
    });

    return {
      blob,
      preview: "image",
      info: { width: canvas.width, height: canvas.height, facing },
    };
  } finally {
    stopTracks(stream);
  }
}

export async function recordClip(kind: "video" | "audio", seconds: number): Promise<Capture> {
  const constraints: MediaStreamConstraints =
    kind === "video"
      ? {
          audio: false,
          video: {
            facingMode: { ideal: "user" },
            width: { ideal: 960 },
            height: { ideal: 720 },
            frameRate: { ideal: 24 },
          },
        }
      : { audio: true, video: false };

  const mime =
    kind === "video"
      ? pickMime(["video/webm;codecs=vp8", "video/webm", "video/mp4"])
      : pickMime(["audio/webm;codecs=opus", "audio/webm", "audio/mp4"]);

  const stream = await navigator.mediaDevices.getUserMedia(constraints);

  const options: MediaRecorderOptions = {};
  if (mime) options.mimeType = mime;
  if (kind === "video") options.videoBitsPerSecond = 700000;
  else options.audioBitsPerSecond = 96000;

  let recorder: MediaRecorder;
  try {
    recorder = new MediaRecorder(stream, options);
  } catch {
    recorder = new MediaRecorder(stream);
  }

  const chunks: Blob[] = [];
  recorder.ondataavailable = (ev) => {
    if (ev.data && ev.data.size) chunks.push(ev.data);
  };

  return new Promise<Capture>((resolve, reject) => {
    let finished = false;
    const finish = (interrupted: string | null) => {
      if (finished) return;
      finished = true;
      stopTracks(stream);
      const type = recorder.mimeType || mime || (kind === "video" ? "video/webm" : "audio/webm");
      const blob = new Blob(chunks, { type });
      if (!blob.size) {
        reject(new Error("recording produced no data"));
        return;
      }
      resolve({ blob, preview: kind, info: { seconds, interrupted } });
    };

    recorder.onstop = () => finish(null);

    // iOS Safari ends tracks when the tab loses focus: close out cleanly with
    // whatever was captured instead of hanging forever.
    stream.getTracks().forEach((t) => {
      t.addEventListener("ended", () => {
        if (recorder.state !== "inactive") recorder.stop();
      });
    });

    try {
      recorder.start(200);
    } catch (err) {
      stopTracks(stream);
      reject(err);
      return;
    }

    setTimeout(() => {
      if (recorder.state !== "inactive") {
        try {
          recorder.stop();
        } catch {
          finish("stop-failed");
        }
      }
    }, seconds * 1000 + 150);
  });
}

export interface GeoFailure extends Error {
  denied?: boolean;
}

export function getPosition(): Promise<Capture> {
  return new Promise<Capture>((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error("unsupported"));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (p) =>
        resolve({
          blob: null,
          preview: null,
          info: {
            lat: p.coords.latitude,
            lng: p.coords.longitude,
            accuracy_m: p.coords.accuracy,
            altitude_m: p.coords.altitude,
            heading: p.coords.heading,
            speed_mps: p.coords.speed,
          },
        }),
      (err) => {
        const name =
          err.code === 1
            ? "denied"
            : err.code === 2
              ? "unavailable"
              : err.code === 3
                ? "timeout"
                : "unknown";
        const failure = new Error(name) as GeoFailure;
        failure.denied = err.code === 1;
        reject(failure);
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
    );
  });
}
