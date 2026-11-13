# Nanit Routines — Setup & Architecture Guide

A dedicated Android Phone + Wear OS (Pixel Watch) companion application for controlling nursery routines (Nanit Pro Camera, Nanit Sound + Light Machine, and Kip's Room smart lights).

---

## 🏗️ Architecture & Modules

```
NanitRoutines/
├── app/                  # Android Phone App (Jetpack Compose, Background Wear Listener)
├── wear/                 # Wear OS Pixel Watch App & Interactive Tile (ProtoLayout)
├── build_release.sh      # 1-step build, signing, version bump, and release pipeline
└── publish_to_play.py    # Headless Google Play Console publisher (Python + Play API v3)
```

### Key Components:
- **Phone App (`:app`)**:
  - **Token Management**: Checks JWT `exp` claims and proactively refreshes the 60-minute Nanit access token via `POST https://api.nanit.com/tokens/refresh` before every routine or telemetry call.
  - **PhoneWearListenerService**: Listens for `/nanit_routines/run_routine` messages from the watch via Google Play Wearable Data Layer, executes routines, updates widgets, and syncs status back to the watch.
  - **UI**: 2-tab consolidated layout (`Home & Controls` and `Settings`) with tactile haptic and visual spring feedback (`FeedbackActionButton`).
- **Wear OS App (`:wear`)**:
  - **KipsRoomTileService**: Swipeable round-watch Tile with **🌙 Naptime** and **☀️ Wakeup** buttons.
  - **WearRoutineDispatcher**: Triggers phone execution over the Wearable Data Layer or falls back to direct Wi-Fi WebSocket control if the phone is disconnected.

---

## 📁 Workspace Directory Structure

All projects follow this standard workspace organization under `/storage/emulated/10/Documents/`:

```
Documents/
├── Projects/                          # Source code projects
│   └── NanitRoutines/
├── Releases/                          # Production .aab and .apk binaries
│   └── NanitRoutines/
│       ├── NanitRoutines.aab          # Phone release bundle
│       ├── NanitRoutines.apk          # Phone installable APK
│       ├── NanitRoutines-wear.aab     # Pixel Watch release bundle
│       └── NanitRoutines-wear.apk     # Pixel Watch installable APK
└── .secrets/                          # Secure credentials (hidden)
    └── play-store-keys.json           # Google Cloud Service Account publishing key
```

---

## 🚀 Building & Publishing to Google Play

To increment version codes, compile release bundles, and immediately deploy both Phone and Watch updates to Google Play Internal Testing:

```bash
cd /storage/emulated/10/Documents/Projects/NanitRoutines
./build_release.sh
```

### Publishing Requirements:
- Service account key located at `/storage/emulated/10/Documents/.secrets/play-store-keys.json`.
- In Google Play Console:
  - Phone track: `internal`
  - Wear OS track: `wear:internal` (Form factors $\rightarrow$ Wear OS enabled).
