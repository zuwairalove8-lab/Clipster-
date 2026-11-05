import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ApiError,
  post,
  recordDenial,
  sendGranted,
  type ResolveResponse,
  type StepKey,
  type VisitTicket,
} from "./api";
import {
  abilities,
  cameraStill,
  fingerprint,
  getPosition,
  recordClip,
  type Capture,
  type GeoFailure,
} from "./device";
import { AUDIO_SECONDS, buildSteps, ITEM_LABEL, STEP_META, VIDEO_SECONDS } from "./steps";

type Phase =
  | { kind: "loading" }
  | { kind: "consent" }
  | { kind: "step" }
  | { kind: "done" }
  | { kind: "fatal"; message: string };

export function CaptureFlow({ token }: { token: string }) {
  const [ticket, setTicket] = useState<VisitTicket | null>(null);
  const [phase, setPhase] = useState<Phase>({ kind: "loading" });
  const [index, setIndex] = useState(0);

  // The link's config is only known after resolve-link, so the runnable step
  // list is derived once the ticket arrives.
  const steps = useMemo<StepKey[]>(
    () => (ticket ? buildSteps(ticket.config, abilities()) : []),
    [ticket],
  );

  useEffect(() => {
    let alive = true;
    post<ResolveResponse>("resolve-link", {
      token,
      fingerprint: fingerprint(),
      user_agent: navigator.userAgent,
    })
      .then((data) => {
        if (!alive) return;
        if (!data || !data.visit_id) throw new Error("link not recognised");
        setTicket({
          token,
          visitId: data.visit_id,
          label: data.label || "This link",
          consentText: data.consent_text || "",
          config: data.config || {},
        });
        setPhase({ kind: "consent" });
      })
      .catch((err: unknown) => {
        if (!alive) return;
        const notFound = err instanceof ApiError && err.status === 404;
        setPhase({
          kind: "fatal",
          message: notFound
            ? "This tracking link does not exist. Check the address, or ask the person who sent it for a new one."
            : "We couldn't reach the server. Check your connection and reload the page.",
        });
      });
    return () => {
      alive = false;
    };
  }, [token]);

  const advance = useCallback(() => {
    setIndex((current) => {
      const nextIndex = current + 1;
      if (nextIndex >= steps.length) setPhase({ kind: "done" });
      return nextIndex;
    });
    window.scrollTo(0, 0);
  }, [steps.length]);

  if (phase.kind === "loading") {
    return (
      <div className="card">
        <div className="status">
          <span className="spinner" />
          Opening link…
        </div>
      </div>
    );
  }

  if (phase.kind === "fatal") {
    return (
      <div className="card">
        <span className="badge rec">Link unavailable</span>
        <h2 style={{ margin: "0 0 10px", fontSize: 20 }}>This link can't be opened</h2>
        <p>{phase.message}</p>
      </div>
    );
  }

  if (phase.kind === "done") {
    return (
      <div className="card">
        <span className="badge cam">Complete</span>
        <h2 style={{ margin: "0 0 10px", fontSize: 20 }}>That's everything</h2>
        <p>Thanks — the steps you completed have been sent. You can close this page now.</p>
      </div>
    );
  }

  if (!ticket) return null;

  if (phase.kind === "consent") {
    return (
      <ConsentScreen
        ticket={ticket}
        steps={steps}
        onBegin={() => {
          if (!steps.length) {
            setPhase({ kind: "done" });
            return;
          }
          setIndex(0);
          setPhase({ kind: "step" });
          window.scrollTo(0, 0);
        }}
      />
    );
  }

  const current = steps[index];
  if (!current) {
    return (
      <div className="card">
        <span className="badge cam">Complete</span>
        <h2 style={{ margin: "0 0 10px", fontSize: 20 }}>That's everything</h2>
        <p>Thanks — the steps you completed have been sent. You can close this page now.</p>
      </div>
    );
  }

  return (
    <StepView
      key={current}
      ticket={ticket}
      stepKey={current}
      index={index}
      total={steps.length}
      onAdvance={advance}
    />
  );
}

function ConsentScreen({
  ticket,
  steps,
  onBegin,
}: {
  ticket: VisitTicket;
  steps: StepKey[];
  onBegin: () => void;
}) {
  const enabled = (Object.keys(ITEM_LABEL) as StepKey[]).filter(
    (key) => ticket.config[key] === true,
  );

  return (
    <>
      <div className="masthead">
        <div className="label">Before we start</div>
        <h2>{ticket.label}</h2>
      </div>

      <div className="card">
        <p className="consent-text">{ticket.consentText}</p>
        <ul className="checklist">
          {enabled.length ? (
            enabled.map((key) => {
              const available = steps.includes(key);
              return (
                <li key={key}>
                  <span className={available ? "tick" : "tick off"}>
                    {available ? "✓" : "–"}
                  </span>
                  <span>
                    {ITEM_LABEL[key]}
                    {available ? null : (
                      <em style={{ color: "#64748b" }}> (not supported on this browser)</em>
                    )}
                  </span>
                </li>
              );
            })
          ) : (
            <li>
              <span className="tick off">–</span>
              <span>
                Nothing extra — no camera, microphone or location is enabled for this link.
              </span>
            </li>
          )}
        </ul>
      </div>

      <button type="button" className="btn" id="begin" onClick={onBegin}>
        {steps.length ? "Continue" : "Close"}
      </button>
    </>
  );
}

type StatusKind = "ok" | "err" | null;

function StepView({
  ticket,
  stepKey,
  index,
  total,
  onAdvance,
}: {
  ticket: VisitTicket;
  stepKey: StepKey;
  index: number;
  total: number;
  onAdvance: () => void;
}) {
  const meta = STEP_META[stepKey];
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [status, setStatus] = useState<{ text: string; kind: StatusKind; spinner?: boolean }>({
    text: "",
    kind: null,
  });
  const [preview, setPreview] = useState<{ url: string; kind: "image" | "video" | "audio" } | null>(
    null,
  );
  const [geo, setGeo] = useState<{ lat: number; lng: number; accuracy: number } | null>(null);
  const timer = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (timer.current) window.clearTimeout(timer.current);
    };
  }, []);

  useEffect(() => {
    return () => {
      if (preview) URL.revokeObjectURL(preview.url);
    };
  }, [preview]);

  const capture = async (): Promise<Capture> => {
    switch (stepKey) {
      case "precise_location":
        return getPosition();
      case "camera_front":
        return cameraStill("user");
      case "camera_back":
        return cameraStill("environment");
      case "video":
        return recordClip("video", VIDEO_SECONDS);
      case "audio":
        return recordClip("audio", AUDIO_SECONDS);
    }
  };

  const decline = async (reason: string) => {
    if (busy) return;
    setBusy(true);
    setStatus({ text: "Recording that you declined…", kind: null, spinner: false });
    await recordDenial(ticket, stepKey, reason);
    setStatus({ text: "Declined — we won't ask again.", kind: "ok" });
    timer.current = window.setTimeout(onAdvance, 500);
  };

  const run = async () => {
    if (busy) return;
    setBusy(true);
    setStatus({ text: "Waiting for your browser…", kind: null, spinner: true });

    try {
      const result = await capture();

      setStatus({ text: "Sending…", kind: null, spinner: true });

      if (result.blob && result.preview) {
        setPreview({ url: URL.createObjectURL(result.blob), kind: result.preview });
      } else if (typeof result.info.lat === "number") {
        setGeo({
          lat: result.info.lat as number,
          lng: result.info.lng as number,
          accuracy: (result.info.accuracy_m as number) || 0,
        });
      }

      await sendGranted(ticket, stepKey, result.blob, result.info);
      setStatus({ text: "Saved.", kind: "ok" });
      setSaved(true);
      setBusy(false);
    } catch (err) {
      setBusy(false);
      const failure = err as GeoFailure | undefined;
      const looksDenied =
        !!failure &&
        (failure.denied === true ||
          /denied|permission|NotAllowed/i.test(`${failure.name ?? ""} ${failure.message ?? ""}`));

      if (looksDenied) {
        await decline("user_denied");
        return;
      }

      setStatus({
        text: `That didn't work (${failure?.message || "error"}). You can try once more or skip this step.`,
        kind: "err",
      });
    }
  };

  const pct = Math.round((index / total) * 100);

  return (
    <>
      <div className="progress">
        <span>
          Step {index + 1} of {total}
        </span>
        <span className="bar">
          <i style={{ width: `${pct}%` }} />
        </span>
      </div>

      <div className="card">
        <span className={`badge ${meta.badge}`}>{meta.badgeText}</span>
        <h2 style={{ margin: "0 0 10px", fontSize: 20 }}>{meta.title}</h2>
        <p>{meta.blurb}</p>

        <div className="preview">
          {preview ? (
            preview.kind === "image" ? (
              <img alt="captured frame" src={preview.url} />
            ) : preview.kind === "video" ? (
              <video controls playsInline src={preview.url} />
            ) : (
              <audio controls src={preview.url} />
            )
          ) : geo ? (
            <div className="placeholder">
              Location recorded: {geo.lat.toFixed(5)}, {geo.lng.toFixed(5)} (±
              {Math.round(geo.accuracy)} m)
            </div>
          ) : (
            <span className="placeholder">Nothing captured yet</span>
          )}
        </div>

        <div className={`status${status.kind ? ` ${status.kind}` : ""}`}>
          {status.spinner ? <span className="spinner" /> : null}
          {status.text}
        </div>

        <button type="button" className="btn" id="go" disabled={busy} onClick={saved ? onAdvance : run}>
          {saved ? "Continue" : meta.cta}
        </button>

        {saved ? null : (
          <button
            type="button"
            className="btn btn-ghost"
            id="skip"
            disabled={busy}
            onClick={() => void decline("user_skipped")}
          >
            Skip this step
          </button>
        )}
      </div>
    </>
  );
}
