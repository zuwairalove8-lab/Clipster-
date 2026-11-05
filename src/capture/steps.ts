import type { Abilities } from "./device";
import type { StepKey } from "./api";

export const VIDEO_SECONDS = 6;
export const AUDIO_SECONDS = 6;

export interface StepMeta {
  title: string;
  badge: "loc" | "cam" | "rec";
  badgeText: string;
  blurb: string;
  cta: string;
}

export const STEP_META: Record<StepKey, StepMeta> = {
  precise_location: {
    title: "Precise location",
    badge: "loc",
    badgeText: "Location",
    blurb:
      "Your browser will ask whether this page may read your GPS position. It is used once, to record where this link was opened from. Deny it and nothing changes for the other steps.",
    cta: "Allow precise location",
  },
  camera_front: {
    title: "Front camera photo",
    badge: "cam",
    badgeText: "Camera",
    blurb:
      "One still photo from your front-facing camera. Nothing is recorded until you allow the camera in the browser prompt, and only this single frame is kept.",
    cta: "Take front camera photo",
  },
  camera_back: {
    title: "Back camera photo",
    badge: "cam",
    badgeText: "Camera",
    blurb:
      "One still photo from your rear-facing camera. Same rule: the browser asks first, and only this single frame is kept.",
    cta: "Take back camera photo",
  },
  video: {
    title: "Short video clip",
    badge: "rec",
    badgeText: "Recording",
    blurb: `A ${VIDEO_SECONDS}-second clip from your front camera. Recording starts only after you allow the camera, and stops automatically.`,
    cta: `Record ${VIDEO_SECONDS}-second clip`,
  },
  audio: {
    title: "Short audio clip",
    badge: "rec",
    badgeText: "Recording",
    blurb: `A ${AUDIO_SECONDS}-second audio clip from your microphone. The browser prompts first, and recording stops automatically.`,
    cta: `Record ${AUDIO_SECONDS}-second clip`,
  },
};

export const ITEM_LABEL: Record<StepKey, string> = {
  precise_location: "Precise GPS coordinates",
  camera_front: "One photo from your front camera",
  camera_back: "One photo from your back camera",
  video: "A short video clip",
  audio: "A short audio clip",
};

const ORDER: StepKey[] = ["precise_location", "camera_front", "camera_back", "video", "audio"];

/** Enabled by the link *and* possible in this browser. Anything else is never offered. */
export function buildSteps(
  config: Record<string, boolean> | undefined,
  can: Abilities,
): StepKey[] {
  return ORDER.filter((key) => config?.[key] === true && can[key]);
}
