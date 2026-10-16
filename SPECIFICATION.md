# Sim Warning — requirements and design specification

Version: 0.5, updated on 15 October 2026 with the approved manual-SIM fallback and first integrated test APK implementation.

This document records agreed requirements. Explicitly marked open questions are not approved behavior. The native setup, main/Settings/Help UI and Android monitoring integration now exist. This does not establish full device compliance: see TESTING.md and TDD_EVIDENCE.md for executed scope, sleep-delivery limitations, and remaining physical-device gates.

## 1. Purpose and scope

| ID | Requirement |
|---|---|
| R01 | Warn about unexpected loss of normal mobile network service, regardless of cause. Do not diagnose a loose SIM or imply a known cause. |
| R02 | Support Android 8.0/API 26 and newer, using native Kotlin and Jetpack Compose as specified in the supplied blueprint. |
| R03 | Keep this version minimal and entirely local: no account, advertising, analytics, or server connection. |
| R04 | Monitor in the background without requiring an open screen. After initial setup, automatically resume on phone startup if monitoring was enabled. Preserve an explicit off setting across restarts. |
| R05 | Minimize battery, memory, and CPU use. Prefer event-driven monitoring; avoid continuous polling, busy loops, and keeping the CPU awake throughout normal monitoring. |

Normal service means mobile network service, not internet reachability. Emergency-calls-only service does not qualify. Wi-Fi does not qualify. Physical SIMs and eSIMs count equally.

## 2. Connectivity states and suppression

| ID | Requirement |
|---|---|
| R06 | Evaluate all SIMs. Any SIM with normal mobile service prevents a confirmed total outage; a loss on one SIM alone must not warn if another has service. |
| R07 | Turning mobile data off alone must not warn while normal mobile service remains available. Wi-Fi must not conceal a mobile-service outage. |
| R08 | Airplane mode and reliably identified intentional disabling of all SIMs suppress both warning types: clear their visual warnings and cancel pending sound/vibration. Do not infer intention merely from missing service or a disappearing SIM. |
| R09 | Distinguish reliably disconnected service from unreadable connection status. Never report a confirmed outage solely because status cannot be read. |
| R10 | Missing required permissions are a separate prerequisite state. Show “Monitoring needs permission” and “Review permissions”; do not claim active monitoring or issue repeated status-unavailable warnings. If a required permission is revoked, clear existing warnings and cancel all pending sound/vibration, including held alerts. Once permissions are restored, resume only if monitoring is enabled, starting fresh with the 5-minute initial grace period rather than resuming previous timers. |

Approved first-test-APK fallback, 15 October 2026: public Android APIs cannot reliably identify manual all-SIM disabling on every phone. The user approved using the Monitoring switch before manually disabling all SIMs, with this explained in setup and Help. Airplane-mode suppression remains automatic. Never interpret an empty subscription list as proof of intention. This resolves the proposed fallback in ANDROID_FEASIBILITY.md for this test APK; automatic manual-disable recognition is not claimed.

## 3. Confirmed connection-loss timing

| ID | Requirement |
|---|---|
| R11 | During established monitoring, require 30 seconds of sustained loss before posting the visual warning. Shorter losses do not warn. |
| R12 | Audible/vibration interval choices are 2, 4, 6, 8, or 10 minutes; default 2 minutes. For ordinary outages, measure deadlines from loss onset: I, 2I, 3I, etc. |
| R13 | Use a fresh 5-minute initial grace period after phone restart, initial monitoring start, manual re-enabling, airplane-mode exit, or re-enabling SIM service after intentionally disabling all SIMs, if service is unavailable. Do not resume old outage timers. |
| R14 | For these initial episodes, post the visual warning and issue the first sound/vibration at 5 minutes if service is still unavailable. Repeat at the chosen interval from that deadline: 5 minutes, 5+I, 5+2I, etc. No outage warnings occur during the initial grace period. |
| R15 | Interval changes apply immediately without resetting the episode's timing anchor. For ordinary outages, retain loss onset. For initial episodes, preserve the first-warning deadline at 5 minutes and measure subsequent repeats from that deadline. Use the next future deadline under the new interval, with no catch-up sounds. |
| R16 | Confirm recovery only after 30 seconds of continuous normal mobile connectivity. Then clear warnings and cancel pending alerts quietly, without a recovery sound or separate recovery notification. |
| R17 | A shorter reconnection does not reset the outage timer or clear the active warning. Hold any sound/vibration due during recovery confirmation. On confirmed recovery cancel it; if recovery fails, issue the held warning once, subject to current system settings, then continue the original schedule. |
| R18 | Switching monitoring off clears both warning types and cancels all pending sounds/vibration, including held warnings. Re-enabling starts fresh under R13. |

During any initial episode under R13, 30 continuous seconds of normal mobile connectivity completes startup/reconnection confirmation and ends the initial grace period early. Cancel initial-episode warning timers. Any subsequent loss is an ordinary outage under R11–12.

For an ordinary loss still in its initial 30-second grace period, a brief reconnection preserves the original loss onset. Hold new warning presentation while the 30-second recovery check is in progress. If recovery succeeds, cancel the episode without warning. If recovery fails after the original loss grace deadline, post the visual warning immediately; preserve the original sound/vibration schedule and apply R17 to any held alert. Existing visual warnings remain posted during recovery confirmation.

Timing examples, assuming uninterrupted loss and sound/vibration permitted:

| Episode | Selected interval | Visual warning | Sound/vibration deadlines |
|---|---|---|---|
| Ordinary loss | 2 minutes | 30 seconds | 2, 4, 6, 8 minutes… |
| Ordinary loss | 6 minutes | 30 seconds | 6, 12, 18 minutes… |
| Startup or exit from intentional disconnection | 2 minutes | 5 minutes | 5, 7, 9, 11 minutes… |
| Startup or exit from intentional disconnection | 6 minutes | 5 minutes | 5, 11, 17 minutes… |

Exact boundary ordering and delivery tolerance require O08 and technical validation; these times are intended deadlines, not verified delivery guarantees.

## 4. Connection status unavailable

| ID | Requirement |
|---|---|
| R19 | During established monitoring, after 30 seconds of continuously unreadable status, post a separate status-unavailable notification without requiring the app to be open. For initial episodes covered by R13, use a 5-minute visual grace period instead. |
| R20 | First issue its distinct sound/vibration after 10 continuous minutes, then every 10 minutes: 10, 20, 30 minutes, etc. This interval is independent of the user's outage interval. |
| R21 | If a confirmed outage becomes unreadable, replace the outage state with status unavailable, cancel its sound/vibration schedule and held warnings, and start the unreadable timer. Never run both schedules concurrently. |
| R22 | Once status is reliably readable, cancel the unreadable-state alert schedule. If connected, quietly confirm recovery using the 30-second rule. If disconnected, start the ordinary loss rules, subject to intentional suppression and any applicable initial grace period. |
| R23 | Intentional-disconnection suppression, monitoring off, and system sound/vibration rules also apply to status-unavailable warnings. |

## 5. Notifications, sounds, and vibration

| ID | Requirement |
|---|---|
| R24 | Post visual warnings independently of sound suppression. Android permissions and user-controlled visual notification settings still govern actual display. Do not bypass those settings. |
| R25 | Respect Do Not Disturb rules. Respect silent mode for sound and vibration; in vibrate mode allow vibration subject to system settings, but no sound. Never alter these settings to warn. |
| R26 | When suppression ends, wait for the next scheduled warning; do not replay missed warnings. Monitoring and visual posting continue while sound is suppressed. |
| R27 | Dismissing a confirmed-outage notification does not stop monitoring or repeating alerts. Repost the visual notification at the next scheduled warning. The repost itself produces no extra sound or vibration; the independent scheduled alert still follows R25. |
| R28 | Tapping the warning opens the main screen with connection status and access to Settings. Show localized elapsed outage duration below the connection-loss title, using a battery-efficient mechanism. |
| R29 | Use the two approved original sounds: connection loss uses the 2.1-second rounded three-note motif in design/connection-loss-candidate.wav (523.25, 440.00, 587.33 Hz); unavailable status uses the 1.8-second lower descending two-note motif in design/status-unavailable-candidate.wav (392.00, 329.63 Hz). Preserve their reviewed audio, timing, and distinct timbres. Their candidate filenames do not indicate pending approval. |
| R30 | Vibrate at scheduled warning deadlines and apply the same hold/cancel timing rules as sound. Visual-only posting or reposting must not itself vibrate. |

Approved vibration design: three short pulses for connection loss and two short pulses for unavailable status, matching each sound's note count. Exact pulse lengths, gaps, and supported-device feel remain to tune and review; system suppression rules still apply.

Status-unavailable notifications use the same tap behavior as connection-loss notifications. Dismissing one does not stop monitoring or its 10-minute warning schedule. Repost it silently at the next status-unavailable warning deadline, with no extra sound/vibration caused by reposting; separately scheduled sound/vibration still follows system settings.

Approved localized text:

| Text | English | Danish | German |
|---|---|---|---|
| App name | Sim Warning | SIM-advarsel | SIM-Warnung |
| Loss warning | No mobile network connection | Ingen forbindelse til mobilnettet | Keine Verbindung zum Mobilfunknetz |
| Unreadable status | Connection status unavailable | Forbindelsesstatus er ikke tilgængelig | Verbindungsstatus nicht verfügbar |

## 6. Setup, settings, localization, and accessibility

| ID | Requirement |
|---|---|
| R31 | First-time setup has four short steps: language, brief explanation, warning interval, required permissions. Enable monitoring on completion when prerequisites are granted. |
| R32 | Offer Danish, English, and German during setup and in Settings. Show native language names with flags: Denmark/Dansk, UK/English, Germany/Deutsch. Preselect the supported phone language, otherwise English; allow a change before continuing. |
| R33 | Use stable text IDs and per-language JSON catalogs such as TextCatalog_DEN.json, TextCatalog_ENG.json, and TextCatalog_GER.json for all app-owned text. Include setup, help, settings, statuses, permission explanations, notifications, and duration formatting. Android-owned dialogs retain system translations. |
| R34 | Require only essential permissions, justified individually against functionality. Provide simple initial granting and later review/grant access through the app, invoking Android dialogs/settings as appropriate. |
| R35 | Main screen: connection status, monitoring switch, Settings button. Show the contextual permission-review action when required. |
| R36 | Settings has six items: Language, Warning interval, Preview sound, Test warning, Permissions, Help. Use familiar Android navigation and normal back behavior. |
| R37 | Preview sound and Test warning each offer both warning types and respect current system settings. Test warning uses a clearly marked sample notification and allowed sound/vibration; it must not alter live state/timers or replace a live warning. |
| R38 | Help uses friendly, clear, respectful language without being patronizing. Explain usage, grace periods, warning intervals, sound modes, intentional-disconnection suppression, and permissions. Apply that tone throughout all languages. |
| R39 | Approved sizing direction: 18sp body text, approximately 28sp main headings, controls at least 48dp high, and Android text scaling. Validate all three languages at enlarged sizes without clipping. |
| R40 | Modern Nordic style: white dominant, medium blue and black secondary, generous spacing and clear readability. Reviewed main-screen direction approved. Approved logo: “Nordic Signal”, a medium-blue (#2769AD) SIM-card outline with rounded rising signal bars on white. Preserve its reviewed proportions and shapes; launcher/adaptive and monochrome notification variants require technical preparation and small-size verification. |

Approved logo assets: [editable SVG](design/logo-nordic-signal.svg) and [PNG preview](design/logo-nordic-signal.png). The same symbol is used across all languages; localized app names remain unchanged.

| ID | Requirement |
|---|---|
| R41 | Implement the approved four-step setup design described in [UI_SETUP_SPEC.md](UI_SETUP_SPEC.md), including its layout, localized content, navigation, interval selection, and permission-gated completion. The preview's simulated permission handling is not production behavior. |
| R42 | Give app-owned screens and interactive/status elements stable, language-independent automation IDs. Use Compose test tags, expose them to UI Automator through resource-ID semantics, and preserve accessible human-readable labels separately. Follow the element registry and test contract in UI_SETUP_SPEC.md. |
| R43 | Make setup UI tests runnable on an Android emulator and on a physical device. Use controlled connectivity/permission inputs for deterministic app UI tests and real Android dialogs in separate integration tests; never ship test-only permission or connectivity overrides in release builds. |
| R44 | Implement the approved Settings menu and detail-screen design in [UI_SETTINGS_SPEC.md](UI_SETTINGS_SPEC.md), including stable element IDs and emulator/device acceptance scenarios. Keep preview simulations separate from actual audio, notifications, permission state, and live monitoring. |
| R45 | While the monitoring service is running, provide a quiet “Monitoring is on” service notification, localized through the text catalogs and separate from both warning types. It must not itself play sound or vibrate. Tie its presence and wording to actual service/readiness state rather than the saved enabled preference alone; system notification settings still govern presentation. Remove it when monitoring stops. |

## 7. Acceptance scenarios

These scenarios define intended behavior, not completed tests. Each must be translated into tests before its production implementation, per the supplied TDD directive.

| ID | Given / when | Expected outcome | Requirements |
|---|---|---|---|
| A01 | Ordinary loss lasts 29 seconds | No warning | R11 |
| A02 | Ordinary loss persists beyond 30 seconds; interval 2 minutes | Visual at grace expiry; sound/vibration at 2, 4, 6 minutes subject to settings | R11–12 |
| A03 | Startup has no service; interval 2 minutes | No outage warning before 5 minutes; visual and first alert at 5, repeats at 7, 9 minutes | R13–14 |
| A04 | Leave airplane mode or re-enable SIM service with no service | Fresh 5-minute initial timing, no old timers resumed | R08, R13–14 |
| A05 | One SIM loses service while another remains connected | No total-outage warning | R06 |
| A06 | All SIMs have only emergency service | Treat as no normal service | R01, R06 |
| A07 | Mobile data off but normal service remains | No outage | R07 |
| A08 | Wi-Fi connected but all mobile service lost | Apply normal outage warning rules | R07 |
| A09 | Airplane mode or confirmed intentional all-SIM disabling begins | Clear either warning type; cancel all associated pending alerts | R08, R23 |
| A10 | Recovery lasts 29 seconds then fails | Preserve outage timer and warning; release held alert once if one became due | R16–17 |
| A11 | Recovery remains continuously available for 30 seconds | Quietly clear warning and cancel alerts | R16–17 |
| A12 | Sound/vibration deadline falls during recovery confirmation | Hold; cancel if confirmed, otherwise release once under current settings | R17, R30 |
| A13 | DND suppresses warnings | Visual still posted; suppressed outputs do not play | R24–26 |
| A14 | Phone silent / vibrate mode | Silent: no sound/vibration. Vibrate: no sound, permitted vibration only | R25 |
| A15 | DND or silent mode ends between deadlines | No catch-up; use next scheduled deadline | R26 |
| A16 | Ordinary outage interval changes from 4 to 2 minutes at minute 5 | No immediate catch-up; next alert at minute 6 | R15 |
| A17 | User dismisses outage notification | Keep monitoring; silent visual repost at next alert deadline | R27 |
| A18 | Tap warning | Open main screen; duration localized | R28 |
| A19 | Monitoring switched off during either warning state | Clear warning and cancel all sound/vibration; retain off after reboot | R04, R18 |
| A20 | Re-enable monitoring without service | Fresh 5-minute initial grace | R13, R18 |
| A21 | Status continuously unreadable | Visual after 30 seconds; distinct sound/vibration at 10, 20 minutes | R19–20 |
| A22 | Confirmed outage changes to unreadable | Stop outage schedule; start unreadable schedule; no concurrent schedules | R21 |
| A23 | Unreadable status becomes readable | Cancel unreadable schedule and follow detected state | R22 |
| A24 | Required permission missing or denied | Permission-needed status/action; no repeated unreadable-status alerts or false active claim | R10, R34 |
| A25 | First launch in each supported phone language; unsupported language | Appropriate preselection, English fallback; choice remains editable | R31–33 |
| A26 | Review all screens with large text in each language | Readable, usable controls, no clipping or untranslated text | R33, R38–40 |
| A27 | Preview/test either warning type during a real outage | Correct sample output subject to settings; live state and notification unaffected | R29, R37 |
| A28 | Initial episode interval changes from 2 to 4 minutes before minute 5 | First warning remains at minute 5; subsequent alerts at minutes 9, 13, etc. | R14–15 |
| A29 | Initial episode interval changes from 4 to 2 minutes at minute 8 | No immediate catch-up; next alert at minute 9, retaining the minute-5 anchor | R14–15 |
| A30 | Status remains unreadable from startup or another initial episode under R13 | Visual notification after 5 minutes; first sound/vibration at 10 minutes, then 20, 30 minutes, etc. | R13, R19–20 |
| A31 | Normal service becomes available during an initial episode and remains available for 30 seconds | End initial mode and cancel its timers; a later loss uses the ordinary 30-second visual grace and onset-based alert schedule | R11–14, R16 |
| A32 | Ordinary loss at second 0; service returns at second 20 and fails at second 40 | No warning during the recovery check; post visual warning at second 40. Keep loss onset at second 0 for alert timing | R11–12, R16–17 |
| A33 | Ordinary loss at second 0; service returns at second 20 and remains available through second 50 | No warning while recovery is checked; cancel episode at second 50 | R11, R16–17 |
| A34 | Required permission revoked during either warning state | Clear active warnings and cancel pending/held sound and vibration; show permission-needed status | R10 |
| A35 | Required permissions restored while monitoring is enabled and service remains unavailable | Fresh 5-minute initial grace; no old warning timers resumed | R10, R13–14 |
| A36 | Required permissions restored while monitoring is off | Remain off; do not start monitoring or warning timers | R04, R10, R18 |
| A37 | Tap a status-unavailable notification | Open the main screen with current status and access to Settings | R28 |
| A38 | Dismiss a status-unavailable notification during ongoing unreadable status | Keep monitoring and the 10-minute schedule; silently repost at the next deadline with no extra sound/vibration from reposting | R20, R23–27 |
| A39 | Run the setup automation scenarios U01–U14 in UI_SETUP_SPEC.md | Stable selectors, correct navigation/state/localization, real permission gating, and accessible layouts across configured emulator/device cases | R31–43 |
| A40 | Run Settings scenarios S01–S12 in UI_SETTINGS_SPEC.md | Approved navigation, persisted choices, stable IDs, isolated previews/tests, localized accessible layouts, and real prerequisite handling | R36–44 |
| A41 | Monitoring service starts and later stops | Separate localized service notification while running, no sound/vibration from service-status posting; remove on stop without affecting the separate warning contracts | R04, R18, R45 |

## 8. Engineering validation and definition of done

Research and proposed architecture: [ANDROID_FEASIBILITY.md](ANDROID_FEASIBILITY.md), checked against official Android/AOSP documentation on 15 October 2026. Important unresolved constraints include idle-mode timing, foreground-service notification/type, and reliable recognition of intentional SIM disabling. This research does not change agreed behavior or establish device-tested reliability.

The supplied blueprint requires interfaces and tests before functional implementation. Pure Kotlin state/timing tests should exercise agreed scenarios with controlled time. Android component/UI tests must verify permission flows, text/layout, and notifications. Physical-device end-to-end tests must verify startup, screen-off operation, SIM transitions, suppression, and recovery. A feature is not fully verified until the applicable tiers pass; missing device access or unrun checks must be reported explicitly.

Respect the supplied low-memory development constraint: constrain build workers/daemons and verify available memory before choosing build settings. UI automation must support an emulator as requested; use a physical device when host memory cannot support it. Emulator tests do not replace real-device telephony and battery validation. No tools are installed as part of this specification update.

Before reliability claims, validate:

- Minimal permissions, Android-version-compatible telephony APIs, and subscription aggregation.
- Reliable recognition of intentionally disabled SIMs versus disappearance/read failure.
- Boot/first-unlock behavior, force-stop limitations, permission revocation, and device battery restrictions.
- A legitimate background-service design, any ongoing notification, and screen-off alert scheduling.
- DND visual suppression, channel sound/vibration settings, notification dismissal, and locale changes.
- Measurable power/memory/CPU use and acceptable timer delivery tolerance. Targets remain to agree.
- Matching catalog IDs, placeholder formatting, localization fallback, and packaged app-name behavior.

## 9. Remaining review points

| ID | Open point |
|---|---|
| O07 | Remaining palette details, exact vibration pulse/gap timing, and remaining localized copy. Settings, setup, both warning sounds, vibration pulse counts, and logo are approved; Android asset packaging, exact permission list, and system dialogs await engineering preparation/validation. |
| O08 | Exact simultaneous-event ordering, warning delivery tolerance, measurable resource targets, supported test-device matrix, and technical feasibility of Android background behavior. |
| O09 | Persistent monitoring notification, process restart handling, and what users should see if Android prevents monitoring. |

Resolve these interactively, one point at a time. Do not silently turn proposed behavior or unverified platform assumptions into requirements.
