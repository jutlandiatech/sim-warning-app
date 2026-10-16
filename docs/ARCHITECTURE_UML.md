# Application Architecture UML

These diagrams describe the current Android implementation, including the boundary between app-owned logic and Android system services. The diagrams are intentionally split by concern: component boundary, class structure, user setup, live monitoring, boot recovery, and monitor states.

## Component And System Boundary

The app has no backend or internet endpoint. Android provides telephony observations, lifecycle/broadcast delivery, permissions, local persistence, alarms, and notification/audio facilities. Cellular registration is observed through Android's telephony API; the app does not open a network connection.

```mermaid
flowchart LR
    User([User])

    subgraph Android[Android operating system]
        Launcher[Activity and lifecycle manager]
        Perms[Runtime permission and package manager]
        Telephony[SubscriptionManager and TelephonyManager]
        Broadcasts[System broadcasts]
        Scheduling[Handler and AlarmManager]
        Notifications[NotificationManager]
        Audio[AudioManager and RingtoneManager]
        Storage[SharedPreferences and app assets]
    end

    subgraph App[Sim Warning app process]
        subgraph UI[Presentation]
            Activity[MainActivity]
            Root[AppRoot]
            Setup[SetupFlow]
        end
        subgraph Core[Platform-independent core]
            Controller[AppController]
            Contracts[SettingsStore and MonitoringPlatform]
            Engine[MonitorEngine]
        end
        subgraph Adapters[Android adapters]
            Access[AndroidAccess]
            Prefs[AppPreferences]
            Platform[AndroidMonitorPlatform]
            Service[MonitorService]
            Observer[CellularObserver]
            Center[NotificationCenter]
            Receivers[BootReceiver, DeadlineReceiver, WarningDismissReceiver]
        end
    end

    User -->|launch, configure, enable| Activity
    Launcher -->|create, resume, permission result| Activity
    Activity --> Root
    Root --> Setup
    Root --> Controller
    Root --> Access
    Root --> Center
    Controller --> Contracts
    Prefs -.->|implements| Contracts
    Platform -.->|implements| Contracts
    Controller --> Prefs
    Controller --> Platform
    Platform -->|start or stop foreground service| Service
    Service --> Engine
    Service --> Observer
    Service --> Center
    Service --> Access
    Service --> Prefs
    Observer -->|register callback / read SIM service state| Telephony
    Telephony -->|service-state callback| Observer
    Access -->|check permissions and feature| Perms
    Activity -->|request runtime permissions / app details| Perms
    Prefs -->|read and write local settings| Storage
    Center -->|post and clear local notifications| Notifications
    Center -->|play local resource / inspect ringer policy| Audio
    Service -->|schedule next elapsed-time deadline| Scheduling
    Scheduling -->|deadline broadcast| Receivers
    Broadcasts -->|boot or package replaced| Receivers
    Broadcasts -->|airplane, ringer, interruption-filter changes| Service
    Receivers --> Controller
    Receivers --> Service
    Notifications -->|tap notification| Activity
    Notifications -->|dismiss warning| Receivers
```

`MainActivity` is the launcher entry point. `MonitorService` and the receivers are app-private Android components; only the launcher activity is exported. System callback directions above represent Android API calls and callbacks, not app-to-app or internet communication.

## Class Structure

The core owns settings coordination and outage decisions. Android implementations satisfy the core interfaces and translate between Android APIs and core data types.

```mermaid
classDiagram
    direction LR

    class MainActivity <<Android_activity>>
    class AppRoot <<Compose_UI>>
    class SetupFlow <<Compose_UI>>
    class AppController {
        +finishSetup(state) Boolean
        +setMonitoring(enabled) Boolean
        +setLanguage(language)
        +setInterval(minutes)
        +resumeIfEnabled() Boolean
    }
    class SettingsStore {
        <<interface>>
        +read() AppSettings
        +write(settings)
    }
    class MonitoringPlatform {
        <<interface>>
        +ready() Boolean
        +running() Boolean
        +start() Boolean
        +stop()
        +preferencesChanged()
    }
    class AppPreferences <<Android_adapter>>
    class AndroidMonitorPlatform <<Android_adapter>>
    class AndroidAccess <<Android_adapter>>
    class MonitorService <<Android_service>>
    class CellularObserver <<Android_adapter>>
    class NotificationCenter <<Android_adapter>>
    class MonitorEngine {
        +update(nowMs, input) MonitorResult
        +dismissWarning()
    }
    class AppSettings
    class SetupState
    class MonitorInput
    class MonitorResult
    class MonitorEffect
    class Connectivity
    class SimService
    class WarningKind
    class SoundPolicy
    class BootReceiver <<Android_receiver>>
    class DeadlineReceiver <<Android_receiver>>
    class WarningDismissReceiver <<Android_receiver>>

    SettingsStore <|.. AppPreferences : implements
    MonitoringPlatform <|.. AndroidMonitorPlatform : implements
    MainActivity --> AppRoot : hosts
    AppRoot --> SetupFlow : shows until setup complete
    AppRoot --> AppController : user actions
    AppRoot --> AndroidAccess : readiness
    AppRoot --> NotificationCenter : preview and sample
    AppController --> SettingsStore : reads and writes
    AppController --> MonitoringPlatform : controls lifecycle
    AppPreferences --> AppSettings : persists
    AndroidMonitorPlatform --> AndroidAccess : checks readiness
    AndroidMonitorPlatform --> MonitorService : starts and stops
    MonitorService *-- MonitorEngine : owns
    MonitorService *-- CellularObserver : observes
    MonitorService --> AppPreferences : reads settings
    MonitorService --> AndroidAccess : checks access
    MonitorService --> NotificationCenter : renders effects
    CellularObserver --> Connectivity : emits aggregate state
    CellularObserver --> SimService : classifies each SIM
    MonitorEngine --> MonitorInput : consumes
    MonitorEngine --> MonitorResult : produces
    MonitorResult --> MonitorEffect : contains
    MonitorInput --> Connectivity
    MonitorInput --> SoundPolicy
    MonitorEffect --> WarningKind
    SetupFlow --> SetupState : edits draft
    AppController ..> SetupState : completes setup
    BootReceiver --> AppController : resumes if enabled
    DeadlineReceiver --> MonitorService : refreshes
    WarningDismissReceiver --> MonitorService : dismisses warning
```

## Setup And Monitoring Start

Permissions are requested by the activity through Android's runtime permission flow. `AndroidAccess.ready()` is rechecked before setup completion and before service start. `AppController` persists settings transactionally around the start request and restores the prior settings if startup fails.

```mermaid
sequenceDiagram
    actor User
    participant Activity as MainActivity
    participant UI as AppRoot / SetupFlow
    participant Access as AndroidAccess
    participant Android as Android permission manager
    participant Controller as AppController
    participant Store as AppPreferences
    participant Platform as AndroidMonitorPlatform
    participant Service as MonitorService

    User->>Activity: Launch app
    Activity->>UI: Compose app
    UI->>Store: read settings
    alt Setup incomplete
        UI->>Access: ready()
        UI->>UI: Show language, explanation, interval, permissions
        User->>UI: Review required access
        UI->>Activity: permission review callback
        Activity->>Access: requiredRuntimePermissions()
        Activity->>Android: Request READ_PHONE_STATE / POST_NOTIFICATIONS as needed
        Android->>Activity: Permission result
        Activity->>UI: Refresh readiness
        User->>UI: Start monitoring
        UI->>Access: ready()
        UI->>Controller: finishSetup(SetupState)
        Controller->>Platform: ready()
        Platform->>Access: ready()
        Access->>Platform: prerequisites satisfied
        Platform->>Controller: ready
        Controller->>Store: write setup complete and monitoring enabled
        Controller->>Platform: start()
        Platform->>Service: startForegroundService()
        Service->>Store: read settings
        Service->>Access: ready()
        Service->>Service: Enter foreground, initialize observer and refresh
        Service->>Platform: Android accepts start request
        Platform->>Controller: accepted
    else Setup already complete
        UI->>Controller: resumeIfEnabled()
    end
```

The start result means Android accepted the start request, not that monitoring has remained active indefinitely. The foreground service independently validates state at creation and on subsequent refreshes.

## Live Monitoring And Warning Delivery

Monitoring is event/deadline-driven. The service submits each observation and current settings to the core engine; the engine returns a status, effects, and at most the next required deadline. The adapter applies effects locally and schedules that deadline without periodic polling.

```mermaid
sequenceDiagram
    participant OS as Android system services
    participant Observer as CellularObserver
    participant Service as MonitorService
    participant Engine as MonitorEngine
    participant Center as NotificationCenter
    participant Alarm as AlarmManager / Handler
    participant Receiver as DeadlineReceiver

    Service->>Observer: start()
    Observer->>OS: Register SIM subscription and service-state listeners
    OS->>Observer: Subscription or service-state callback
    Observer->>Observer: Classify per-SIM state, aggregate connectivity
    Observer->>Service: Changed(Connectivity)
    Service->>Service: refresh(), read preferences, access, airplane mode, sound policy
    Service->>Engine: update(elapsedRealtime, MonitorInput)
    Engine->>Service: MonitorResult(status, effects, nextDeadline)
    opt ShowWarning or Alert effect
        Service->>Center: postWarning(kind, alert, episode start)
        Center->>OS: Post local notification using channel policy
    end
    opt ClearWarning effect
        Service->>Center: clearWarning()
        Center->>OS: Cancel warning notification
    end
    opt A future deadline is required
        Service->>Alarm: Schedule elapsed-time deadline
        Service->>Service: Also post delayed refresh on main Handler
        Alarm->>Receiver: Deadline broadcast (may be delayed by Android)
        Receiver->>Service: refresh()
        Service->>Engine: update(current elapsed time, latest input)
    end
    opt Airplane, ringer, or interruption-filter change
        OS->>Service: System broadcast
        Service->>Service: Re-read affected state and refresh
    end
    opt User dismisses warning
        OS->>Service: WarningDismissReceiver broadcast
        Service->>Engine: dismissWarning()
    end
```

Telephony observations concern cellular registration, not Wi-Fi or general internet connectivity. Audio uses bundled sound resources; the app does not request exact-alarm or DND-policy access.

## Boot And Package-Update Recovery

When Android delivers `BOOT_COMPLETED` or `MY_PACKAGE_REPLACED`, the private boot receiver asks the same controller/platform path to resume only if setup and monitoring are enabled and prerequisites remain available.

```mermaid
sequenceDiagram
    participant Android as Android system
    participant Receiver as BootReceiver
    participant Controller as AppController
    participant Store as AppPreferences
    participant Platform as AndroidMonitorPlatform
    participant Access as AndroidAccess
    participant Service as MonitorService

    Android->>Receiver: BOOT_COMPLETED or MY_PACKAGE_REPLACED
    Receiver->>Controller: resumeIfEnabled()
    Controller->>Store: read settings
    Store->>Controller: setupComplete and monitoringEnabled
    alt Both enabled
        Controller->>Platform: ready()
        Platform->>Access: ready()
        Access->>Platform: feature, phone permission, and notifications status
        Platform->>Controller: ready / not ready
        opt Ready
            Controller->>Platform: start()
            Platform->>Service: startForegroundService()
            Service->>Store: Revalidate persisted settings
            Service->>Access: Revalidate prerequisites
            Service->>Service: Start observer and refresh
        end
    else Not configured or monitoring disabled
        Controller->>Receiver: No service start
    end
```

## Monitor State Machine

This view summarizes externally visible `MonitorStatus` values. It omits internal episode timestamps and sound/vibration policy details; `MonitorEngine` remains the authoritative transition logic.

```mermaid
stateDiagram-v2
    [*] --> OFF
    OFF --> NEEDS_PERMISSION: monitoring enabled, access missing
    OFF --> INTENTIONALLY_DISCONNECTED: airplane mode or all SIMs disabled
    OFF --> INITIAL_GRACE: initial cellular loss
    OFF --> RECOVERING: initial observation is connected

    NEEDS_PERMISSION --> OFF: monitoring disabled
    NEEDS_PERMISSION --> INTENTIONALLY_DISCONNECTED: intentional disconnect detected
    NEEDS_PERMISSION --> INITIAL_GRACE: access restored, cellular loss
    NEEDS_PERMISSION --> RECOVERING: access restored, connected

    INTENTIONALLY_DISCONNECTED --> OFF: monitoring disabled
    INTENTIONALLY_DISCONNECTED --> INITIAL_GRACE: intentional disconnect ends, cellular loss
    INTENTIONALLY_DISCONNECTED --> RECOVERING: intentional disconnect ends, connected

    INITIAL_GRACE --> OUTAGE: initial visual grace expires
    INITIAL_GRACE --> RECOVERING: cellular service returns
    INITIAL_GRACE --> STATUS_UNAVAILABLE: service state becomes unknown
    LOSS_GRACE --> OUTAGE: visual grace expires
    LOSS_GRACE --> RECOVERING: cellular service returns
    LOSS_GRACE --> STATUS_UNAVAILABLE: service state becomes unknown

    OUTAGE --> RECOVERING: cellular service returns
    OUTAGE --> STATUS_UNAVAILABLE: service state becomes unknown
    STATUS_UNAVAILABLE --> LOSS_GRACE: cellular loss becomes known
    STATUS_UNAVAILABLE --> RECOVERING: cellular service becomes known and connected

    RECOVERING --> CONNECTED: connected continuously for recovery grace
    RECOVERING --> LOSS_GRACE: loss resumes before recovery completes
    RECOVERING --> OUTAGE: same outage resumes after visual warning issued
    RECOVERING --> STATUS_UNAVAILABLE: service state becomes unknown

    CONNECTED --> LOSS_GRACE: cellular loss
    CONNECTED --> STATUS_UNAVAILABLE: service state unknown
    CONNECTED --> INTENTIONALLY_DISCONNECTED: airplane mode or all SIMs disabled
    CONNECTED --> OFF: monitoring disabled

    OUTAGE --> OFF: monitoring disabled
    STATUS_UNAVAILABLE --> OFF: monitoring disabled
    LOSS_GRACE --> OFF: monitoring disabled
```

`INITIAL_GRACE` uses the initial five-minute visual grace. Later known connection-loss episodes use the configured warning interval as their grace. Recovery must remain connected for 30 seconds before the episode is cleared. Unknown service state is represented separately and uses its own ten-minute alert cadence.

## Source Map

- Core contracts and effects: [`MonitorContract.kt`](../core/src/main/kotlin/app/simwarning/core/MonitorContract.kt)
- Setup and lifecycle coordination: [`AppController.kt`](../core/src/main/kotlin/app/simwarning/core/AppController.kt)
- Outage state machine: [`MonitorEngine.kt`](../core/src/main/kotlin/app/simwarning/core/MonitorEngine.kt)
- Android component declarations and permissions: [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml)
- UI and permission entry points: [`MainActivity.kt`](../app/src/main/java/app/simwarning/MainActivity.kt), [`AppRoot.kt`](../app/src/main/java/app/simwarning/ui/AppRoot.kt), [`SetupFlow.kt`](../app/src/main/java/app/simwarning/setup/SetupFlow.kt)
- Service lifecycle and receivers: [`MonitorService.kt`](../app/src/main/java/app/simwarning/platform/MonitorService.kt)
- Telephony and Android adapters: [`CellularObserver.kt`](../app/src/main/java/app/simwarning/platform/CellularObserver.kt), [`AndroidAccess.kt`](../app/src/main/java/app/simwarning/platform/AndroidAccess.kt), [`AndroidMonitorPlatform.kt`](../app/src/main/java/app/simwarning/platform/AndroidMonitorPlatform.kt)
- Local persistence and notifications: [`AppPreferences.kt`](../app/src/main/java/app/simwarning/platform/AppPreferences.kt), [`NotificationCenter.kt`](../app/src/main/java/app/simwarning/platform/NotificationCenter.kt)