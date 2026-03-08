# Changelog

All notable changes to SignageApp are recorded here.

## [Unreleased]

## [1.0.0] — 2026-03-08

### Added
- **Display modes:** world clock, lightning, fire, animated marquee, crime-eyes GIF, and blank screen
- **Marquee options:** configurable text, color, speed, direction, and blink
- **Border options:** style, color mode, custom color, and shape
- **Crime-eyes mode:** selectable animated GIF from bundled `evil-eyes/` asset set (evil-eye-1.gif, evil-eye-2.gif, evil-eye-3.gif)
- **Admin web UI** served by NanoHTTPD on port 8080 (`/admin`)
- **REST API** for settings (`GET/POST /api/settings`) and schedule (`GET/POST /api/schedule`)
- **Scheduler service** with 30-second polling, day-of-week rules, and fallback to yesterday's last entry
- **BootReceiver** for automatic app launch on device boot
- **Instant settings propagation** via `com.signage.app.SETTINGS_CHANGED` local broadcast
- **Portrait/landscape rotation** — all display pages rotate content 90° via CSS transform or canvas translate/rotate to fill the 585mm long axis
- **Docker-based build** using `mingc/android-build-box` via `make build`
- **Snake border animation** — two snakes starting from opposite corners
