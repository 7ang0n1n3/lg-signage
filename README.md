# SignageApp

Android digital signage app designed for the **LG Display LD230EKS-FPN1** — a 23.1" strip LCD panel (585mm × 48mm, ~12:1 aspect ratio).

## Pre-built Release APK

A compiled, unsigned release APK is included in this repository at:

```
app/build/outputs/apk/release/app-release-unsigned.apk
```

Install directly to a connected device:

```sh
adb install -r app/build/outputs/apk/release/app-release-unsigned.apk
```

Or use `make install-release` to build from source and install in one step.

## Target Display — LG Display LD230EKS-FPN1

| Property | Value |
|---|---|
| Model | LD230EKS-FPN1 |
| Manufacturer | LG Display |
| Panel size | 23.1" (585mm × 48mm) |
| Aspect ratio | ~12:1 |
| Spec sheet | https://www.panelook.com/LD230EKS-FPN1_LG_Display_23.1_LCM_parameter_46385.html |

## Hardware

- **Display:** LG Display LD230EKS-FPN1, 585mm × 48mm
- **Orientation:** Portrait in Android (content rotated 90° via CSS/canvas to fill the long axis)
- **Min SDK:** 25 (Android 7.1)  |  **Target SDK:** 28  |  **Compile SDK:** 33

## Features

- **Multiple display modes:** world clock, lightning animation, fire animation, scrolling marquee, animated GIF eyes, and blank screen
- **Scheduling:** time-based automatic mode switching with per-day-of-week rules
- **On-device admin UI:** web interface served on port 8080 (`http://<device-ip>:8080/admin`)
- **REST API:** get/set settings and schedule via HTTP
- **Boot autostart:** app launches automatically on device boot
- **Instant updates:** settings changes apply immediately via `LocalBroadcastManager`

## Display Modes

| Mode | Description |
|---|---|
| `worldclock` | Rotating world clock display |
| `lightning` | Animated lightning effect |
| `fire` | Animated fire effect |
| `marquee` | Scrolling text with configurable color, speed, direction, and blink |
| `crime_eyes` | Animated GIF from the evil-eyes asset set |
| `blank` | Pure black screen |

## Building

Requires [Docker](https://www.docker.com/) and the `mingc/android-build-box` image.

```sh
# Build APK
make build

# Build and install to connected device
make install

# Clean build artifacts
make clean
```

The APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

## Admin Web Interface

While the app is running, a web server is available on **port 8080**:

| Endpoint | Method | Description |
|---|---|---|
| `/admin` | GET | Browser-based settings and schedule UI |
| `/api/settings` | GET | Read current settings as JSON |
| `/api/settings` | POST | Write settings |
| `/api/schedule` | GET | Read full schedule as JSON array |
| `/api/schedule` | POST | Replace full schedule |

## Schedule Format

Schedule entries are stored as a JSON array under the `schedule` SharedPreferences key.

```json
[
  {
    "id": "abc123",
    "enabled": true,
    "time": "09:00",
    "days": [1, 2, 3, 4, 5],
    "mode": "marquee",
    "marquee_text": "Good morning",
    "marquee_color": -1,
    "marquee_speed": 5
  }
]
```

- `days`: 0 = Sunday … 6 = Saturday. Empty array means every day.
- At each 30-second tick, the scheduler picks the latest past entry for today (falling back to yesterday's last entry if needed).

## Project Structure

```
app/src/main/
├── java/com/signage/app/
│   ├── MainActivity.java       # WebView host, BroadcastReceiver, URL builder
│   ├── SettingsActivity.java   # On-device settings UI
│   ├── WebServerService.java   # NanoHTTPD server (port 8080) + REST API
│   ├── SchedulerService.java   # Background scheduler (30s polling)
│   └── BootReceiver.java       # Auto-start on boot
└── assets/
    ├── worldclock.html
    ├── lightning.html
    ├── fire.html
    ├── marquee.html
    ├── crime_eyes.html
    ├── blank.html
    └── evil-eyes/              # Animated GIF assets
```

## SharedPreferences Keys

| Key | Type | Description |
|---|---|---|
| `display_mode` | String | Active display mode |
| `marquee_text` | String | Scrolling text content |
| `marquee_color` | int | Text color (ARGB) |
| `marquee_speed` | int | Scroll speed |
| `marquee_direction` | String | Scroll direction |
| `marquee_blink` | boolean | Enable blinking |
| `border_style` | String | Border animation style |
| `border_color_mode` | String | Border color mode |
| `border_color` | int | Border color (ARGB) |
| `border_shape` | String | Border shape |
| `crime_eyes_gif` | String | Selected GIF filename |
| `schedule` | String | JSON schedule array |
| `schedule_applied_id` | String | ID of last applied schedule entry |
