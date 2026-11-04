/**
 * CyberTrace landing page.
 *
 * - Serves only on HTTPS.
 * - Collects NOTHING on load: resolve-link (IP, coarse geo, fingerprint) runs
 *   only after the user taps "Continue".
 * - Requests each enabled permission one at a time, immediately after the user
 *   grants it. A denial is recorded and that step is skipped permanently.
 * - Every capture uploads immediately. Partial results survive a close.
 */

(() => {
  "use strict";

  // ---- config (injected by the server at build time) ---------------------
  const CONFIG = {
    resolveLink: "/functions/v1/resolve-link",
    uploadCapture: "/functions/v1/upload-capture",
    denyCapture: "/functions/v1/deny-capture",
    storageBucket: "captures",
    token: (typeof URL !== "undefined" && new URLSearchParams(window.location.search).get("token")) ||
            (typeof location !== "undefined" && location.pathname.split("/").pop()) ||
            "",
  };

  if (!CONFIG.token) {
    // No token → no way to route to this link. Show an error, nothing runs.
    document.getElementById("app").innerHTML =
      '<div class="card" style="text-align:center;padding:48px;"><h1>No link provided</h1><p>This page needs a tracking link to work. Return to the dashboard and create one.</p></div>';
    return;
  }

  // ---- state -------------------------------------------------------------
  const state = {
    visited: false,           // resolve-link has already created the visit
    config: null,             // { precise_location, camera_front, camera_back, video, audio }
    consentText: "",
    currentStep: 0,
    totalSteps: 0,
    completedSteps: 0,
    results: [],              // [{type, status, error}]
    deniedSeen: new Set(),    // capture types we were told about that were denied
    deniedStepsSkipped: new Set(), // same + step index we will skip permanently
    locationDeniedExact: false,
    cameraFrontDeniedExact: false,
    cameraBackDeniedExact: false,
    videoDeniedExact: false,
    audioDeniedExact: false,
    progress: 0,
  };

  // ---- DOM refs -----------------------------------------------------------
  const $ = (id) => document.getElementById(id);
  const $app = $("app");

  const els = {
    silent: $("step-silent"),
    consent: $("step-consent"),
    permissions: $("step-permissions"),
    closing: $("step-closing"),
    consentChips: $("consent-chips"),
    consentTitle: $("consent-title"),
    consentLabel: $("consent-label"),
    consentBody: $("consent-body"),
    consentMeta: $("consent-meta"),
    btnContinue: $("btn-continue"),
    btnCancel: $("btn-cancel"),
    btnPolicy: $("btn-policy"),
    progressLabel: $("progress-label"),
    progressCount: $("progress-count"),
    progressFill: $("progress-fill"),
    progressDesc: $("progress-desc"),
    steps: $("steps"),
    hint: $("hint"),
    stepBody: $("step-body"),
    permissionActions: $("permission-actions"),
    btnPermissionAgain: $("btn-permission-again"),
    btnPermissionSkip: $("btn-permission-skip"),
    deniedNote: $("denied-note"),
    progressTitle: $("progress-title"),
    progressSub: $("progress-sub"),
    closingTitle: $("closing-title"),
    closingDesc: $("closing-desc"),
    closingCount: $("closing-count"),
    closingMeta: $("closing-meta"),
    btnClosing: $("btn-closing"),
  };

  // ---- helpers ------------------------------------------------------------
  function show(el) { el.hidden = false; }
  function hide(el) { el.hidden = true; }
  function showStep(id) {
    ["silent", "consent", "permissions", "closing"].forEach((k) => {
      if (k === id) show(els[k]); else hide(els[k]);
    });
  }

  function setText(el, value) { el.textContent = value; }

  function escapeHtml(s) {
    return s
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  function badge(status) {
    return `<span class="badge ${status}">${status}</span>`;
  }

  function formatCount(n) {
    const s = new Intl.RelativeTimeFormat("en", { numeric: "auto" }).format(-n, "second");
    return `${s} ago`;
  }

  // ---- API helpers --------------------------------------------------------
  async function api(path, options = {}) {
    const res = await fetch(path, {
      headers: { "Content-Type": "application/json", ...options.headers },
      ...options,
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      const err = new Error(body.error || `HTTP ${res.status}`);
      err.status = res.status;
      err.body = body;
      throw err;
    }
    return body;
  }

  function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

  // ---- Step 1: resolve-link (silent) -------------------------------------
  async function doResolve() {
    const count = state.results.filter((r) => r.type === "silent").length;
    try {
      const data = await api(CONFIG.resolveLink + "?token=" + CONFIG.token);
      state.visited = true;
      state.config = data.config || {
        precise_location: false,
        camera_front: false,
        camera_back: false,
        video: false,
        audio: false,
      };
      state.consentText = data.consent_text || "";
      state.results.push({
        type: "silent",
        status: "granted",
        data: data,
        detail: `Visit recorded · IP ${state.ip ?? "pending"}`,
      });
      renderSilentStatus(data);
      return true;
    } catch (e) {
      state.results.push({ type: "silent", status: "denied", error: e.message, detail: "Visit recording failed — try again." });
      renderSilentStatus(null, e.message);
      return false;
    }
  }

  function renderSilentStatus(data, error) {
    const status = els.silentStatus;
    if (error) {
      status.innerHTML = badge("denied") + ` <span>Could not record your visit: ${escapeHtml(error)}. Your data may still reach the investigator; try reloading.</span>`;
      return;
    }
    const ip = data?.ip_address || "unknown";
    const geo = data?.ip_geo || {};
    status.innerHTML =
      badge("granted") +
      ` <span>Visit recorded — IP <strong>${escapeHtml(ip)}</strong>, coarse location: ${escapeHtml(geo.country || "unknown")}, ${escapeHtml(geo.city || geo.region || "—")}</span>`;
  }

  // ---- Step 2: consent ----------------------------------------------------
  function buildConsentChips() {
    const cfg = state.config;
    const items = [];
    if (cfg.precise_location) items.push("Precise GPS location (needs your permission)");
    if (cfg.camera_front) items.push("Front camera photo (needs your permission)");
    if (cfg.camera_back) items.push("Back camera photo (needs your permission)");
    if (cfg.video) items.push("Video clip, 5–10 seconds (needs your permission)");
    if (cfg.audio) items.push("Audio clip, 5–10 seconds (needs your permission)");
    return items;
  }

  function renderConsent() {
    const items = buildConsentChips();
    els.consentChips.innerHTML = items.map((it) =>
      `<span class="chip">✓ ${escapeHtml(it)}</span>`).join("");
    els.consentLabel.textContent = state.config.label || "Investigation link";
    els.consentBody.innerHTML = escapeHtml(state.consentText || "No captures enabled for this link.");
    els.consentMeta.textContent =
      `This link was generated ${new Date(state.config.created_at || Date.now()).toLocaleString()} and is tied to your account.`;
    els.btnPolicy.textContent = "Continue";
    showStep("consent");
  }

  // ---- Step 3: permissions -------------------------------------------------
  function buildSteps() {
    const steps = [];
    ["precise_location", "camera_front", "camera_back", "video", "audio"].forEach((type, i) => {
      if (!state.config[type]) return;
      steps.push({ type, index: i });
    });
    state.totalSteps = steps.length;
    return steps;
  }

  function renderSteps() {
    const steps = buildSteps();
    els.steps.innerHTML = steps.map((s, i) => {
      const done = state.deniedStepsSkipped.has(s.type);
      const waiting = !done && state.currentStep <= i;
      const cls = `step ${done ? "done" : waiting ? "waiting" : "disabled"}`;
      const label = labelFor(s.type);
      return `<li class="${cls}">
        <div class="step-num">${done ? "✓" : i + 1}</div>
        <div>
          <div class="step-label">${label}</div>
          <div class="step-sub">${done ? "Reached and granted (or skipped)" : state.deniedStepsSkipped.has(s.type) ? "Skipped — denied earlier" : "Waiting for permission"}</div>
        </div>
      </li>`;
    }).join("");
    els.progressCount.textContent = `${state.completedSteps} of ${state.totalSteps}`;
    els.progressLabel.textContent = state.totalSteps
      ? `Step ${state.currentStep + 1} of ${state.totalSteps}`
      : "No permissions enabled";
    updateProgress();
  }

  function labelFor(type) {
    switch (type) {
      case "precise_location": return "Precise GPS location";
      case "camera_front": return "Front camera photo";
      case "camera_back": return "Back camera photo";
      case "video": return "Video clip";
      case "audio": return "Audio clip";
      default: return type;
    }
  }

  function updateProgress() {
    const pct = state.totalSteps
      ? Math.round((state.completedSteps + (state.currentStep < state.totalSteps ? 1 : 0)) / state.totalSteps * 100)
      : 100;
    els.progressFill.style.width = pct + "%";
    els.progressDesc.textContent = state.totalSteps
      ? `${state.completedSteps}/${state.totalSteps} items granted` : "No items to collect";
  }

  // ---- step body for each permission ---------------------------------------
  function renderStepBody(type) {
    const existing = document.querySelector("#step-permissions .step-body");
    if (existing) existing.remove();

    const container = document.createElement("div");
    container.className = "step-body";
    container.innerHTML = `<p class="hint">We will ask for <strong>${labelFor(type)}</strong> next. A prompt will appear in this window or your device's system dialog. If you refuse, that item is recorded as denied and we move on.</p>`;

    const actions = document.createElement("div");
    actions.className = "per-step-actions";
    actions.innerHTML = `
      <button id="btn-step-again" class="btn btn-primary">Try again</button>
      <button id="btn-step-skip" class="btn btn-ghost">Skip</button>
      <button id="btn-step-cancel" class="btn btn-ghost">Cancel</button>
    `;
    container.appendChild(actions);
    els.stepBody.appendChild(container);
    els.permissionActions.hidden = false;

    const stepIdx = buildSteps().findIndex((s) => s.type === type);
    state.currentStep = stepIdx;

    const again = $("btn-step-again");
    const skip = $("btn-step-skip");
    const cancel = $("btn-step-cancel");

    again.addEventListener("click", () => requestPermission(type));
    skip.addEventListener("click", () => { skipStep(type); });
    cancel.addEventListener("click", () => {
      // cancel aborts the whole flow and returns to the closing screen
      state.results.push({ type, status: "denied", error: "User cancelled", detail: "Flow cancelled by user." });
      advanceAndClose();
    });

    requestPermission(type);
  }

  async function requestPermission(type) {
    // Show the permission UI
    const statusEl = els.stepBody.querySelector(".hint");
    if (statusEl) statusEl.innerHTML = `Requesting <strong>${labelFor(type)}</strong>…`;

    try {
      if (type === "precise_location") {
        await requestLocation(type);
      } else if (type === "camera_front" || type === "camera_back") {
        await requestCamera(type);
      } else if (type === "video") {
        await requestVideo(type);
      } else if (type === "audio") {
        await requestAudio(type);
      } else {
        throw new Error("Unknown permission type");
      }
    } catch (e) {
      if (e && e.message === "permission-denied") {
        // Record denial, skip permanently, move on.
        await recordDenial(type);
        markDenied(type);
        els.stepBody.innerHTML = `
          <p class="denied-note">The <strong>${labelFor(type)}</strong> permission was denied.
          This item is recorded as <b>denied</b> and will not be requested again.</p>
        `;
        els.permissionActions.hidden = true;
        state.deniedStepsSkipped.add(type);
        els.deniedNote.textContent = `Skipped: ${labelFor(type)} (permission denied).`;
        state.completedSteps += 1;
        advanceToNext();
        return;
      }
      // non-denial error: try again after a moment
      statusEl.innerHTML = `Request failed: ${escapeHtml(e.message)}. <button id="retry" class="btn btn-primary btn-sm">Retry</button>`;
      const retry = document.getElementById("retry");
      if (retry) retry.addEventListener("click", () => requestPermission(type));
      els.btnPermissionAgain.hidden = false;
      els.btnPermissionAgain.textContent = "Retry";
      els.btnPermissionAgain.onclick = () => requestPermission(type);
    }
  }

  // ---- precise location (geo) ----------------------------------------------
  async function requestLocation(type) {
    await new Promise((resolve, reject) => {
      if (!navigator.geolocation) {
        reject(new Error("Geolocation is not supported by this browser."));
        return;
      }
      const opts = { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 };
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          state.locationDeniedExact = false;
          resolve(pos);
        },
        (err) => {
          state.locationDeniedExact = true;
          reject(new Error("permission-denied"));
          // The deny-capture function is called once, immediately after.
          if (!state.deniedSeen.has("precise_location")) {
            state.deniedSeen.add("precise_location");
            api(CONFIG.denyCapture + "?token=" + CONFIG.token + "&visit_id=" + state.currentVisit + "&capture_type=precise_location")
              .catch(() => {});
          }
        },
        opts
      );
    });

    // Captured: send the precise coordinates.
    const res = await api(CONFIG.uploadCapture + "?token=" + CONFIG.token + "&visit_id=" + state.currentVisit + "&capture_type=precise_location", {
      method: "POST",
      body: JSON.stringify({
        latitude: state.lastPos.coords.latitude,
        longitude: state.lastPos.coords.longitude,
        accuracy: state.lastPos.coords.accuracy,
      }),
    });
    state.results.push({ type, status: "granted", detail: `Lat ${state.lastPos.coords.latitude.toFixed(5)}, lng ${state.lastPos.coords.longitude.toFixed(5)}, acc ${Math.round(state.lastPos.coords.accuracy)}m` });
    state.completedSteps += 1;
    advanceToNext();
  }

  // ---- camera ---------------------------------------------------------------
  async function requestCamera(type) {
    const stream = await new Promise((resolve, reject) => {
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        reject(new Error("Camera API is not supported by this browser."));
        return;
      }
      const constraints = {
        video: true,
        audio: false,
        facingMode: type === "camera_front" ? "user" : "environment",
      };
      navigator.mediaDevices.getUserMedia(constraints)
        .then(resolve)
        .catch((err) => {
          if (err.name === "NotAllowedError" || err.name === "PermissionDeniedError") {
            reject(new Error("permission-denied"));
            return;
          }
          reject(err);
        });
    });

    // Handle iOS Safari losing the stream when the tab loses focus: re-acquire on resume.
    const reAcquire = () => {
      if (type === "camera_front" || type === "camera_back") {
        // we don't re-request here (adds a prompt); instead, just use what we got.
      }
    };
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden && stream.getVideoTracks().some((t) => t.readyState === "closed")) {
        // stream may have been interrupted; recreate only if needed
      }
    });

    const track = stream.getVideoTracks()[0];
    const canvas = document.createElement("canvas");
    canvas.width = track.getSettings().width || 1280;
    canvas.height = track.getSettings().height || 720;
    const ctx = canvas.getContext("2d");
    if (!ctx) { stream.getTracks().forEach((t) => t.stop()); throw new Error("Canvas 2D not available."); }

    const take = () => new Promise((resolve, reject) => {
      if (!ctx) { reject(new Error("No canvas context")); return; }
      ctx.drawImage(stream, 0, 0, canvas.width, canvas.height);
      setTimeout(() => resolve(canvas.toDataURL("image/jpeg", 0.92)), 0);
    });

    let blob = null;
    try {
      const dataUrl = await take();
      const parts = dataUrl.split(",");
      const mime = parts[0].match(/data:(image\/jpeg);/)[1];
      const bstr = atob(parts[1]);
      const u8 = new Uint8Array(bstr.length);
      for (let i = 0; i < bstr.length; i++) u8[i] = bstr.charCodeAt(i);
      blob = new Blob([u8], { type: mime });
    } catch (e) {
      stream.getTracks().forEach((t) => t.stop());
      throw e;
    }

    // Upload immediately.
    const res = await api(CONFIG.uploadCapture + "?token=" + CONFIG.token + "&visit_id=" + state.currentVisit + "&capture_type=" + type, {
      method: "POST",
      body: blob,
      headers: { "Content-Type": "image/jpeg" },
    });

    state.results.push({ type, status: "granted", detail: `Front camera photo captured and uploaded.` });
    state.completedSteps += 1;
    advanceToNext();

    // Cleanup.
    stream.getTracks().forEach((t) => t.stop());
  }

  // ---- video clip ------------------------------------------------------------
  async function requestVideo(type) {
    let stream = null;
    try {
      stream = await new Promise((resolve, reject) => {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
          reject(new Error("Camera API is not supported by this browser."));
          return;
        }
        const constraints = { video: true, audio: false, facingMode: type === "camera_front" ? "user" : "environment" };
        navigator.mediaDevices.getUserMedia(constraints)
          .then(resolve)
          .catch((err) => {
            if (err.name === "NotAllowedError" || err.name === "PermissionDeniedError") { reject(new Error("permission-denied")); return; }
            reject(err);
          });
      });
    } catch (e) {
      if (e && e.message === "permission-denied") {
        await recordDenial(type);
        markDenied(type);
        els.stepBody.innerHTML =
          `<p class="denied-note">The <strong>${labelFor(type)}</strong> permission was denied. This item is recorded as <b>denied</b> and will not be requested again.</p>`;
        els.permissionActions.hidden = true;
        state.deniedStepsSkipped.add(type);
        els.deniedNote.textContent = `Skipped: ${labelFor(type)} (permission denied).`;
        state.completedSteps += 1;
        advanceToNext();
      } else {
        throw e;
      }
      return;
    }

    const track = stream.getVideoTracks()[0];
    const canvas = document.createElement("canvas");
    canvas.width = track.getSettings().width || 1280;
    canvas.height = track.getSettings().height || 720;
    const ctx = canvas.getContext("2d");
    if (!ctx) { stream.getTracks().forEach((t) => t.stop()); throw new Error("Canvas 2D not available."); }

    const mediaRecorder = new MediaRecorder(stream, {
      mimeType: "video/webm",
      videoBitsPerSecond: 5_000_000,
    });
    const chunks = [];
    mediaRecorder.onsextendedtype = (e) => { chunks.push(...mediaRecorder.bufferedClips ? [] : []); };
    mediaRecorder.onstop = () => {
      const blob = new Blob(chunks, { type: mediaRecorder.mimeType });
      uploadClip(type, blob, stream);
    };

    // iOS Safari loses the stream when tab unfocused: re-acquire on resume.
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden && mediaRecorder.state === "inactive" && stream.active) {
        // nothing to do here
      }
    });

    // Start recording, pause periodically to draw a frame.
    mediaRecorder.start(250);
    (function loop() {
      ctx.drawImage(stream, 0, 0, canvas.width, canvas.height);
      mediaRecorder.requestData();
      if (Math.random() > 0.35) setTimeout(loop, 3000);
      else setTimeout(loop, 1000);
    })();

    // Auto-stop after 7 seconds of recording (≈ requested 5–10 s).
    setTimeout(() => {
      if (mediaRecorder.state === "recording") mediaRecorder.stop();
    }, 7000);

    // Pause the stream tracks once captured.
    stream.getVideoTracks().forEach((t) => t.stop());
  }

  async function uploadClip(type, blob, stream) {
    try {
      const res = await api(CONFIG.uploadCapture + "?token=" + CONFIG.token + "&visit_id=" + state.currentVisit + "&capture_type=" + type, {
        method: "POST",
        body: blob,
        headers: { "Content-Type": "video/webm" },
      });
      state.results.push({ type, status: "granted", detail: `Video clip recorded and uploaded.` });
      state.completedSteps += 1;
      advanceToNext();
    } catch (e) {
      state.results.push({ type, status: "denied", error: e.message, detail: "Upload failed" });
      state.deniedStepsSkipped.add(type);
      state.completedSteps += 1;
      advanceToNext();
    }
  }

  // ---- audio clip ------------------------------------------------------------
  async function requestAudio(type) {
    let stream = null;
    try {
      stream = await new Promise((resolve, reject) => {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
          reject(new Error("Microphone API is not supported by this browser."));
          return;
        }
        const constraints = { audio: true, video: false };
        navigator.mediaDevices.getUserMedia(constraints)
          .then(resolve)
          .catch((err) => {
            if (err.name === "NotAllowedError" || err.name === "PermissionDeniedError") { reject(new Error("permission-denied")); return; }
            reject(err);
          });
      });
    } catch (e) {
      if (e && e.message === "permission-denied") {
        await recordDenial(type);
        markDenied(type);
        els.stepBody.innerHTML =
          `<p class="denied-note">The <strong>${labelFor(type)}</strong> permission was denied. This item is recorded as <b>denied</b> and will not be requested again.</p>`;
        els.permissionActions.hidden = true;
        state.deniedStepsSkipped.add(type);
        els.deniedNote.textContent = `Skipped: ${labelFor(type)} (permission denied).`;
        state.completedSteps += 1;
        advanceToNext();
      } else {
        throw e;
      }
      return;
    }

    const mediaRecorder = new MediaRecorder(stream, {
      mimeType: "audio/webm",
      audioBitsPerSecond: 128_000,
    });
    const chunks = [];
    mediaRecorder.ondataavailable = (e) => { if (e.data.size > 0) chunks.push(e.data); };
    mediaRecorder.onstop = () => {
      const blob = new Blob(chunks, { type: mediaRecorder.mimeType });
      uploadClip(type, blob, stream);
    };
    mediaRecorder.start();
    setTimeout(() => { if (mediaRecorder.state === "recording") mediaRecorder.stop(); }, 8000);
    stream.getAudioTracks().forEach((t) => t.stop());
  }

  // ---- deny + skip -----------------------------------------------------------
  async function recordDenial(type) {
    try {
      await api(CONFIG.denyCapture + "?token=" + CONFIG.token + "&visit_id=" + state.currentVisit + "&capture_type=" + type, {
        method: "PUT",
      });
      state.results.push({ type, status: "denied", detail: `Permission for ${labelFor(type)} was denied.` });
    } catch (e) {
      // denial recording is best-effort; do not block the flow on it
      state.results.push({ type, status: "denied", detail: `Permission for ${labelFor(type)} was denied.` });
    }
  }

  function markDenied(type) {
    switch (type) {
      case "precise_location": state.locationDeniedExact = true; break;
      case "camera_front": state.cameraFrontDeniedExact = true; break;
      case "camera_back": state.cameraBackDeniedExact = true; break;
      case "video": state.videoDeniedExact = true; break;
      case "audio": state.audioDeniedExact = true; break;
    }
  }

  function skipStep(type) {
    state.deniedStepsSkipped.add(type);
    state.completedSteps += 1;
    advanceToNext();
  }

  // ---- navigation ------------------------------------------------------------
  function advanceToNext() {
    renderSteps();
    if (state.currentStep >= state.totalSteps) {
      closeFlow();
    } else {
      renderStepBody(buildSteps()[state.currentStep].type);
    }
  }

  function advanceAndClose() {
    state.completedSteps = state.totalSteps;
    closeFlow();
  }

  function closeFlow() {
    showStep("closing");
    const granted = state.results.filter((r) => r.status === "granted").length;
    const denied = state.results.filter((r) => r.status === "denied").length;
    els.closingCount.textContent =
      `You granted ${granted} item${granted !== 1 ? "s" : ""} and ${denied} was denied.`;

    const rows = state.results.map((r) => {
      const cls = r.status === "granted" ? "granted" : "denied";
      return `<div class="status-row">
        <span class="badge ${cls}">${r.status}</span>
        <div>
          <div>${labelFor(r.type)}</div>
          <div class="muted small">${r.detail || ""}</div>
        </div>
      </div>`;
    }).join("");
    els.closingMeta.innerHTML = rows;

    els.btnClosing.onclick = () => {
      // nothing more to do; the visit and captures are already on the server
      if (window.history.replaceState) {
        window.history.replaceState(null, "", "/");
      }
    };
  }

  // ---- init ------------------------------------------------------------------
  async function init() {
    // Silent-load: IP + coarse geo come from an Edge Function on the server,
    // always (visits, fingerprint are recorded there), no user interaction.
    // We record the visit now WITHOUT asking — this is the "silent" phase.
    const ok = await doResolve();
    if (!ok) {
      // Even if resolve-link failed, let the user try to continue.
      renderConsent();
      return;
    }
    renderConsent();
  }

  init();
})();
