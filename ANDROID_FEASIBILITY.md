# Android monitoring feasibility and proposed technical design

Researched 15 October 2026 using Android documentation and AOSP sources. Native UI, runtime permission state, foreground lifecycle, airplane suppression, and notification-channel integration now have API 35 emulator validation recorded in TDD_EVIDENCE.md. Physical telephony, sleep/power measurements, and Play review remain unverified. The user approved the explicit Monitoring-switch fallback for manual SIM disabling in the first test APK.

## Findings that affect the requirements

| Area | Finding | Implication |
|---|---|---|
| Continuous observation | Subscription-specific service-state callbacks exist; API 31+ uses TelephonyCallback, older supported versions use PhoneStateListener. | Event-driven observation is the appropriate starting point, without internet polling. [Callback reference](https://developer.android.com/reference/android/telephony/TelephonyCallback.ServiceStateListener), [legacy listener](https://developer.android.com/reference/android/telephony/PhoneStateListener). |
| Long-lived service | Foreground services require a user-visible monitoring notification. | Propose a quiet “Monitoring is on” notification while enabled, distinct from fault warnings. The app screen can remain closed. [Foreground services overview](https://developer.android.com/develop/background-work/services/fgs). |
| Service classification | Android 14+ requires an appropriate type. `specialUse` covers valid cases outside the named types, with a manifest explanation and Play review if published there. | Candidate classification, not guaranteed approval. Do not misuse phoneCall, location, or mediaPlayback merely to keep monitoring alive. [Service types](https://developer.android.com/develop/background-work/services/fgs/service-types). |
| Boot startup | Boot broadcasts are an exception to background-start restrictions, with restrictions for some foreground-service types. | Conditional automatic startup is a plausible design; verify target-version/type rules, first unlock, and manufacturer behavior. [Background-start rules](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start). |
| Sleep and repeat timing | Idle modes defer ordinary timers/alarms; allow-while-idle alarms are rate limited. | Exact 30-second checks and 2/4/6/8-minute repeats cannot be promised on every sleeping device using standard alarm scheduling alone. A foreground service is not a universal timing exemption. [Doze guide](https://developer.android.com/training/monitoring-device-state/doze-standby), [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager). |
| Intentional SIM disabling | Public active-subscription APIs report available subscriptions, not a reliable universal explanation of why a subscription disappeared. Privileged enablement queries exist outside ordinary app access. | Airplane suppression is straightforward; all-SIM-disable suppression remains device/API dependent. Never suppress just because the active list is empty. [SubscriptionManager](https://developer.android.com/reference/android/telephony/SubscriptionManager), [AOSP enablement API example](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/android-30/+/refs/heads/androidx-room-release/android/telephony/SubscriptionManager.java). |
| User force-stop | The package stopped state requires user interaction to exit; Android 15 also cancels pending intents. | Cannot promise autonomous recovery after Force stop. Explain that reopening may be required. This is different from closing the app screen. [Stopped-state rules](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state). |

## Proposed monitoring architecture

1. **UI and preferences:** Compose screens, JSON text catalogs, persisted language/interval/monitoring-enabled/setup-completed settings. No connection polling from a screen.
2. **Lifecycle coordinator:** user enable starts one service while the UI is visible; boot receiver checks saved preference and actual permissions before starting. Keep desired enabled state distinct from actual running/readiness state. Handle lifecycle failure without a false “active” claim.
3. **Telephony adapter:** observe subscription changes; register one service-state listener per relevant subscription and unregister removed listeners. API 31+ uses TelephonyCallback; API 26–30 uses PhoneStateListener. Use location-redacted callbacks and avoid cell identity/operator information. Missing or uncertain information maps to unknown, never fabricated connectivity.
4. **Pure Kotlin reducer:** a serialized state machine receives observations, suppression changes, permission changes, and deadlines. States include Off, NeedsPermission, IntentionalDisconnect, InitialGrace, Connected, LossGrace, Outage, RecoveryPending, and StatusUnavailable. Inject a monotonic clock and scheduler so tests can advance time without real waits. Preserve the agreed distinct initial/ordinary schedule anchors.
5. **Deadline adapter:** schedule only the next necessary deadline and cancel obsolete work. Ordinary in-process delays do not wake a sleeping CPU. Evaluate alarm delivery and any narrowly bounded wake-lock need in a dedicated experiment; no continuous normal-monitoring wake lock. Exact-alarm access is a separate decision, not an assumed solution to idle throttling.
6. **Notification adapter:** separate quiet service status from connection-loss and status-unavailable outputs. Package the two approved sounds and vibration patterns. Keep sample notifications on separate IDs from live warnings. Match visual-only and alerting output to tested notification-channel behavior; do not work around user settings.

This is an engineering recommendation. It requires tests before implementation and a feasibility spike before release reliability claims.

### Connectivity classification caveats

The callback supplies a ServiceState with location-sensitive fields stripped when access is absent. This supports a no-location prototype. However, synchronous `getServiceState()` has documented location permission requirements: do not accidentally add location access just to seed the initial state. Wait for validated callback delivery and represent genuinely unreadable state explicitly. [Service-state callback](https://developer.android.com/reference/android/telephony/TelephonyCallback.ServiceStateListener), [TelephonyManager](https://developer.android.com/reference/android/telephony/TelephonyManager).

`ServiceState.getState()` describes voice service; it is not a complete universal test for every voice/data configuration. Validate voice/data-only subscriptions, LTE/5G, emergency-only registration, and Wi-Fi calling so cellular service is distinguished from WLAN service. A healthy observable SIM proves some service exists even if another SIM is unreadable. If none is healthy and some relevant observations remain unknown, do not report a confirmed total outage without sufficient evidence. Exact cross-version classification needs a tested API adapter. [ServiceState](https://developer.android.com/reference/android/telephony/ServiceState).

Subscription disappearance, physical removal, manual disabling, and temporarily unavailable subscription metadata can overlap. No privileged API, root requirement, accessibility-service inspection, or private reflection is used. For the first test APK, the user approved switching Monitoring off before manually disabling all SIMs. Setup and Help explain this fallback.

## Minimal permission proposal

Not a final manifest: verify the compiled dependency manifest and actual APIs in the prototype.

| Permission | User grant dialog? | Justification / condition |
|---|---|---|
| READ_PHONE_STATE | Yes | Subscription enumeration for all-SIM aggregation; request only for status, not call content or phone numbers. |
| POST_NOTIFICATIONS | Yes, Android 13+ | Visible fault/test notifications. Older versions still need notification/channel-enabled checks. |
| RECEIVE_BOOT_COMPLETED | No; normal permission | Resume enabled monitoring on startup. |
| FOREGROUND_SERVICE | No; normal permission, relevant from API 28 | Long-lived monitoring service. |
| FOREGROUND_SERVICE_SPECIAL_USE | No; normal permission, API 34+ | Candidate service-type declaration, subject to valid use-case justification. |
| VIBRATE | No; normal permission | Agreed vibration warnings. |

Normal permissions are manifest declarations, not extra switches the user must grant. [Permission reference](https://developer.android.com/reference/android/Manifest.permission). Subscription enumeration requires READ_PHONE_STATE for an ordinary app. [Subscription reference](https://developer.android.com/reference/android/telephony/SubscriptionManager). POST_NOTIFICATIONS is required for the app's visible warnings on Android 13+, although Android can technically launch an FGS without that grant; our product should still report missing readiness. [Notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission).

Do not request INTERNET, contacts, SMS, call logs, phone numbers, storage, camera, microphone, or location for the proposed baseline. ACCESS_NETWORK_STATE is not justified merely to observe mobile service; add it only if a chosen, necessary API proves to need it. WAKE_LOCK, exact-alarm special access, and battery-optimization exemption remain conditional and unapproved. Reading airplane mode does not require permission to modify system settings. Do not request permission to change DND.

Proposed setup explanation for phone access: “Allow access to phone status so Sim Warning can check whether a mobile network is available.” Android's own dialog may describe broader capability; explain our narrow use honestly and validate exact wording on supported versions.

## Warning timing and battery tradeoff

The difficult requirement is not keeping a screen closed; it is issuing short-interval warnings while the CPU sleeps. WorkManager periodic work has a 15-minute minimum and is not a continuous telephony listener. It does not meet the agreed short grace periods. [PeriodicWorkRequest](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest).

Exact alarms require justification and, on relevant versions, special access. Allow-while-idle delivery is still limited; the Doze guide documents a nine-minute minimum between such alarms per app, while AlarmManager warns that idle dispatch can be considerably longer. They cannot establish a universal two-minute cadence. `setAlarmClock()` has visible alarm-clock semantics and resource cost; do not disguise connectivity reminders as clock alarms to evade restrictions. [Alarm scheduling](https://developer.android.com/develop/background-work/services/alarms).

Battery exemptions are partial, not a guarantee of unlimited execution. They may permit partial wake locks during Doze, but adding them complicates setup and changes power behavior. Do not request them automatically or promise they alone solve timing. Holding a CPU wake lock through an outage is a possible investigation topic, not an approved baseline; it would explicitly trade outage battery cost for stronger timing. We should first measure default behavior, then review evidence and any necessary product compromise.

Proposed decision sequence:

- Keep current durations as desired deadlines until measured.
- Measure delay distributions during awake, screen-off, forced Doze, battery saver, and manufacturer restrictions.
- Present observed timing and power costs before choosing best-effort delivery or a justified reliability option.
- Never replay a backlog of missed alerts on wake; preserve no-catch-up intent. Exact rescheduling behavior after sleep needs a documented state-machine rule.

## Notification and audio design constraints

Android 8+ channels determine sound, vibration, and importance; users control these after creation. Plan channel defaults before first release and do not recreate channels to override choices. A visual-only notification must remain silent, and a scheduled alert must not accidentally double-play through both channel and manual playback. Test the exact channel arrangement, including DND and vibrate mode, before finalizing it. [Notification channels](https://developer.android.com/develop/ui/views/notifications/channels).

Prefer system-managed notification sounds for background alerts. Raw player playback is not a shortcut: Android 17 documents further background-audio restrictions, including while-in-use capability considerations for services started from the background. The boot-started case particularly needs validation; notification-driven playback and foreground preview playback should be tested separately. [Android 17 audio changes](https://developer.android.com/about/versions/17/changes/bg-audio).

DND can suppress notification visuals as well as sound. The agreed contract remains posting when allowed, not overriding visual settings. Likewise, a killed or force-stopped process cannot reliably notify about its own inactivity; show truthful readiness on next launch rather than claiming continuous coverage.

## Feasibility validation gates

| Gate | Evidence needed before accepting the implementation |
|---|---|
| F01 | Callback access on API 26–30 and 31+, without location permission; useful initial delivery and correct listener cleanup. |
| F02 | Physical/eSIM aggregation, empty/unknown lists, emergency-only, data-only, and Wi-Fi-calling classification. |
| F03 | Manual disable/enable versus unexpected SIM removal on actual target devices; record unsupported distinctions. |
| F04 | Quiet monitoring notification and valid service type; successful boot/first-unlock startup where supported. |
| F05 | Actual timestamped delivery of 30-second checks, 5-minute initial warning, 2–10-minute repeats, and 10-minute unknown alerts under sleep modes. |
| F06 | Original audio/vibration output, no duplicates, silent reposts, DND/channel settings, previews/tests, and boot-started background audio. |
| F07 | Permission denial/revocation, force-stop, system Task Manager stop, process death, and service-start rejection do not produce a false ready state. |
| F08 | Compare baseline and monitoring-enabled runs on the same device under matched conditions; separate healthy idle monitoring from an active outage with sounds. Record CPU, memory, wake locks, wakeups, and battery/power rather than inventing a percentage target. |

Use deterministic reducer/UI tests first, emulator platform integration next, and physical-device telephony/power tests. Test matrices should include API 26, 31, 33, 34/35, and newer platform changes relevant to the chosen target SDK, with at least one non-stock manufacturer device. Exact images/devices and measured acceptance tolerances remain to agree.

## Product decisions to review one at a time

1. Resolved: the quiet monitoring notification is approved; see SPECIFICATION.md R45.
2. Choose delivery expectations after the sleep-mode experiment; the current strict short cadence is not yet technically proven.
3. Resolved for the first test APK: the user approved the explicit Monitoring-switch fallback for manual SIM disabling; airplane mode remains automatic.
4. Define the boundary for boot-before-unlock, force-stop, process restart, and system restrictions in Help/readiness states.

No product requirement has been weakened automatically by these findings. The findings identify feasibility gates and proposals to bring back for review.
