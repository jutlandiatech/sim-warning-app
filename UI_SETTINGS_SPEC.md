# Approved Settings design and automation contract

Approved on 15 October 2026. Implements SPECIFICATION.md R36–R40 and R42–R44. Reference: [Settings preview](design/settings-preview.html).

## Layout and navigation

Use the approved white, blue, and black visual style, 18sp body text, approximately 28sp screen heading, and large touch targets. Top bar contains a labeled accessible Back action and localized screen title. Main list has six full-width rows with blue supporting icons, clear labels, optional secondary values, subtle dividers, and trailing chevrons. Icons supplement text, never replace it.

Order: Language, Warning interval, Preview sound, Test warning, Permissions, Help. Show selected language beneath Language and selected repeat interval beneath Warning interval. Sound preview and test support both warning types. Permissions links to review of actual access; never imply access is granted merely from opening the page.

Each item opens its own detail screen. In-app Back and Android Back return from a detail to Settings, and from Settings to the actual main screen. Preserve monitoring state, outage timing, and changes already made. Selection changes apply immediately, without an extra Save action. This is a design choice embodied in the approved preview. Do not reset selections when leaving a detail screen.

### Detail screens

- **Language:** native names and approved flags in three single-selection rows. Immediately localize current screen, Settings, main screen, and subsequent app-owned messages. Persist the choice. Android launcher-label behavior needs platform validation.
- **Warning interval:** labeled dropdown, exactly 2/4/6/8/10 minutes, with explanation of repeating sound/vibration. Apply changes under R15, not by resetting a live episode.
- **Preview sound:** select either warning type, then explicitly tap the preview action. Play only the selected approved sound, subject to current system settings. Never auto-play on navigation or selection.
- **Test warning:** select either type, then explicitly tap Test warning. In production this posts a clearly labeled sample Android notification and permits sound/vibration under system rules. It must not overwrite a live notification or change monitoring/timers. The preview's in-page sample is a simulation, not the production delivery method.
- **Permissions:** explain essential access and provide review/grant/settings navigation. Actual permission list, per-permission states, and any Android settings screens await platform investigation. The mock does not request or verify grants.
- **Help:** readable short sections in the selected language, using the agreed respectful tone. The preview's three short sections establish layout, not exhaustive final help copy; final help must cover all R38 topics, including both warning types and their grace periods.

The preview's generic icon, partial main-screen destination, chat-specific audio message, and simulation disclosures are not final product UI. Production uses the approved logo where appropriate, actual main screen, real audio/system notifications, and actual permission state. Do not copy preview implementation shortcuts into production.

## Stable automation IDs

Use the same Compose testTag/resource-ID approach as UI_SETUP_SPEC.md. Never select app controls by localized label or screen coordinates. Reuse existing Settings root/menu IDs from that registry. New IDs below are independent of language and selected value.

| ID | Element |
|---|---|
| `settings_title` | Current localized Settings/detail title |
| `settings_back_button` | Accessible Back action |
| `settings_language_value` | Selected language on menu |
| `settings_warning_interval_value` | Selected interval on menu |
| `settings_language_screen` | Language detail root |
| `settings_language_da` / `settings_language_en` / `settings_language_de` | Selectable language rows |
| `settings_interval_screen` | Interval detail root |
| `settings_interval_selector` | Interval dropdown |
| `settings_interval_option_2` / `_4` / `_6` / `_8` / `_10` | Full tags use the shared prefix `settings_interval_option_` |
| `settings_preview_sound_screen` | Sound detail root |
| `settings_test_warning_screen` | Test detail root |
| `settings_warning_type_selector` | Warning type selector in active preview/test screen |
| `settings_warning_type_loss` | Connection-loss option |
| `settings_warning_type_unavailable` | Unavailable-status option |
| `settings_play_preview_button` | Explicit sound preview action |
| `settings_send_test_button` | Explicit test-warning action |
| `settings_preview_feedback` | Playback/result feedback if needed |
| `settings_test_feedback` | Test-result feedback if needed |
| `settings_permissions_screen` | Permission detail root |
| `settings_permissions_explanation` | Essential-access explanation |
| `settings_permission_status` | Actual prerequisite/grant status |
| `settings_review_permissions_button` | Review/request/settings action |
| `settings_help_screen` | Help root |
| `settings_help_content` | Scrollable help content |

Add per-permission and per-help-section IDs when final content is defined. Every displayed ID must be unique in its active semantic root. Labels, selected state, enabled state, and feedback remain accessible and localized.

## Acceptance scenarios

| ID | Scenario and expected result |
|---|---|
| S01 | Open Settings: six rows in agreed order, current language/interval shown; all menu IDs discoverable in Compose and UI Automator. |
| S02 | Open each detail; app Back and Android Back return correctly; preserve monitoring and current selections. |
| S03 | Select each language: localized content immediately updates; labels use native language names and approved flags; selection persists across navigation and relaunch. |
| S04 | Select each allowed interval: value updates and persists; apply R15 using controlled-time tests during ordinary and initial outages. |
| S05 | Enter sound/test screens and change warning type: no output before explicit action. Only selected type is previewed/tested. |
| S06 | Explicit preview: selected approved audio requested once, subject to suppression. No test notification or change to monitoring. |
| S07 | Explicit test: distinct sample notification and permitted sound/vibration; live episode/notification/timers unchanged. Denied notification permission must not report successful posting. |
| S08 | DND, silent, and vibrate settings: preview/test respect each, independently of visual test posting. Verify actual device integration separately from deterministic UI tests. |
| S09 | Permission review: actual state shown; return from Android dialog/settings refreshes grant state. Revocation/restoration follows R10. |
| S10 | Help: all agreed topics covered in each catalog, readable headings and scrollable content; no prototype-only copy. |
| S11 | All three languages, enlarged fonts, compact screens: no clipping, reachable controls, meaningful screen-reader semantics; IDs unchanged. |
| S12 | Settings entered during an active warning: merely browsing or going Back never restarts or silences monitoring. Test/preview remain isolated; interval edits change timing only under R15. |

The native menu/details are now implemented. API 35 tests cover all six routes/back navigation, persisted language/interval, main recreation/off preference, sample isolation, actual permission grants/denial, and foreground notification channels. This is partial coverage of the scenarios above, not full acceptance; see TESTING.md and TDD_EVIDENCE.md. Retain device validation for acoustic/haptic output, DND variants, deep sleep, accessibility/layout, phone permissions, and physical SIM behavior.
