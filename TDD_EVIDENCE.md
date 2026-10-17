# Executed TDD evidence

15 October 2026. This records actual runs, separately from the future acceptance plan.

## Original specification suite

1. Wrote the Kotlin contract/API skeleton with unimplemented entry points, and 47 JUnit tests before functional implementation.
2. Ran `tools/run-gradle.ps1 :core:test`: compilation succeeded; **47 executed, 47 failed**. See [red-baseline.json](docs/tdd/red-baseline.json) for suite counts, failure types, timestamp, and test-source SHA-256 values.
3. Implemented MonitorEngine and aggregateServices against those tests.
4. Ran the same suite after moving generated outputs outside OneDrive: **47 executed, 0 failed**. See [green-first-slice.json](docs/tdd/green-first-slice.json). Test-source hashes match the original red baseline.

Two setup failures occurred before/around these runs: Java's certificate trust for dependency downloads and file locks in OneDrive-generated caches/results. Neither is counted as a red domain-test result. The build now uses a dedicated cache/output location outside OneDrive and a project-local Windows-validated Java trust store.

## Delayed-callback regression cycle

Added four tests before fixes, covering completed recovery/startup confirmation whose timer delivery was delayed, and ending DND/silent mode after missed deadlines. Ran the suite: **51 executed, 4 failed**, with the original 47 still passing. See [red-regressions.json](docs/tdd/red-regressions.json).

Implemented reconciliation of elapsed recovery and skipped overdue alerts when suppression ends. An intermediate run caught a regression in the existing independent-output policy test; the implementation was corrected without weakening that test.

Final executed suite: **51 tests, 0 failures, 0 errors**. `:core:test :core:build :app:assembleDebug :app:lintDebug` completed successfully. A subsequent resource/configuration-only check added explicit local-only backup rules and raised bounded Metaspace from 384 to 512 MB; `:core:build :app:assembleDebug :app:lintDebug` also succeeded, reusing the unchanged passing unit results. Android lint finished with **0 issues**. [green-final.json](docs/tdd/green-final.json) records test counts, hashes, APK checksum, and command provenance.

The APK manifest was inspected with AAPT2: minSdk 26, targetSdk 36, approved app label, and no launcher or requested permissions in this build-only shell. APK creation and lint do not establish Android runtime behavior. A non-fatal SDK XML version warning remains because the installed command-line tools emit newer metadata than this build-plugin parser; assembly and lint both passed.

## Scope limits of the monitoring-domain slice

The VS Code unit-test task command was also verified through `powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/run-gradle.ps1 :core:test`. It succeeded with tasks up to date, reusing the 51 passing results; this was not a second execution of the tests.

These tests run on the JVM with explicitly supplied monotonic time. They do not depend on an emulator and do not claim hardware/Android scheduling reliability. No UI, real permission, notification/audio, boot, SIM, or battery end-to-end check is represented as complete. The Android module is a packaging scaffold without a launcher or monitoring service; that prevents a partial app from presenting unimplemented monitoring as active.

## Native setup slice

Wrote six `SetupStateTest` cases and the unimplemented navigation/language API first. `:core:test` executed **57 tests, 5 failures**: the five unimplemented behaviors failed, the interval invariant already passed, and all 51 original monitoring tests remained green. See [red-setup-state.json](docs/tdd/red-setup-state.json).

Before implementing the rendered screens or catalog loader, wrote 12 Compose/UI Automator checks and two catalog checks against empty UI/unimplemented loading. Built and installed both APKs on the API 35 AOSP x86_64 emulator. **14 executed, 14 failed** with missing UI selectors and unimplemented catalog loading; see [red-setup-ui.txt](docs/tdd/red-setup-ui.txt). The earlier install attempt before Android finished booting is a setup failure, not a red test result. The test runner now explicitly checks boot completion.

Implemented the setup state, four native screens, JSON catalogs, stable selectors, saveable state, lifecycle readiness refresh, start-time prerequisite re-check, and duplicate-start guard. The combined core/build/lint command succeeded. **57 core tests passed**, then the same **14 Android tests passed**. Original test-source SHA-256 values match the red baseline; see [source hashes](docs/tdd/setup-test-source-baseline.json), [first setup green evidence](docs/tdd/green-setup-first.json), and [instrumentation output](docs/tdd/green-setup-ui-attempt1.txt).

Five additional validation checks cover Android Back dispatch, readiness on return to a resumed activity, popup resource IDs, all locales at 320dp width and 1x/2x font scale with text-overflow assertions, and native screen capture. These are additional validation of the component, not an invented red baseline. The final build command is `:core:check :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug`; it passed and reused the unchanged passing core results. The merged release manifest was inspected: no activities/test host, no runtime permission requests, only AndroidX's app-scoped signature receiver permission. Lint reports no errors and five pinned-dependency update suggestions.

The extended emulator run executed **19 checks, 1 failure**: the compact-width text-overflow assertion flagged the app-name header. See [initial accessibility output](docs/tdd/red-setup-accessibility.txt). Screen capture revealed that a fixed body-text color overrode the primary button's white content color. The implementation was adjusted to constrain the header and give it a larger line height, and to inherit component content colors.

A second diagnostic run still had one failure, now on progress text: a 211px text node reported a 748px paragraph layout width while the paragraph height fit exactly. See [layout diagnostics](docs/tdd/setup-layout-diagnostics.txt). The test's aggregate `hasVisualOverflow` predicate was too broad for these semantics results; it did not establish actual glyph clipping. The check was corrected to assert actual line left/right bounds (1px rounding tolerance), no vertical overflow/ellipsis, and text-node containment within the 320dp screen (0.5dp rounding tolerance). This preserves the no-clipping requirement rather than widening the screen or shrinking text. [AndroidX TextLayoutResult source](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/TextLayoutResult.kt) distinguishes constrained text-node size from paragraph dimensions. Original red-baseline tests remain unchanged.

Final executed Android suite: **19 tests, 0 failures**, including all four screens across English/Danish/German at 320dp width and normal/double font size. See [final instrumentation output](docs/tdd/green-setup-ui-final.txt) and [final provenance/APK hashes](docs/tdd/green-setup-final.json). The last debug/release/test-APK assembly and lint command succeeded; the 57 previously executed passing core results were reused after UI-only changes. Five native screenshots were retrieved to `design/native-setup` and visually reviewed. The emulator was then stopped to release host memory.

The test activity exists only in debug sources. Tests inject permission readiness and start acceptance; no real grant dialog, persisted completion/main destination, or monitoring service is claimed. Full activity recreation, API 26 execution, Settings, device telephony, notifications, sleep timing, boot, and battery checks remain pending. See [component integration boundary](docs/SETUP_IMPLEMENTATION.md).

## Integrated Android test APK

Added seven lifecycle/persistence tests and six cellular-evidence tests against unimplemented contracts. Actual red run: **70 core tests, 13 failures**, with the existing 57 passing. See [core red results](docs/tdd/red-platform-core.json). Added four Android adapter and four real main/Settings navigation tests before implementing those adapters/screens: **8 Android tests, 8 failures** against the skeletons; see [Android red output](docs/tdd/red-platform-android.txt). A misquoted class filter failed before executing tests and is not counted as a red test result.

Implemented persisted setup/off/language/interval, access checks, real launcher, main and Settings/Help UI, public service/subscription callbacks, foreground/deadline/boot lifecycle, and system-managed warning sounds/vibration. **70 core tests pass**; see [suite counts](docs/tdd/green-platform-core-suites.json). The original four new test-source hashes remain unchanged from [the platform baseline](docs/tdd/platform-test-baseline.json). A compile error exposed SIM broadcast constants not present in the public SDK; those references were removed, retaining public subscription listeners instead of hidden-API workarounds.

The first integrated UI/adapter run passed **27 checks**. Four additional emulator-only service checks initially passed three and failed one because the foreground status notification was not yet visible. Production now requests immediate, quiet foreground presentation; the test waits for actual system posting rather than assuming synchronous visibility. No arbitrary sleep or shortened production deadline was introduced. [Initial lifecycle result](docs/tdd/monitoring-lifecycle-attempt1.txt).

The reproducible staged runner then passed **33 checks**: 27 UI/adapters, 4 actual service/airplane/sample/off-boot checks, one actual permission-denial flow, and one actual grant/completion/start flow. Grant and denial each use clean app data plus explicitly reset Android permission flags. Android-owned dialogs are selected by their resource IDs. See [stage summary](docs/tdd/green-platform-stages.json) and the four `green-platform-*.txt` outputs. The grant test retains the real enabled service for subsequent beta smoke checks; default tooling otherwise resets the fixture.

The signed, non-debuggable beta has no debug test host or release input overrides. Live beta checks use only Android/emulator radio controls and the service's read-only dump, with unchanged 30-second and 2-minute production timings. The first successful run observed the visual warning at **30,004 ms** and the system alert at **120,039 ms** after ordinary loss; WLAN was connected and the app screen was closed. Android's notification record selected the approved connection-loss sound URI and three-pulse vibration pattern. The warning persisted during recovery, cleared quietly after stable recovery, and the real boot receiver resumed enabled monitoring following an emulator reboot. See [pre-theme smoke record](docs/tdd/beta-smoke-before-theme.json). These are awake API 35 emulator observations, not sleeping-phone timing or acoustic/haptic measurements.

Final visual QA prompted an explicit light system-bar theme so status/navigation icons contrast with the white app. Final APK metadata, audio-byte/signature verification, and the exact handed-over binary are recorded by `tools/export-beta.ps1` in `dist/BUILD_INFO.json`. The final smoke output is `docs/tdd/beta-smoke.json`; a binary hash mismatch blocks export.

The first theme lint run caught `windowLightNavigationBar` as API 27-only with a minimum of 26. It was moved to `values-v27`; API 26 retains a black navigation bar with light icons. No lint baseline or suppression was used to hide the compatibility error. The theme-only rebuild reuses the unchanged core logic/tests; the handed-over beta is smoke-tested again after this resource correction.

The final theme-corrected beta observed visual posting at **30,040 ms** and the scheduled system alert at **120,176 ms**, with normal mobile-data-off behavior, Wi-Fi present during cellular loss, warning retention during recovery, and quiet clearing. Its warm emulator reboot exceeded the harness's original 100-second readiness limit, so that command exited 1; this is preserved in [raw timeout evidence](docs/tdd/beta-smoke-reboot-timeout.json). Follow-up ADB inspection confirmed Android boot completion and the enabled service automatically running/connected without an app launch. The installed APK's on-device SHA-256 matched the final artifact. [Combined final evidence](docs/tdd/beta-smoke.json) retains the timeout and explicitly records the later verification; it does not portray the original command as uninterrupted green. The future emulator readiness allowance is 240 seconds, without changing any application grace/alert timing.

Export completed successfully: `dist/SimWarning-0.2.0-test.1.apk`, SHA-256 `430FDF3942BD60C0D018A3388F39E23EA34E15A0FB36F934B0AFC64B2B2E3BC4`. The exact installed/exported beta is signed, non-debuggable, has only MainActivity, contains both approved WAVs byte-for-byte despite optimized ZIP paths, and requests only the documented permissions. Final debug and beta lint each report **0 errors, 10 warnings** (dependency/style/feature suggestions). See `dist/BUILD_INFO.json`, `dist/SHA256SUMS.txt`, and `APK_GUIDE.md`. The export helper was adjusted to recognize the current AAPT2 `minSdkVersion` output field; the SDK requirement itself was not relaxed.

Remaining gates include physical multi-SIM/eSIM, Wi-Fi calling/data-only and API 26–29 behavior, active permission revocation/process termination, manufacturer battery policies, deep-sleep deadlines, measured power, acoustic output, full screen-reader/layout review, and Play service-type approval if distributed there. The APK is a local test build, not a declaration of full specification compliance.


## Version 1.0.0 — 2026-10-15

User reported successful beta testing on their phone and requested only louder audio and version 1.0.0. Both approved previews replace packaged WAVs (+4.75 dB, same melodies/durations, zero clipped PCM samples). Version code increments to 3. Localized version wording updated; application logic and notification channels unchanged. Release build/lint, signature/manifest and packaged audio hash validation are the relevant delta checks. No new behavior tests were added for gain/metadata changes. Prior 70 core/33 Android test and beta smoke evidence remains historical; no exact-1.0.0 emulator smoke run is claimed. See release acceptance JSON and dist build information.


## Version 2.0.0 — 2026-10-17

User requested version 2.0.0 with sounds 80% louder. Implemented as 1.8x PCM RMS relative to 1.0.0 (+5.105 dB), with smooth tanh saturation and 95% peak headroom. This adds harmonics and changes timbre; it is not an acoustic/perceptual loudness guarantee. Source originals and 1.0.0 audio are preserved. Generator checks RMS ratio tolerance, unchanged frame count/format, zero preservation and zero digitally clipped samples. See design/v2-audio-measurements.json. Packaged WAV hashes are checked at export. Only resource audio, version labels and metadata changed; full suite/emulator tests were deliberately not rerun. No physical test of 2.0.0 is claimed.
