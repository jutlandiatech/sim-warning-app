# Sim Warning — version 2.0.0

Version 2.0.0 (version code 4). Installable file: `dist/SimWarning-2.0.0.apk`. The user reported successful beta testing on their phone and approved both louder sound previews. Version 2.0.0 increases both sounds by 80% in PCM RMS relative to 1.0.0 (+5.105 dB). Smooth saturation keeps peaks at 95% without digital clipping and adds harmonics for a fuller, brighter tone. Notes and duration are unchanged. This signal-level increase does not guarantee 80% greater perceived loudness on every phone. Monitoring behavior is unchanged.

Install over version 1.0.0 or the beta to retain setup, settings and notification preferences. The same local signing identity is used. Android notification volume, DND and channel settings still apply. A custom sound selected in Android notification settings remains your choice; use Settings → Preview sound and Test warning after updating to check playback on your phone.

Version 2.0.0 has not yet been tested on a physical phone. Release verification covers build/lint, signature, manifest, SDK levels and exact packaged audio hashes. The 70 core tests and 33 Android checks and radio/reboot smoke evidence are from the prior beta; they are not claimed as fresh emulator runs of 2.0.0. See `docs/tdd/release-2.0.0-acceptance.json`.

## Install and try

1. Copy the APK to your Android phone and open it. If Android asks, allow installation from the app you used to open the file.
2. Choose Dansk, English, or Deutsch and the repeat interval. Review the phone-status and notification permissions, then tap Start monitoring.
3. In Settings, use Preview sound or Test warning to check both louder sounds and notification presentation. Your phone's DND, silent/vibrate, volume, and notification settings apply.
4. Keep the quiet monitoring notification enabled. You can close the app screen. Switch Monitoring off before manually disabling all SIMs; airplane mode is detected automatically.

The APK contains the real launcher, setup, main status/switch, six Settings pages, Help, local settings, foreground service, SIM callbacks, boot receiver, and system notifications. Its native code is compatible with API 26+, and the emulator checks use API 35. It is signed with the local development identity, is not debuggable, and excludes the debug instrumentation host. It is not a Play Store release.

## Timing and device limits

- The tested domain rules remain 30 seconds for ordinary loss/recovery, 5 minutes after startup/re-enable/airplane exit, selected 2/4/6/8/10-minute loss repeats, and separate 10-minute unreadable-status repeats.
- Android may delay deadlines in deep sleep or under battery restrictions. This build uses callbacks, a single next deadline, and standard inexact idle-allowed alarms. It has no continuous wake lock, exact-alarm access, battery exemption, or internet polling. Exact sleeping-phone timing is not established.
- The public API 30+ adapter uses cellular registration records and excludes WLAN registration. On API 26–29 it uses positive cellular voice-bearer evidence and otherwise stays conservative; some data-only, emergency-only, or Wi-Fi-calling combinations may appear as status unavailable. Physical/eSIM/multi-SIM and manufacturer variations need phone testing.
- Enabled monitoring resumes after startup and unlock where Android permits. After Force stop or a system stop, reopen the app. A process prevented from running cannot reliably announce its own inactivity; the main screen shows actual readiness when opened.
- Notification placement, sound/vibration delivery, and launcher-label language are partly controlled by Android. The main UI uses the chosen catalog language; launcher labels follow available Android locale resources. Three/two short vibration pulses use provisional 180ms pulses and 140ms gaps, with no separate quantified physical feel review.
- The user reported a successful beta phone test. Broader device coverage remains outstanding. The headless emulator validates requests, channels, and scheduling, not what a person hears or feels. Help translations and full screen-reader/device-size coverage also remain review items.

## Permissions and local data

Runtime prompts: phone status (`READ_PHONE_STATE`) and notifications (`POST_NOTIFICATIONS`, Android 13+). Normal declarations: foreground service, its special-use type, boot completed, and vibration. AndroidX also declares its app-scoped signature receiver permission. The manifest requests no internet, location, contacts, SMS, call logs, phone-number, microphone, camera, storage, wake-lock, exact-alarm, or DND-policy access.

Settings stay in local app preferences with backups disabled. The app does not store SIM identifiers or phone numbers. The service's ADB dump exposes only state names and monotonic deadlines for local testing; it provides no input overrides and writes no telemetry file.

See `TDD_EVIDENCE.md`, `docs/tdd/green-platform-stages.json`, and the final beta smoke evidence for actual validation. `dist/BUILD_INFO.json` and `dist/SHA256SUMS.txt` identify the handed-over binary.
