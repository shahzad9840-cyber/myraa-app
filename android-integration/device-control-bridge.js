// Import this in your web app instead of calling
// navigator.mediaDevices.getDisplayMedia(), which does not exist
// inside an Android WebView.
//
// import { Plugins } from '@capacitor/core' pattern (Capacitor 6 uses
// registerPlugin instead):
import { registerPlugin } from '@capacitor/core';

const ScreenShare = registerPlugin('ScreenShare');
const DeviceControl = registerPlugin('DeviceControl');

// ---- Screen share ----

export async function startScreenShare() {
  // Triggers Android's native "MYRAA wants to start capturing
  // everything on your screen" system dialog. This is Android's own
  // consent prompt and cannot be skipped from code.
  return ScreenShare.start();
}

export async function stopScreenShare() {
  return ScreenShare.stop();
}

/** Call this in a loop (e.g. every 500ms-2s) to poll frames while
 * screen share is active, and feed them to your AI vision call. */
export async function grabFrame() {
  const { image } = await ScreenShare.captureFrame();
  return image; // data:image/jpeg;base64,...
}

// ---- On-device control ----

export async function isControlEnabled() {
  const { enabled } = await DeviceControl.isEnabled();
  return enabled;
}

/** Sends the user to Settings > Accessibility to flip MYRAA on.
 * Android requires this manual step; there's no code path around it. */
export async function requestControlPermission() {
  return DeviceControl.openSettings();
}

/** NEW: Returns everything visible on screen right now - an array of
 * { text, clickable, x, y }. Call this BEFORE tapping so the AI
 * actually knows what's on screen instead of guessing coordinates. */
export async function readScreen() {
  const { elements } = await DeviceControl.readScreen();
  return elements;
}

/** NEW: Finds the element whose label matches `text` and taps it
 * directly - no coordinates needed. Prefer this over tap(x, y) for
 * anything driven by the AI, since it only ever has to reason about
 * labels it can see (from readScreen), not pixel positions.
 * Returns { success, message? }. */
export async function findAndTap(text) {
  return DeviceControl.findAndTap({ text });
}

/** Raw coordinate tap - use only when you already have exact
 * coordinates (e.g. from a prior readScreen() result). */
export async function tap(x, y) {
  return DeviceControl.tap({ x, y });
}

export async function swipe(x1, y1, x2, y2, durationMs = 300) {
  return DeviceControl.swipe({ x1, y1, x2, y2, durationMs });
}

/** NEW: Scrolls the screen. direction is 'up' | 'down' | 'left' | 'right'. */
export async function scroll(direction = 'down') {
  return DeviceControl.scroll({ direction });
}

export async function typeText(text) {
  return DeviceControl.typeText({ text });
}
