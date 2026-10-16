# Approved setup design and UI automation contract

Status: approved design direction, 15 October 2026. Companion to SPECIFICATION.md R31–R43. A reusable native setup component is now implemented and tested on an API 35 emulator through a debug-only host. Real permission/service integration and full acceptance coverage remain pending; see [implementation and test boundary](docs/SETUP_IMPLEMENTATION.md).

## Design reference

The approved interactive reference is preserved at [design/setup-preview.html](design/setup-preview.html). It is a design reference, not the native implementation or a source of Android permission truth. Its simulated permission review, completion message, and restart-preview button must not become production shortcuts. The last confirmation screen is informational; the application must derive actual readiness from prerequisites, not claim it is running unconditionally.

## Layout and typography

- White background; medium blue accents and primary buttons; near-black text. The reference uses #2769AD blue, #15191E primary text, #454E58 secondary text, and #E0E6ED dividers as reviewable reference values, not a finalized palette.
- App name with provisional signal mark in the top bar. Localize the name immediately when language changes.
- Content padding approximately 24dp. Body 18sp; main heading approximately 28sp; app title approximately 22sp. Secondary progress text may be 16sp if still readable. Use Android-scaled units.
- Show “Step N of 4” and four short progress marks. Meaning must not depend solely on the blue marks.
- One primary full-width button, at least 56dp high in this design. Back action below it on steps 2–4; Android Back provides equivalent backward navigation within setup.
- Rounded choice rows and buttons, approximately 12dp corners. Entire language rows are selectable, with at least 48dp touch targets.
- Respect system insets, keyboard/navigation areas, font scaling, and screen readers. Content may scroll vertically on small screens or with large text; controls must remain reachable without clipping or overlap. Do not shrink text to force a fit.
- Preserve selected language and interval when moving backward/forward, recomposing, or recreating the activity. Process-death/abandoned-setup resumption policy remains a separate design question; do not confuse activity recreation with completion.

## Four steps

### Language

English heading: “Choose your language”. Supporting text: “You can change this later in Settings.”

Show three single-selection rows: Denmark flag + Dansk, UK flag + English, Germany flag + Deutsch. Use native names in every locale. Preselect the supported phone language, otherwise English. Exactly one option is selected. Changing it immediately updates app-owned setup text and app title, preserving the current step and interval. Flags are decorative supplements; the language name supplies the accessible label.

Primary action: Continue. No in-app Back action on the first step. Continue advances to the explanation without committing setup as complete.

### Explanation

English heading: “Stay aware of lost mobile service”. Body: “Sim Warning runs in the background and warns you if your phone loses its mobile network connection.”

Two short sections:

- “Quiet when you choose”: “No warnings in airplane mode. Switch monitoring off before manually disabling all SIMs.” This is the user-approved fallback for the first test APK, agreed after Android feasibility investigation.
- “Your sound settings apply”: “Warnings respect Do Not Disturb, silent mode and vibrate mode.”

Continue advances; Back returns to language. The explanatory copy does not waive the need to validate disabled-SIM recognition on supported devices.

### Warning interval

English heading: “Choose a warning interval”. Body: “If mobile service stays unavailable, sound and vibration repeat at this interval.”

Use an accessible native-style dropdown with only 2, 4, 6, 8, 10 minutes; default 2. Keep a visible field label. Explain the visual warning: “Usually after 30 seconds. After startup or airplane mode, the app allows 5 minutes to reconnect.” Detailed recovery and other initial-episode cases belong in Help.

Continue advances; Back returns to the explanation. Changing this setup value must not accidentally start monitoring before completion.

### Permissions and completion

English heading: “Allow essential access”. Explain that mobile-service observation and warning delivery need the minimum access appropriate to the device's Android version. “Your information stays on your phone. No account or tracking.”

Provide Review permissions, followed by Start monitoring. The final exact permission list and grant order depend on the engineering investigation; request no unrelated permission. Review invokes actual Android requests or relevant settings where applicable. Returning from Android settings must refresh actual grant state. Denial, cancellation, or permanently denied permission must never be treated as a grant.

Start monitoring is enabled only when all required prerequisites are actually satisfied. Re-check them when tapped, persist setup completion and monitoring-enabled preference, start the monitoring lifecycle once, and navigate to the main screen. A rapid double tap must not create duplicate monitors. The main screen reports actual readiness/status. Use the agreed 5-minute initial timing when appropriate. Do not request permissions merely when opening the language, explanation, or interval step.

The design preview's “Review setup again” and “Preview only” text are preview controls/disclosures and must not appear in the released setup flow.

## Text catalog contract

Keep UI IDs separate from text IDs. Use shared stable text keys across ENG/DEN/GER JSON catalogs. The preserved reference contains the reviewed three-language wording; transfer it into catalogs rather than copying visible strings into composables. Duration values and language options must use locale-appropriate formatting.

Suggested key families: `app.name`, `setup.progress` with `{current}`/`{total}`, `setup.language.*`, `setup.explanation.*`, `setup.interval.*`, `setup.permissions.*`, `action.continue`, `action.back`, `action.startMonitoring`, `status.permissionRequired`. Final catalog naming may vary consistently; automation IDs below are the fixed selector contract.

## Element-ID registry

IDs are stable lowercase snake_case, independent of language, layout order, current copy, or selected value. The shared native registry should define constants once for production UI and test references. Preserve IDs when editing translations or styles. Expose only the current screen's active elements; avoid duplicate visible IDs.

| Element ID / Compose test tag | Element and observable state |
|---|---|
| `setup_screen` | Setup root, present throughout the four steps |
| `setup_app_name` | Current localized app name |
| `setup_step_progress` | Localized current step and total; progress semantics |
| `setup_language_screen` | Language step root |
| `setup_language_title` | Language heading |
| `setup_language_hint` | Language-change explanation |
| `setup_language_da` | Entire Danish radio row; selected semantics |
| `setup_language_en` | Entire English radio row; selected semantics |
| `setup_language_de` | Entire German radio row; selected semantics |
| `setup_explanation_screen` | Explanation step root |
| `setup_explanation_title` | Explanation heading |
| `setup_explanation_body` | Purpose explanation |
| `setup_intentional_disconnect_info` | Intentional-disconnection explanation |
| `setup_sound_rules_info` | Sound-mode explanation |
| `setup_interval_screen` | Interval step root |
| `setup_interval_title` | Interval heading |
| `setup_interval_selector` | Dropdown trigger, selected value semantics |
| `setup_interval_option_2` | 2-minute menu option |
| `setup_interval_option_4` | 4-minute menu option |
| `setup_interval_option_6` | 6-minute menu option |
| `setup_interval_option_8` | 8-minute menu option |
| `setup_interval_option_10` | 10-minute menu option |
| `setup_visual_warning_info` | Visual-warning timing explanation |
| `setup_permissions_screen` | Permission step root |
| `setup_permissions_title` | Permission heading |
| `setup_permissions_explanation` | Device-appropriate access explanation |
| `setup_privacy_info` | Local-only explanation |
| `setup_review_permissions_button` | Starts permission review |
| `setup_permission_status` | Actual grant/readiness status; accessible live update |
| `setup_start_monitoring_button` | Permission-gated final action; enabled state |
| `setup_continue_button` | Continue on steps 1–3 only |
| `setup_back_button` | Back on steps 2–4 only |
| `main_screen` | Actual post-setup destination |
| `main_connection_status` | Current connection/readiness status |
| `main_monitoring_switch` | Monitoring control with checked semantics |
| `main_settings_button` | Opens Settings |
| `main_review_permissions_button` | Contextual missing-permission action |
| `settings_screen` | Settings destination |
| `settings_language_item` | Language |
| `settings_warning_interval_item` | Warning interval |
| `settings_preview_sound_item` | Preview sound |
| `settings_test_warning_item` | Test warning |
| `settings_permissions_item` | Permissions |
| `settings_help_item` | Help |

Permission-specific row IDs must be added to this registry once the permission list is justified. System-owned permission dialogs are not app UI and cannot receive app-owned tags.

### Compose and UI Automator integration

Use `Modifier.testTag(ElementIds.SETUP_LANGUAGE_EN)` and equivalent tags for app-owned elements. Enable `testTagsAsResourceId = true` high in each relevant Compose semantic root so UI Automator can locate tags with `By.res("setup_language_en")`. Include independently hosted popup/dialog roots when needed. Compose tests use `onNodeWithTag("setup_language_en")`. The setup registry and Compose/resource-ID bridge are now implemented in `app/src/main/java/app/simwarning/setup`; main and Settings IDs remain future implementation contracts.

Reference: [Android Compose testing interoperability](https://developer.android.com/develop/ui/compose/testing/interoperability).

Keep selection/toggle/disabled/progress semantics accurate and selectors reachable in the semantic tree. Attach row IDs to the selectable node rather than hiding them inside merged decorative content. Accessibility descriptions must remain meaningful localized descriptions; never use machine IDs as spoken labels. Test IDs identify elements; tests should separately assert visible text against catalog expectations to catch translation regressions.

## Emulator and device test plan

Write tests before the implementing UI. Use injected deterministic monitoring, time, and prerequisite-state interfaces for app-owned behavior; separately test real Android permissions and lifecycle. Release builds must not expose test overrides. Avoid arbitrary sleeps: wait for semantic conditions, use Compose synchronization, and controlled time for domain timers. Use clean app data for first-run tests and preserve data intentionally for persistence tests.

| ID | Scenario and expected assertions |
|---|---|
| U01 | First launch in da/en/de and unsupported locale: correct language preselection, one selected row, correct app title, progress 1/4. |
| U02 | Select each language by stable row ID: setup text/title change immediately; IDs and selected semantics remain stable. |
| U03 | Continue through steps 1–4: exactly one step root active; progress increments; Back absent on step 1 and present on steps 2–4. |
| U04 | In-app Back and Android Back within steps 2–4 return one step without losing selections. Do not assume a first-step system-Back policy until specified. |
| U05 | Interval default 2; dropdown contains exactly 2/4/6/8/10; select each option by ID and assert value. |
| U06 | Navigate backward/forward after language and interval changes: retain selections. Recreate activity and assert same step/selections. |
| U07 | On permission step with missing requirements: Start monitoring disabled; status honest; review action available. No earlier step requested permissions. |
| U08 | Real Android request grant/deny/cancel and permanent denial as supported: reflect actual state after return. Do not locate system dialogs using app tags or one assumed English button label. |
| U09 | All required access granted: Start monitoring enabled. Revoke access before tapping: re-check, do not falsely finish. |
| U10 | Successful completion: main screen, enabled monitoring preference, single lifecycle start; no simulated permission grant, preview disclosures, or restart-preview button. Double tap does not duplicate start. |
| U11 | After successful setup, relaunch: main screen rather than setup; retained language and interval; preserve explicit monitoring-off preference. |
| U12 | All three languages at normal and enlarged font scale, compact and standard phone widths: no overlap/clipping, scroll reaches actions, controls meet target sizes. |
| U13 | Screen reader/semantic checks: headings, radio selection, meaningful labels, disabled action, readable progress, correct focus order; decorative flags/logo not redundant speech. |
| U14 | UI Automator can find app-owned controls by resource IDs; Compose tests find matching tags across locales, including dropdown options. Verify Settings items and main controls are addressable. |

Run representative API 26 and current-supported Android emulator cases, including notification-permission behavior on applicable versions. Exact images, font-scale values, and screen dimensions belong in the upcoming test matrix. Do not run several emulators concurrently on the constrained host. Emulator UI success does not establish physical multi-SIM, battery, boot, or screen-off scheduling reliability; retain physical-device validation.
