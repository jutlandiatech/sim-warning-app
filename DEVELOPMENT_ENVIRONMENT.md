# Android environment inspection

Read-only inspection on 15 October 2026. No software installed and no emulator started.

| Item | Observed result |
|---|---|
| Android Studio | Installed under `%ProgramFiles%\Android\Android Studio`; product version AI-262.9437.185.2621.16467767. |
| Bundled Java | jbr\bin\java.exe runs successfully; OpenJDK 25.0.3. Compatibility with selected Gradle/Android build plugin must be verified before choosing the build runtime. |
| PATH tools | java, javac, gradle, adb, emulator, and sdkmanager not found through command discovery in this session. |
| SDK configuration | ANDROID_HOME, ANDROID_SDK_ROOT, and JAVA_HOME not found in the inspected process environment; no SDK path found in the inspected user environment values. |
| SDK files | No SDK found in standard LocalAppData location or checked common C:/D: Android directories; no ADB, sdkmanager, or emulator executable found within Android Studio installation. This is not an exhaustive disk search. |
| Emulator configuration | No AVD entries found in the standard user .android\avd location; no emulator/ADB/Studio processes found in this session. |
| Connected devices | Not established: without ADB, no `adb devices` query can be performed. This does not mean no phone is physically connected. |
| Physical memory | Native Windows memory query reported 15.83 GiB total and 4.02 GiB available, 74% load at inspection time. Availability fluctuates; original 1.52 GB estimate is not the current reading. |
| Android project | No native project, wrapper, SDK configuration, or compiled prototype exists yet. |

The initial CIM memory query was unavailable; the successful reading used Windows GlobalMemoryStatusEx instead.

Before the timing experiment, locate an existing custom SDK or complete SDK setup, choose a compatible build runtime/toolchain, and obtain ADB access to a physical phone (preferred for power/telephony) or one appropriately sized emulator for preliminary integration. Recheck memory before launching an emulator. Studio plugin tooling JARs do not substitute for a standalone Gradle distribution or project wrapper.

No actual screen-off/Doze timing, service-start, or battery experiment has run. Tool installation and production implementation are separate next steps, not part of this inspection.

## Installation update — 15 October 2026

The user subsequently authorized installation of missing development tools. The original inspection above is historical.

- Official Android command-line tools 15859902 installed in `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`; downloaded ZIP verified against the published SHA-256.
- Java-based sdkmanager could not validate the network certificate chain. A Windows-root-store attempt did not resolve it. Installation succeeded using the official Android CLI with `--no-metrics`, without disabling HTTPS verification or altering certificate stores. Future Gradle dependency downloads still need verification; Gradle's version check does not prove Maven repository access.
- Installed ADB/platform-tools 37.0.1, Android 16/API 36 platform revision 2, Build Tools 36.0.0, and emulator 37.2.12. Android 17/API 37's requested package identifier was not available from the queried repository; it was not installed.
- Installed checksum-verified Gradle 9.3.1 at `%LOCALAPPDATA%\SimWarning\Tools\gradle-9.3.1`. Verified `gradle --version` using the existing Android Studio Java 25.0.3 runtime; no additional JDK installed.
- Configured user ANDROID_HOME, JAVA_HOME, and PATH. Existing terminals may need reopening; dot-source [tools/use-android-tools.ps1](tools/use-android-tools.ps1) to configure a current terminal.
- Verified `adb version`, `adb devices -l`, `aapt2 version`, `emulator -version`, and `emulator -accel-check`. WHPX is installed and usable. ADB reported no attached debugging devices.
- Installed Android 15/API 35 AOSP x86_64 system image, revision 2, without Google Play services. Created SimWarning_API35 using the Pixel 4 device definition, 2 CPU cores, 2048 MB RAM, and 2 GB data partition; profile is under `%USERPROFILE%\.android\avd\SimWarning_API35.avd`.
- AVD creation printed a missing image-specific devices.xml warning but succeeded. Verified saved CPU/RAM/device/ABI/image-path values, the nonempty 1,622,147,072-byte system image, kernel, package metadata, and `emulator -list-avds` returning SimWarning_API35. No emulator has been launched, so boot/runtime operation remains unverified.

Reproducible installation/configuration scripts are in [tools](tools/). They write to standard user tool/SDK locations and require appropriate filesystem/network authorization. SDK and Gradle downloads are kept outside the repository. No application code, build wrapper, or test execution is implied by tool installation.

## Test-first project setup — 15 October 2026

- Added a checksum-pinned Gradle wrapper, Kotlin/JVM core module, Android application build shell, and VS Code workspace/tasks. Pinned Kotlin plugin 2.3.21 and Android Gradle plugin 9.1.1; shared plugin declarations at the root avoid the Kotlin/Android classloader conflict observed during initial setup.
- Installed official VS Code Kotlin extension `JetBrains.kotlin-server` 0.0.13. The extension install used a PEM bundle of roots already validated/trusted by Windows after the default Node trust failed. No TLS verification was disabled. Language-server import/first-launch terms are not represented as verified; Gradle tasks work independently.
- `tools/prepare-java-trust.ps1` now prepares a project-local Java trust store and a public PEM bundle from Windows-validated chains. The dedicated Gradle cache is `%USERPROFILE%\.gradle-simwarning`; generated outputs are under its `outputs\SimWarning` directory. This avoids OneDrive/long-path cache and result-directory failures observed during initial runs. `.tooling` trust files and obsolete generated workspace caches are ignored by Git.
- The build runner supplies short external project/Kotlin-state cache locations. Source/specifications remain in OneDrive. Gradle uses one worker, a 768 MB heap and 512 MB Metaspace cap; JVM tests use at most 256 MB. Do not run Studio, an emulator, a language server, and full builds concurrently on the constrained host without checking free memory.
- Verified the full red/green cycle, 51 passing unit tests, core build, debug APK assembly, Android lint (zero issues), and APK minimum/target SDK metadata. See TDD_EVIDENCE.md and docs/tdd. No emulator/device runtime tests ran; there is no launcher or monitoring service yet.
- A non-fatal SDK XML v4/v3 compatibility warning remains in build logs; package parsing, APK assembly, and lint succeeded. The earlier Gradle Metaspace warning and lint backup-rule warning were addressed and checked again successfully.

## Native setup and emulator validation — 15 October 2026

- Added Compose setup screens, English/Danish/German JSON catalogs, debug-only instrumentation host, and separate VS Code UI-test build/run tasks. No additional development tools were installed; pinned AndroidX dependencies were downloaded through the configured Gradle trust setup.
- The core suite now has 57 passing tests. The actual UI red baseline had 14 failures, followed by 14 passing unchanged checks. The final extended emulator suite has 19 passing checks. See TDD_EVIDENCE.md for the intermediate test-diagnostic correction and exact scope.
- Successfully booted API 35 and ran instrumentation using 1024 MB guest RAM, host graphics, Vulkan disabled, and two CPU cores. The larger software-rendered attempt consumed too much host memory and was stopped. Build and emulator execution are separate; the emulator was stopped after final screenshots were saved.
- Debug/test APKs and the unsigned release APK build successfully. The release manifest excludes the test activity and requests no runtime permissions; AndroidX supplies only its app-scoped signature receiver permission. Lint has zero errors and five pinned-dependency update suggestions. Monitoring service, real permissions, and physical-device validation remain pending.

## Integrated beta — 15 October 2026

- Implemented the real launcher/UI/settings, phone/notification access flows, foreground service, cellular callback adapters, next-deadline scheduler, boot receiver, and notifications/audio. Runtime grants require no additional development tools.
- 70 core tests and 33 staged API 35 Android checks pass. Real denial/grant dialogs and foreground/airplane/sample/off-boot behavior are exercised with separate clean emulator fixtures. The exact beta is also installed for live radio, unchanged timing, recovery, and actual reboot checks; see current TDD evidence.
- `beta` derives from release, is not debuggable, excludes src/debug, and uses the local development signing identity for an installable side-load build. Signature and optimized WAV bytes are verified before export. Normal release signing remains unset.
- Debug/beta builds and lint succeed. Informational warnings include pinned dependency suggestions, stylistic KTX suggestions, the justified required-telephony feature, SDK XML metadata version mismatch, and a Gradle Metaspace expiry warning during combined multi-variant lint; none are represented as application runtime errors. One build worker and separate emulator/build phases remain in use.
- Emulator host graphics can produce stale/corrupted screenshots after a warm Android reboot. Such a frame was discarded; cold-boot/native test screenshots are used for visual review. The service's read-only state dump independently verifies lifecycle/radio/deadline behavior.

- The final system-bar theme uses a values-v27 override, retaining API 26 compatibility. Final debug/beta lint reports 0 errors and 10 advisory warnings. The signed beta was exported with original-audio byte verification and exact installed/APK hash matching; the raw final reboot timeout and successful delayed follow-up are retained in TDD_EVIDENCE.md rather than counted as an uninterrupted command pass.
