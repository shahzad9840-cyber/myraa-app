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

export async function tap(x, y) {
  return DeviceControl.tap({ x, y });
}

export async function swipe(x1, y1, x2, y2, durationMs = 300) {
  return DeviceControl.swipe({ x1, y1, x2, y2, durationMs });
}

export async function typeText(text) {
  return DeviceControl.typeText({ text });
}
