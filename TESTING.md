# Specification-first development

The product requirements are in SPECIFICATION.md. UI contracts and stable automation IDs are in UI_SETUP_SPEC.md and UI_SETTINGS_SPEC.md.

## First implementation slice

The `core` module is pure Kotlin/JVM. It deliberately has no Android dependency, real clock, notification player, background service, permissions, or network access. Its API accepts monotonic timestamps and observed inputs, returning explicit effects and the next required deadline. This lets the ordinary JVM tests cover minutes of behavior instantly and independently of emulator timing.

1. Write scenario tests linked to agreed requirement/acceptance IDs.
2. Compile the API skeleton and run the tests: establish an actual failing baseline.
3. Implement the minimum domain logic to satisfy those tests.
4. Run tests and build; add regression tests before fixing uncovered defects.
5. Add Android adapters and UI in later test-first slices. A passing core suite does not establish phone reliability.

## Running from VS Code

Open `SimWarning.code-workspace`. Use **Terminal → Run Task → Sim Warning: unit tests**. **Ctrl+Shift+B** runs the default core build task. The official JetBrains Kotlin extension is installed and recommended for code navigation; Gradle tasks do not depend on it. The language server's heap is capped at 768 MB in the workspace. Its first launch may require JetBrains terms/data-sharing choices; editor import behavior is separate from the verified command-line build.

From the project terminal:

```powershell
.\tools\run-gradle.ps1 :core:test
.\tools\run-gradle.ps1 :core:build
.\tools\run-gradle.ps1 :app:assembleDebug :app:lintDebug
```

Generated build outputs live outside OneDrive, under `~/.gradle-simwarning/outputs/SimWarning/core`. After a test run, view `reports/tests/test/index.html` there; machine-readable results are under `test-results/test`. Use `:core:test --tests '*MonitorEngineTest*'` to select the timing tests. To explicitly execute an unchanged suite again, append `--rerun-tasks`.

The wrapper pins Gradle and verifies its distribution SHA-256. The runner uses the installed Java/SDK, a dedicated `~/.gradle-simwarning` cache outside OneDrive, one worker, and bounded memory. On this machine, Java needs the project-local trust preparation in `tools/prepare-java-trust.ps1` before dependency download; it imports only roots already validated/trusted by Windows and does not disable TLS validation. That store is ignored by Git and is not a portable project asset. Standard Java trust works without it on machines that already trust their HTTPS environment.

## Traceability and limits

`MonitorEngineTest` covers domain portions of A01–A04, A09–A17, A19–A24, A28–A36, and A38, plus policy and clock invariants. `ConnectivityAggregationTest` covers the pure observation portions of A05/A06 and uncertainty handling. These are executable domain scenarios, not proof of Android integration.

The integrated app has **70 passing JVM tests** and **33 staged API 35 Android checks**. The original 19 setup/catalog checks remain, supplemented by 8 main/Settings/adapter checks, 4 real service/airplane/sample/off-boot checks, and two actual Android grant/denial flows run in separate clean fixtures. Setup layout checks exercise all three languages at 320dp width and normal/double font size. Saveable restoration uses StateRestorationTester; real main/activity recreation and saved-off behavior also have Android coverage. This is not a claim that every UI acceptance case or physical device has passed.

The exact signed beta is additionally exercised through public emulator radio controls, with real production 30-second/2-minute timing, mobile-data-off handling, WLAN present during cellular loss, background visual posting, notification sound/vibration metadata, recovery, and actual reboot startup. See TDD_EVIDENCE.md and docs/tdd/beta-smoke.json. No app input/timing overrides are shipped.

Run Android checks after building and booting the single emulator:

```powershell
.\tools\run-gradle.ps1 :core:check :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleBeta :app:lintDebug :app:lintBeta
.\tools\start-ui-emulator.ps1
# Wait for sys.boot_completed=1, then:
.\tools\run-android-checks.ps1
```

The staged runner is restricted to an emulator because it resets app data and runtime-permission flags. Its default final reset returns to a fresh fixture. `-KeepEmulatorState` retains the granted/enabled setup for `tools/smoke-beta.ps1`, which installs the beta itself and performs the longer live radio/restart checks. A quick 27-check suite is available with `tools/run-ui-tests.ps1 -CleanAppData`; explicit class filters must be quoted in PowerShell. Do not run all grant/denial classes in a single shared fixture. Screenshot artifacts are in design/native-app. Build and emulator execution stay separate on the constrained host.

Remaining coverage includes physical/eSIM/multi-SIM and Wi-Fi-calling/data-only classification, API 26–29 cases, active permission revocation and process termination, manufacturer battery restrictions, deep-sleep deadlines and power use, audible/haptic output, full Settings accessibility/large-font/screen-reader review, and other remaining U/S scenario portions. Launcher language follows Android resources and needs additional platform review. Runtime phone/notification grants and boot have API 35 evidence, not blanket cross-device approval.

Do not reduce timing values merely to speed up Android tests or add hidden release overrides. Inject time in domain tests; use isolated test-only adapters for UI scenarios, and separately verify real platform behavior. Production service scheduling must reconcile sleep constraints identified in ANDROID_FEASIBILITY.md before release.

## Evidence

Actual red/green commands, counts, and results will be recorded in `TDD_EVIDENCE.md` after execution. Never mark a specification requirement complete solely because its domain portion passes.
