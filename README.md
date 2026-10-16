# Sim Warning

A minimal Android app that warns about unexpected loss of mobile network service.

Approved localized names: **Sim Warning**, **SIM-advarsel**, **SIM-Warnung**.

The consolidated requirements, acceptance scenarios, and remaining review points are in [SPECIFICATION.md](SPECIFICATION.md). This is the authoritative working specification.

Current version: 1.0.0. The user reported a successful beta phone test; this version includes their approved louder sounds (+4.75 dB). Setup, main status/switch, Settings/Help, real Android permissions, persisted preferences, foreground monitoring, cellular callbacks, warning notifications, original sounds, and boot resume are implemented. Validation includes 70 passing core tests, 33 staged API 35 Android checks, and live beta radio/restart smoke checks. Broader physical-phone coverage, full older-device classification, deep-sleep timing, and battery measurements remain pending.

The installable APK is [dist/SimWarning-1.0.0.apk](dist/SimWarning-1.0.0.apk). Read [APK_GUIDE.md](APK_GUIDE.md) for installation and device limits; [dist/BUILD_INFO.json](dist/BUILD_INFO.json) identifies the tested binary. The non-debuggable beta is signed for local testing and excludes the debug instrumentation host.

## Inspect in Visual Studio Code

Open [SimWarning.code-workspace](SimWarning.code-workspace). JetBrains' official Kotlin extension is installed on the development machine and recommended by the workspace.

You can also run `.\tools\open-vscode.ps1` to open the workspace with the prepared Java, Android SDK, and Gradle environment. On first launch, review any Kotlin extension terms or data-sharing choices.

Use **Terminal → Run Task**:

- **Sim Warning: unit tests** — domain tests.
- **Sim Warning: build core** — compile and test the Kotlin module (default Ctrl+Shift+B).
- **Sim Warning: build Android APK** — build the debug app.
- **Sim Warning: build beta APK** — build/lint the signed beta without the debug host.
- **Sim Warning: export tested APK** — verify signature, minimum permissions, exact-binary smoke evidence and original audio bytes, then copy the APK to `dist`.
- **Sim Warning: all checks** — core checks, Android lint, and APK build.
- **Sim Warning: build UI tests** — assemble app and instrumentation APKs.
- **Sim Warning: run UI tests on emulator** — execute the built tests on a booted emulator, separately from compilation to limit memory use.
- **Sim Warning: Android integration checks** — staged UI/service/real permission-dialog checks with isolated emulator data.

From the project terminal:

```powershell
.\tools\run-gradle.ps1 :core:test
.\tools\run-gradle.ps1 :core:build :app:assembleDebug :app:lintDebug
```

Generated outputs and reports are under `%USERPROFILE%\.gradle-simwarning\outputs\SimWarning`, outside OneDrive. Unit-test reports: `core\reports\tests\test\index.html`; APK: `app\outputs\apk\debug\app-debug.apk`. Old ignored outputs in the source folder are not the current results.

## Code map

- `core/src/main/kotlin/app/simwarning/core` — timing engine, cellular evidence classification, and persistent lifecycle coordinator contracts.
- `core/src/test/kotlin/app/simwarning/core` — specification-linked JUnit tests using virtual time.
- `app/src/main/java/app/simwarning/setup` — native Compose setup, stable element IDs, and strict JSON text-catalog loading.
- `app/src/main/java/app/simwarning/ui` — main screen, six Settings details, Help, and stable IDs.
- `app/src/main/java/app/simwarning/platform` — real grants/preferences, subscription callbacks, service/deadline/boot lifecycle, and notification/audio adapters.
- `app/src/main/assets/texts` — English, Danish, and German text catalogs.
- `app/src/androidTest` — instrumentation and UI Automator checks. The activity host is debug-only and absent from release builds.
- `app` — Android 8+ packaging with approved names/logo, minimal declared permissions, launcher, and special-use foreground service.
- `design` — approved design references, original logo, and two approved warning sounds.
- `tools` — terminal, build, and installation helpers.

See [TESTING.md](TESTING.md) for test scope, [TDD_EVIDENCE.md](TDD_EVIDENCE.md) for executed red/green evidence, and [ANDROID_FEASIBILITY.md](ANDROID_FEASIBILITY.md) for unresolved platform constraints. Passing domain tests does not prove screen-off timing or battery behavior.

Version 1.0.0 export: `.\tools\export-1.0.0.ps1`. This preserves historical beta evidence and records user acceptance separately; it does not claim an exact-release emulator smoke run.
