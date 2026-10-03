# Inbox (KompaktX fork, notifications-only)

A stripped-down fork of [KompaktX](https://github.com/ScreenSensitive/KompaktX) for the
**Mudita Kompakt** that keeps exactly one feature: **seeing your notifications**.

The Kompakt's OS (MuditaOS K) intentionally has no notification tray — apps ring and vibrate,
but there is no UI to show *what* arrived. Inbox fixes that with a plain list:

- Open the app → every active notification: **app · title · sender · text · time**
- **Tap** a row → jumps into the source app at that notification (deep-link via its contentIntent)
- **Swipe** a row (or tap ✕) → dismisses it
- **Clear all** button
- Optional: a silent, ongoing **"N unread"** marker notification (SECRET visibility, min
  importance — never rings, never shows on lockscreen) so you can tell something waits
  without opening the app
- No polling in the background: the listener only keeps an in-memory snapshot updated by
  system callbacks; the list is read when you open the app

## What was removed vs. KompaktX

Everything except the notification read/dismiss/deep-link path:

- the overlay system tray + touch strips (`OverlayService`, `OverlayPanelManager` — ~3.9k lines)
- heads-up popups and inline replies
- the accessibility service (hardware-button interception)
- quick settings, media widget, recent-apps row
- e-ink controller, custom tones, preference screens
- Compose entirely — replaced with plain XML layouts + RecyclerView

## Permissions

- `BIND_NOTIFICATION_LISTENER_SERVICE` (the service binding) — granted as "Notification access"
- `POST_NOTIFICATIONS` — only for the optional unread marker

No overlay permission. No accessibility. **No network permission at all** — the app cannot
make network calls.

## Build

Requires JDK 17 + Android SDK (build-tools 34):

    ./gradlew assembleRelease
    # → app/build/outputs/apk/release/app-release.apk (debug-signed)

## Install on the Kompakt

1. Copy the APK to the phone (Mudita Center → Manage Files → App Installers, or
   `adb install inbox.apk` with USB debugging enabled)
2. Open **Inbox** → *Grant access* → enable notification access for Inbox
   (if the system UI hides that entry, over adb:
   `adb shell cmd notification allow_listener com.kompaktx.app/com.noti.restore.service.KompaktXListener`)
3. Done — open Inbox whenever it pings to see who it was.

## License

GPL v2, same as upstream KompaktX.
