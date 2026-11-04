/* CyberTrace capture page.
 *
 * Ground rules enforced here:
 *  - nothing is requested on load; the consent screen comes first and a tap
 *    (Continue) is required before any permission prompt
 *  - every item is requested on its own, behind its own button
 *  - a refusal is recorded once and never re-prompted
 *  - each granted item is uploaded immediately, so partial results survive
 *  - unsupported items are hidden rather than failing
 *
 * No trackers, no third-party cookies, no external requests other than this
 * project's own Supabase endpoints.
 */
(function () {
  "use strict";

  var SUPABASE_URL = "https://yrmdsvkxpyejpmphfstb.supabase.co";
  var ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlybWRzdmt4cHllanBtcGhmc3RiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM3NDMxMjUsImV4cCI6MjEwOTMxOTEyNX0.g8VO892KEg5n9Lc3bjYbDCxEti-WI6FqKCSoYvnaIvg";

  var DIRECT_LIMIT = 4 * 1024 * 1024;
  var VIDEO_SECONDS = 6;
  var AUDIO_SECONDS = 6;

  var STATE = {
    token: "",
    visitId: "",
    label: "",
    config: null,
    consentText: "",
    steps: [],
    index: 0,
    busy: false,
  };

  /* ------------------------------------------------------------------ utils */

  function $(sel) {
    return document.querySelector(sel);
  }

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return {
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;",
      }[c];
    });
  }

  function sleep(ms) {
    return new Promise(function (r) {
      setTimeout(r, ms);
    });
  }

  function post(fn, body, timeoutMs) {
    var ctl = new AbortController();
    var timer = setTimeout(function () {
      ctl.abort();
    }, timeoutMs || 25000);
    return fetch(SUPABASE_URL + "/functions/v1/" + fn, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        apikey: ANON_KEY,
        Authorization: "Bearer " + ANON_KEY,
      },
      body: JSON.stringify(body),
      signal: ctl.signal,
      mode: "cors",
      credentials: "omit",
    })
      .then(function (r) {
        return r.text().then(function (t) {
          var data = null;
          try {
            data = JSON.parse(t);
          } catch (e) {
            data = { raw: t };
          }
          if (!r.ok) {
            var err = new Error((data && data.error) || "HTTP " + r.status);
            err.status = r.status;
            throw err;
          }
          return data;
        });
      })
      .finally(function () {
        clearTimeout(timer);
      });
  }

  function blobToBase64(blob) {
    return new Promise(function (resolve, reject) {
      var fr = new FileReader();
      fr.onload = function () {
        var s = String(fr.result);
        resolve(s.slice(s.indexOf(",") + 1));
      };
      fr.onerror = function () {
        reject(new Error("could not read file"));
      };
      fr.readAsDataURL(blob);
    });
  }

  /* ------------------------------------------------------------ fingerprint */

  function fnv(str) {
    var h = 0x811c9dc5;
    for (var i = 0; i < str.length; i++) {
      h ^= str.charCodeAt(i);
      h = (h * 0x01000193) >>> 0;
    }
    return ("00000000" + h.toString(16)).slice(-8);
  }

  function canvasHash() {
    try {
      var c = document.createElement("canvas");
      c.width = 240;
      c.height = 60;
      var ctx = c.getContext("2d");
      ctx.textBaseline = "top";
      ctx.font = "16px 'Arial'";
      ctx.fillStyle = "#f60";
      ctx.fillRect(2, 2, 120, 24);
      ctx.fillStyle = "#069";
      ctx.fillText("CyberTrace \u2588\u2591\u2592", 4, 6);
      ctx.fillStyle = "rgba(102,204,0,0.7)";
      ctx.fillText("fingerprint", 6, 34);
      var data = c.toDataURL();
      return fnv(data) + ":" + data.length;
    } catch (e) {
      return null;
    }
  }

  function webglInfo() {
    try {
      var c = document.createElement("canvas");
      var gl = c.getContext("webgl") || c.getContext("experimental-webgl");
      if (!gl) return null;
      var dbg = gl.getExtension("WEBGL_debug_renderer_info");
      return {
        vendor: dbg ? gl.getParameter(dbg.UNMASKED_VENDOR_WEBGL) : gl.getParameter(gl.VENDOR),
        renderer: dbg ? gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER),
      };
    } catch (e) {
      return null;
    }
  }

  function fingerprint() {
    var n = navigator;
    return {
      user_agent: n.userAgent,
      platform: n.platform || null,
      language: n.language || null,
      languages: (n.languages || []).join(","),
      timezone: (function () {
        try {
          return Intl.DateTimeFormat().resolvedOptions().timeZone;
        } catch (e) {
          return null;
        }
      })(),
      screen: window.screen ? screen.width + "x" + screen.height : null,
      viewport: window.innerWidth + "x" + window.innerHeight,
      device_pixel_ratio: window.devicePixelRatio || 1,
      color_depth: window.screen ? screen.colorDepth : null,
      device_memory_gb: n.deviceMemory || null,
      cpu_cores: n.hardwareConcurrency || null,
      touch: (n.maxTouchPoints || 0) > 0,
      cookies_enabled: !!n.cookieEnabled,
      canvas_hash: canvasHash(),
      webgl: webglInfo(),
    };
  }

  /* -------------------------------------------------------- feature detect */

  function supports() {
    var md = navigator.mediaDevices;
    var hasGum = !!(md && md.getUserMedia);
    var hasRec = typeof window.MediaRecorder !== "undefined";
    return {
      precise_location: !!navigator.geolocation,
      camera_front: hasGum,
      camera_back: hasGum,
      video: hasGum && hasRec,
      audio: hasGum && hasRec,
    };
  }

  var STEP_META = {
    precise_location: {
      title: "Precise location",
      badge: "loc",
      badge_text: "Location",
      blurb:
        "Your browser will ask whether this page may read your GPS position. It is used once, to record where this link was opened from. Deny it and nothing changes for the other steps.",
      cta: "Allow precise location",
    },
    camera_front: {
      title: "Front camera photo",
      badge: "cam",
      badge_text: "Camera",
      blurb:
        "One still photo from your front-facing camera. Nothing is recorded until you allow the camera in the browser prompt, and only this single frame is kept.",
      cta: "Take front camera photo",
    },
    camera_back: {
      title: "Back camera photo",
      badge: "cam",
      badge_text: "Camera",
      blurb:
        "One still photo from your rear-facing camera. Same rule: the browser asks first, and only this single frame is kept.",
      cta: "Take back camera photo",
    },
    video: {
      title: "Short video clip",
      badge: "rec",
      badge_text: "Recording",
      blurb:
        "A " +
        VIDEO_SECONDS +
        "-second clip from your front camera. Recording starts only after you allow the camera, and stops automatically.",
      cta: "Record " + VIDEO_SECONDS + "-second clip",
    },
    audio: {
      title: "Short audio clip",
      badge: "rec",
      badge_text: "Recording",
      blurb:
        "A " +
        AUDIO_SECONDS +
        "-second audio clip from your microphone. The browser prompts first, and recording stops automatically.",
      cta: "Record " + AUDIO_SECONDS + "-second clip",
    },
  };

  var ITEM_LABEL = {
    precise_location: "Precise GPS coordinates",
    camera_front: "One photo from your front camera",
    camera_back: "One photo from your back camera",
    video: "A short video clip",
    audio: "A short audio clip",
  };

  function buildSteps(config, can) {
    var order = ["precise_location", "camera_front", "camera_back", "video", "audio"];
    return order.filter(function (k) {
      return config && config[k] === true && can[k] === true;
    });
  }

  /* ------------------------------------------------------------- rendering */

  function renderConsent() {
    var on = Object.keys(ITEM_LABEL).filter(function (k) {
      return STATE.config[k] === true;
    });

    var list = on.length
      ? on
          .map(function (k) {
            var available = STATE.steps.indexOf(k) >= 0;
            return (
              '<li><span class="tick' +
              (available ? "" : " off") +
              '">' +
              (available ? "\u2713" : "\u2013") +
              "</span><span>" +
              esc(ITEM_LABEL[k]) +
              (available ? "" : ' <em style="color:#64748b">(not supported on this browser)</em>') +
              "</span></li>"
            );
          })
          .join("")
      : '<li><span class="tick off">\u2013</span><span>Nothing extra — no camera, microphone or location is enabled for this link.</span></li>';

    $("#flow").innerHTML =
      '<div class="masthead"><div class="label">Before we start</div>' +
      '<h2>' +
      esc(STATE.label) +
      "</h2></div>" +
      '<div class="card">' +
      '<p class="consent-text">' +
      esc(STATE.consentText) +
      "</p>" +
      '<ul class="checklist">' +
      list +
      "</ul>" +
      "</div>" +
      '<button class="btn" id="begin">' +
      (STATE.steps.length ? "Continue" : "Close") +
      "</button>";

    $("#begin").addEventListener("click", function () {
      if (!STATE.steps.length) return renderDone();
      STATE.index = 0;
      renderStep();
    });

    window.scrollTo(0, 0);
  }

  function renderStep() {
    var key = STATE.steps[STATE.index];
    var meta = STEP_META[key];
    var total = STATE.steps.length;
    var pct = Math.round((STATE.index / total) * 100);

    $("#flow").innerHTML =
      '<div class="progress"><span>Step ' +
      (STATE.index + 1) +
      " of " +
      total +
      '</span><span class="bar"><i style="width:' +
      pct +
      '%"></i></span></div>' +
      '<div class="card">' +
      '<span class="badge ' +
      meta.badge +
      '">' +
      esc(meta.badge_text) +
      "</span>" +
      "<h2 style=\"margin:0 0 10px;font-size:20px\">" +
      esc(meta.title) +
      "</h2>" +
      "<p>" +
      esc(meta.blurb) +
      "</p>" +
      '<div class="preview" id="preview"><span class="placeholder">Nothing captured yet</span></div>' +
      '<div class="status" id="status"></div>' +
      '<button class="btn" id="go">' +
      esc(meta.cta) +
      "</button>" +
      '<button class="btn btn-ghost" id="skip">Skip this step</button>' +
      "</div>";

    $("#go").addEventListener("click", function () {
      runStep(key);
    });
    $("#skip").addEventListener("click", function () {
      deny(key, "user_skipped");
    });

    window.scrollTo(0, 0);
  }

  function setStatus(msg, kind) {
    var el = $("#status");
    if (!el) return;
    el.className = "status" + (kind ? " " + kind : "");
    el.innerHTML = msg;
  }

  function next() {
    STATE.index += 1;
    if (STATE.index >= STATE.steps.length) renderDone();
    else renderStep();
  }

  function renderDone() {
    $("#flow").innerHTML =
      '<div class="card">' +
      '<span class="badge cam">Complete</span>' +
      '<h2 style="margin:0 0 10px;font-size:20px">That\'s everything</h2>' +
      "<p>Thanks — the steps you completed have been sent. You can close this page now.</p>" +
      "</div>";
    window.scrollTo(0, 0);
  }

  function renderFatal(msg) {
    $("#flow").innerHTML =
      '<div class="card">' +
      '<span class="badge rec">Link unavailable</span>' +
      '<h2 style="margin:0 0 10px;font-size:20px">This link can\'t be opened</h2>' +
      "<p>" +
      esc(msg) +
      "</p></div>";
  }

  /* -------------------------------------------------------------- uploading */

  function withAuthUpload(blob, contentType, signed) {
    var headers = { "Content-Type": contentType, apikey: ANON_KEY };
    return fetch(signed.upload_url, {
      method: "PUT",
      headers: headers,
      body: blob,
    }).then(function (r) {
      if (!r.ok) throw new Error("upload failed (" + r.status + ")");
      return signed;
    });
  }

  function sendGranted(type, blob, contentType, payload) {
    var base = {
      token: STATE.token,
      visit_id: STATE.visitId,
      capture_type: type,
      status: "granted",
      payload: payload || {},
    };

    if (!blob) return post("upload-capture", base);

    var ct = contentType || blob.type || "application/octet-stream";

    if (blob.size <= DIRECT_LIMIT) {
      return blobToBase64(blob).then(function (b64) {
        base.content_type = ct;
        base.data_base64 = b64;
        return post("upload-capture", base, 60000);
      });
    }

    base.content_type = ct;
    return post("upload-capture", base, 30000).then(function (signed) {
      if (!signed.upload_url) return signed;
      return withAuthUpload(blob, ct, signed);
    });
  }

  function deny(type, reason) {
    if (STATE.busy) return;
    STATE.busy = true;
    setStatus("Recording that you declined\u2026");
    post("deny-capture", {
      token: STATE.token,
      visit_id: STATE.visitId,
      capture_type: type,
      reason: reason || "user_denied",
    })
      .catch(function () {
        /* a lost denial record must never block the user */
      })
      .then(function () {
        STATE.busy = false;
        setStatus("Declined — we won't ask again.", "ok");
        setTimeout(next, 500);
      });
  }

  /* --------------------------------------------------------------- capture */

  function waitForVideo(video) {
    return new Promise(function (resolve) {
      if (video.readyState >= 2 && video.videoWidth) return resolve();
      var done = false;
      var finish = function () {
        if (done) return;
        done = true;
        resolve();
      };
      video.addEventListener("loadeddata", finish, { once: true });
      setTimeout(finish, 4000);
    });
  }

  function pickMime(list) {
    if (typeof MediaRecorder === "undefined" || !MediaRecorder.isTypeSupported) return "";
    for (var i = 0; i < list.length; i++) {
      if (MediaRecorder.isTypeSupported(list[i])) return list[i];
    }
    return "";
  }

  function cameraStill(facing) {
    return navigator.mediaDevices
      .getUserMedia({
        audio: false,
        video: {
          facingMode: { ideal: facing },
          width: { ideal: 1280 },
          height: { ideal: 960 },
        },
      })
      .then(function (stream) {
        var video = document.createElement("video");
        video.setAttribute("playsinline", "");
        video.muted = true;
        video.autoplay = true;
        video.srcObject = stream;
        var stop = function () {
          stream.getTracks().forEach(function (t) {
            try {
              t.stop();
            } catch (e) {}
          });
        };
        return video
          .play()
          .catch(function () {})
          .then(function () {
            return waitForVideo(video);
          })
          .then(function () {
            return sleep(400);
          })
          .then(function () {
            var w = video.videoWidth || 1280;
            var h = video.videoHeight || 960;
            var scale = Math.min(1, 1280 / Math.max(w, h));
            var canvas = document.createElement("canvas");
            canvas.width = Math.round(w * scale);
            canvas.height = Math.round(h * scale);
            canvas.getContext("2d").drawImage(video, 0, 0, canvas.width, canvas.height);
            return new Promise(function (resolve, reject) {
              canvas.toBlob(
                function (blob) {
                  stop();
                  resolve({
                    blob: blob,
                    preview: "image",
                    info: { width: canvas.width, height: canvas.height, facing: facing },
                  });
                },
                "image/jpeg",
                0.82,
              );
            });
          })
          .catch(function (e) {
            stop();
            throw e;
          });
      });
  }

  function recordClip(kind) {
    var constraints =
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

    var mime =
      kind === "video"
        ? pickMime(["video/webm;codecs=vp8", "video/webm", "video/mp4"])
        : pickMime(["audio/webm;codecs=opus", "audio/webm", "audio/mp4"]);

    return navigator.mediaDevices.getUserMedia(constraints).then(function (stream) {
      var opts = {};
      if (mime) opts.mimeType = mime;
      if (kind === "video") opts.videoBitsPerSecond = 700000;
      else opts.audioBitsPerSecond = 96000;

      var recorder;
      try {
        recorder = new MediaRecorder(stream, opts);
      } catch (e) {
        recorder = new MediaRecorder(stream);
      }

      var chunks = [];
      recorder.ondataavailable = function (ev) {
        if (ev.data && ev.data.size) chunks.push(ev.data);
      };

      var stopTracks = function () {
        stream.getTracks().forEach(function (t) {
          try {
            t.stop();
          } catch (e) {}
        });
      };

      return new Promise(function (resolve, reject) {
        var finished = false;
        var finish = function (reason) {
          if (finished) return;
          finished = true;
          stopTracks();
          var type = recorder.mimeType || mime || (kind === "video" ? "video/webm" : "audio/webm");
          var blob = new Blob(chunks, { type: type });
          if (!blob.size) return reject(new Error("recording produced no data"));
          resolve({ blob: blob, preview: kind, info: { seconds: kind === "video" ? VIDEO_SECONDS : AUDIO_SECONDS, interrupted: reason || null } });
        };

        recorder.onstop = function () {
          finish(null);
        };

        // iOS Safari can end the track when the tab loses focus; close out
        // cleanly with whatever was captured instead of hanging.
        stream.getTracks().forEach(function (t) {
          t.addEventListener("ended", function () {
            if (recorder.state !== "inactive") recorder.stop();
          });
        });

        try {
          recorder.start(200);
        } catch (e) {
          stopTracks();
          return reject(e);
        }

        setTimeout(function () {
          if (recorder.state !== "inactive") {
            try {
              recorder.stop();
            } catch (e) {
              finish("stop-failed");
            }
          }
        }, (kind === "video" ? VIDEO_SECONDS : AUDIO_SECONDS) * 1000 + 150);
      });
    });
  }

  function getPosition() {
    return new Promise(function (resolve, reject) {
      if (!navigator.geolocation) return reject(new Error("unsupported"));
      navigator.geolocation.getCurrentPosition(
        function (p) {
          resolve({
            preview: null,
            info: {
              lat: p.coords.latitude,
              lng: p.coords.longitude,
              accuracy_m: p.coords.accuracy,
              altitude_m: p.coords.altitude,
              heading: p.coords.heading,
              speed_mps: p.coords.speed,
            },
          });
        },
        function (err) {
          var msg =
            err.code === 1
              ? "denied"
              : err.code === 2
                ? "unavailable"
                : err.code === 3
                  ? "timeout"
                  : "unknown";
          var e = new Error(msg);
          e.denied = err.code === 1;
          reject(e);
        },
        { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
      );
    });
  }

  function showPreview(kind, blob, info) {
    var box = $("#preview");
    if (!box) return;
    box.innerHTML = "";
    if (kind === "image" && blob) {
      var img = document.createElement("img");
      img.alt = "captured frame";
      img.src = URL.createObjectURL(blob);
      box.appendChild(img);
    } else if (kind === "video" && blob) {
      var v = document.createElement("video");
      v.controls = true;
      v.playsInline = true;
      v.src = URL.createObjectURL(blob);
      box.appendChild(v);
    } else if (kind === "audio" && blob) {
      var a = document.createElement("audio");
      a.controls = true;
      a.src = URL.createObjectURL(blob);
      box.appendChild(a);
    } else if (info && typeof info.lat === "number") {
      box.innerHTML =
        '<div class="placeholder">Location recorded: ' +
        info.lat.toFixed(5) +
        ", " +
        info.lng.toFixed(5) +
        " (\u00b1" +
        Math.round(info.accuracy_m || 0) +
        " m)</div>";
    }
  }

  function finishStep(message) {
    setStatus(message, "ok");
    var btn = $("#go");
    if (btn) {
      btn.disabled = false;
      btn.textContent = "Continue";
      var clone = btn.cloneNode(true);
      btn.parentNode.replaceChild(clone, btn);
      clone.addEventListener("click", next);
    }
    var skip = $("#skip");
    if (skip) skip.remove();
  }

  function runStep(key) {
    if (STATE.busy) return;
    STATE.busy = true;
    var btn = $("#go");
    if (btn) btn.disabled = true;
    setStatus('<span class="spinner"></span>Waiting for your browser\u2026');

    var work;
    if (key === "precise_location") work = getPosition();
    else if (key === "camera_front") work = cameraStill("user");
    else if (key === "camera_back") work = cameraStill("environment");
    else if (key === "video") work = recordClip("video");
    else work = recordClip("audio");

    work
      .then(function (res) {
        setStatus('<span class="spinner"></span>Sending\u2026');
        showPreview(res.preview, res.blob, res.info);
        return sendGranted(key, res.blob || null, res.blob ? res.blob.type : null, res.info).then(
          function () {
            STATE.busy = false;
            finishStep("Saved.");
          },
        );
      })
      .catch(function (err) {
        STATE.busy = false;
        var denied =
          err && (err.denied || /denied|permission|NotAllowed/i.test(err.name + " " + err.message));
        if (denied) {
          return deny(key, "user_denied");
        }
        setStatus(
          "That didn't work (" +
            esc((err && err.message) || "error") +
            "). You can try once more or skip this step.",
          "err",
        );
        var b = $("#go");
        if (b) b.disabled = false;
      });
  }

  /* ------------------------------------------------------------------ boot */

  function tokenFromUrl() {
    var m = /[?&]t=([^&#]+)/.exec(location.search);
    if (m) return decodeURIComponent(m[1]);
    var p = /\/c\/([^/?#]+)/.exec(location.pathname);
    return p ? decodeURIComponent(p[1]) : "";
  }

  function boot() {
    var token = tokenFromUrl();

    if (!token) {
      $("#marketing").classList.remove("hidden");
      return;
    }

    STATE.token = token;
    $("#marketing").classList.add("hidden");
    $("#app").classList.remove("hidden");
    $("#flow").innerHTML =
      '<div class="card"><div class="status"><span class="spinner"></span>Opening link\u2026</div></div>';

    var fp = fingerprint();
    post("resolve-link", {
      token: token,
      fingerprint: fp,
      user_agent: navigator.userAgent,
    })
      .then(function (data) {
        if (!data || !data.visit_id) throw new Error("link not recognised");
        STATE.visitId = data.visit_id;
        STATE.label = data.label || "This link";
        STATE.config = data.config || {};
        STATE.consentText = data.consent_text || "";
        STATE.steps = buildSteps(STATE.config, supports());
        renderConsent();
      })
      .catch(function (err) {
        renderFatal(
          err && err.status === 404
            ? "This tracking link does not exist. Check the address, or ask the person who sent it for a new one."
            : "We couldn't reach the server. Check your connection and reload the page.",
        );
      });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
