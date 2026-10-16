# Native setup component

The reusable `SetupFlow` implements the four approved screens in `UI_SETUP_SPEC.md`: language, explanation, interval, and access. It is now integrated by MainActivity/AppRoot with real Android access requests, AppPreferences/AppController, and the foreground monitor. The isolated component tests remain separate from real grant/denial/lifecycle tests.

## Integration boundary

- `deviceLanguage`: supported language tag or English fallback. Language changes immediately load the corresponding JSON catalog; native language names also come from catalog keys.
- `readReadiness`: side-effect-free snapshot of actual prerequisites. `ACCESS_REQUIRED` leaves Start disabled; `UNAVAILABLE` also prevents completion. Return `READY` only after real prerequisite verification. The component reads it on entry, return to the resumed lifecycle, after permission review, and immediately before starting.
- `onReviewPermissions`: MainActivity invokes actual version-appropriate requests/settings and advances a refresh token on Android's result callback. The component does not fabricate grants or request anything on earlier steps.
- `onStartMonitoring`: AppController re-checks prerequisites, persists approved preferences/completion, and accepts one monitoring start request through AndroidMonitorPlatform. Its Boolean result indicates acceptance, not proof of a continuously running service. A failed request allows retry; main status uses the actual lifecycle/readiness state.

Draft language, interval, and current step use saveable state. Successful completion locks further start taps. Committed preferences and main routing now persist through AppPreferences. Activity/process restoration is distinct from an agreed abandoned-setup draft policy; do not treat an abandoned or denied setup as complete.

The beta manifest now includes the real launcher and monitoring/boot/deadline components, plus the six justified permissions documented in APK_GUIDE.md. It excludes `SetupTestActivity`; that remains an unexported debug-only instrumentation host with no production overrides. AndroidX's additional app-scoped signature permission protects receiver compatibility. There is no location, internet, exact-alarm, wake-lock, or DND-policy request.

## Testing locally

Build before starting the emulator on this memory-constrained machine:

```powershell
.\tools\run-gradle.ps1 :core:check :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
.\tools\start-ui-emulator.ps1
# Wait until this returns 1:
. .\tools\use-android-tools.ps1
adb -s emulator-5554 shell getprop sys.boot_completed
.\tools\run-ui-tests.ps1
adb -s emulator-5554 emu kill
```

The runner accepts `-DeviceSerial`, `-TestClass`, and `-EvidencePath`. It verifies boot completion and treats JUnit failures as command failures even when Android's `am instrument` command exits zero. VS Code tasks expose build and execution separately.

The validated emulator configuration is API 35 AOSP x86_64, 1024 MB guest RAM, host graphics, Vulkan disabled, two cores, headless. The initial 1536 MB software-rendered attempt reached roughly 2.8 GB resident memory and was stopped before tests. No user apps were closed. The smaller host-graphics configuration booted and executed tests; compilation and emulator runs were kept separate. The start helper requires at least 3 GB available host memory and does not start a second connected emulator.

Dependencies are pinned to an SDK-36-compatible Compose BOM (2025.08.01), Activity 1.10.1, Lifecycle 2.9.2, and AndroidX instrumentation versions. Newer Compose versions require SDK 37, which is not installed. Integrated-app lint includes five dependency-update suggestions plus stylistic KTX/required-telephony-feature suggestions. Do not silently upgrade the toolchain during feature work. References: [Compose setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler), [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [Compose testing](https://developer.android.com/develop/ui/compose/testing).

Real Android grant/denial dialogs, persisted main routing, foreground notification/channel integration, and boot now have API 35 evidence. API 26 execution, physical-device telephony/audio, sleeping-phone timing, power, active revocation/process termination, and full screen-reader review remain separate gates. Current commands and scope are maintained in TESTING.md; the beta-specific handoff is APK_GUIDE.md.
