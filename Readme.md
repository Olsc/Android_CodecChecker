<div align="center">

# 📱 Android CodecChecker

**Device Video Codec & Performance Test Tool**

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android)](https://www.android.com)
[![Min SDK](https://img.shields.io/badge/minSdk-24-brightgreen)](app/build.gradle.kts)
[![Target SDK](https://img.shields.io/badge/targetSdk-36-blue)](app/build.gradle.kts)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-yellow)](#license)

[中文版](./README.zh.md)

</div>

## Project Overview

Android CodecChecker is a diagnostic tool for Android devices that detects hardware/software video codec support and measures decoding performance. It generates comprehensive device reports (HTML + TXT), making it ideal for device compatibility testing, video app development debugging, and hardware evaluation.

## Features

| Category | Features |
|----------|----------|
| **Device Info** | Manufacturer, model, hardware platform, Android version, kernel version, build fingerprint |
| **CPU Detection** | Architecture, core count, frequency range, features (NEON, etc.), implementer/part decoding |
| **Memory Info** | Total RAM, available RAM, Swap size |
| **Codec Scanning** | Full `MediaCodecList` scan, hardware/software classification, encoder/decoder separation |
| **Video Format** | H.264/AVC, H.265/HEVC, VP8, VP9, AV1, MPEG-4, VC-1, Dolby Vision, MPEG-2, H.263 |
| **Audio Format** | AAC, MP3, AC-3, E-AC-3, FLAC, Opus, Vorbis, WMA, AMR, TrueHD, DTS |
| **Performance Test** | Decoder creation timing, basic decode validation, resolution/fps capability matrix (1080p→8K) |
| **Concurrent Test** | Max concurrent H.264 & HEVC hardware decoder sessions (multi-instance stress test) |
| **Scoring** | 0–100 comprehensive score based on HW decode, concurrent capability, resolution, and RAM |
| **Report Export** | Full HTML report with dark theme + plain text summary → `/sdcard/CodecCheckerReport/` |

## Quick Start

### Build from Source

```bash
# Clone
git clone https://github.com/your-org/Android_CodecChecker.git
cd Android_CodecChecker

# Build debug APK (requires Android SDK)
./gradlew assembleDebug

# APK location:
#   app/build/outputs/apk/debug/app-debug.apk
```

**Requirements:**
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17+
- Android SDK platform 37

### Install & Run

1. Build the APK and install via Android Studio, or run:
   ```bash
   ./gradlew installDebug
   ```
2. Open the app → tap **▶ Start Test**
3. The 5-step detection runs automatically:
   - Device & CPU Info → Codec Summary → Format Support → Decoder Performance → Conclusion
4. Tap **📤 Export Report** to save the full report

## Report Output

Reports are saved to the device at:

```
/sdcard/CodecCheckerReport/
├── codec_report_20260708_143025.html    ← Full HTML report (dark theme)
└── codec_report_20260708_143025.txt     ← Plain text summary
```

> **Note on file access:**
> - **Android 10+**: Reports are written via MediaStore and accessible through the Downloads app / Files by Google
> - **Android 9 and below**: Direct file access requires `WRITE_EXTERNAL_STORAGE` permission
> - Optionally grant **"Allow access to manage all files"** in Settings for direct file manager browsing

### HTML Report Sections

| # | Section | Content |
|---|---------|---------|
| 1 | Device Info | Manufacturer, model, hardware, Android/kernel version, fingerprint |
| 2 | CPU Info | Architecture, cores, frequency, features, implementer |
| 3 | Memory Info | Total/available RAM, Swap |
| 4 | Decoder Overview | HW/SW decoder/encoder counts |
| 5 | Video Format Support | Known video MIME type support matrix |
| 6 | Audio Format Support | Known audio MIME type support matrix |
| 7 | HW Decoder Details | Per-codec profiles, resolutions, color formats |
| 8 | Decoder Performance | Resolution capability matrix (1080p30→8K60) + init timing |
| 9 | Concurrent Decoding | Max concurrent H.264 & HEVC streams |
| 10 | Conclusion | Summary + comprehensive score + rating |

## Architecture

```
app/
├── build.gradle.kts                         # Module build config
└── src/main/
    ├── AndroidManifest.xml                  # Permissions & Activity declarations
    ├── res/
    │   ├── values/strings.xml               # English strings
    │   ├── values-zh/strings.xml            # Chinese strings
    │   └── values/themes.xml                # Dark theme
    └── java/com/olsc/codecchecker/
        ├── MainActivity.kt                  # Main UI (pure code, no XML layout)
        ├── collector/
        │   ├── DeviceInfoCollector.kt       # Device/CPU/memory info
        │   ├── CodecInfoCollector.kt        # Codec enumeration & classification
        │   └── DecoderStressTester.kt       # Decoder performance & concurrency tests
        └── report/
            └── ReportGenerator.kt           # HTML + TXT report generation & export
```

### Detection Flow

```
Start → Device Info (CPU/MEM/SOC) → Codec Scan (MediaCodecList)
→ Format Support Check → Decoder Stress Test (Init + Concurrency)
→ Conclusion (Score + Rating) → Display Results / Export Report
```

## Tech Stack

| Component | Library / Tool |
|-----------|---------------|
| Language | Kotlin |
| UI | Pure code (no XML), dark theme |
| Async | Kotlin Coroutines |
| Codec API | `android.media.MediaCodecList`, `MediaCodec`, `MediaCodecInfo` |
| Export (API 29+) | `MediaStore.Downloads` |
| Export (API 28-) | Direct file I/O |
| Min SDK / Target | 24 / 36 |
| Build | AGP 9.2.1, Kotlin DSL |

## Multi-language

| Language | File |
|----------|------|
| 简体中文 (Default) | `res/values-zh/strings.xml` |
| English | `res/values/strings.xml` |

System automatically loads the correct language based on device locale.

## Screenshots

> *Screenshots coming soon — contributions welcome!*

<details>
<summary>Preview of HTML Report Theme</summary>

The HTML report features a custom dark theme with:

- Gold accent headers (`#d4af37`)
- Monospace data tables on dark backgrounds
- Color-coded status badges (green ✅ / red ❌ / blue ℹ️)
- Responsive layout for mobile viewing

</details>

## License

```
MIT License

Copyright (c) 2026 OLSC

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions...
```

---

<div align="center">

Built with ❤️ for Android developers and device testers

</div>
