# Live Island for Android

A native Android Dynamic-Island-style overlay that is **anchored to the physical camera cutout** instead of using a hard-coded top-center position.

## What is implemented in this build

- Physical `DisplayCutout` detection and anchoring.
- Portrait, landscape-left, landscape-right and upside-down fallback geometry.
- The collapsed island rotates/repositions with the physical camera location.
- Expansion grows inward from the camera anchor rather than moving the anchor.
- Manual X/Y fine adjustment for OEMs that report unusual cutout bounds.
- Shizuku integration with no root required.
- On expansion, overlapping status-bar groups are suppressed with `cmd statusbar send-disable-flag` and restored with `none` when the island collapses.
- Notification-driven live activities: calls, messages, navigation, alarms, downloads, Bluetooth transfers and payment/UPI notifications.
- MediaSession integration: real track metadata and previous/play-pause/next controls.
- Incoming/ongoing call notification actions when the dialer exposes Answer/Decline/Hang up actions.
- Real internal countdown timer with pause/resume and finish vibration.
- Charging animation/event.
- Empty-island bank/UPI QR shortcut. QR is chosen by the user, stored as a persistent document URI and can be biometric protected.
- Android platform biometric prompt with island success/failure state.
- NFC reader activity and island NFC state on a real NFC tag interaction.
- Bluetooth file sharing through Android's system share flow; transfer progress is mirrored when the Bluetooth stack posts it as a notification.
- Foreground service, boot restart preference and status-bar restoration on normal service shutdown.
- No preloaded demo account/data and no fake media controls.

## Status-bar behavior

When the island is collapsed, Android's status icons remain normal. When a top-anchored island expands far enough to intersect the left/right status areas, the app asks its Shizuku user service to run the appropriate SystemUI command:

- left collision: `clock notification-icons`
- right collision: `system-icons`
- collapse: `none`

This means root is not required, but **Shizuku must be running and authorized** for SystemUI icon suppression.

## Build

Open the project in a current Android Studio installation and build the `app` module. The project targets API 35, uses Java 17, and has only the Shizuku API/provider external dependencies.

## First-run setup on the phone

1. Install and start Shizuku using Wireless Debugging.
2. Open Live Island.
3. Allow “Display over other apps”.
4. Enable Live Island under Notification access.
5. Tap “Authorize Shizuku” and allow it.
6. Select a bank/UPI QR if you want empty-island QR behavior.
7. Tap “Start Live Island”.

## Important Android limitations

The NFC screen reacts to NFC interactions; it does not impersonate Google Wallet or a bank card. Secure wallet transaction state is not exposed to ordinary third-party apps. Navigation is mirrored from the navigation app's live notification, so it does not require a Google Maps API key.
