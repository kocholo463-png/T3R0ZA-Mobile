# T3R0ZA

Android companion app for Free Fire sessions.

## Included
- Full dashboard UI. No Mini Panel and no in-game overlay.
- Free Fire package detection and direct launch.
- Live device telemetry: refresh rate, memory pressure, thermal status, battery and Power Saver.
- Real network probe for TCP latency, jitter estimate and probe loss.
- Real DNS benchmark for 1.1.1.1, 8.8.8.8 and 9.9.9.9.
- Smart DNS selection only before a DNS session when DNS Lock is off.
- Stable DNS session through Android VpnService with user approval.
- Session detection using Android Usage Access.
- Manual aim/sensitivity profile controls without input injection.
- No root, no client modification, no auto-headshot and no aim-bot.

## Platform limits
A normal Android app cannot directly set or read Free Fire's internal FPS, recoil or sensitivity. Values that cannot be controlled by Android are shown as limitations instead of fake Boost controls.

## Build
The repository uses GitHub Actions with JDK 17, Android SDK 36 and Gradle 9.6.1.
