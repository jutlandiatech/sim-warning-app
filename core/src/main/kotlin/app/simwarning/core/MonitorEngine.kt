package app.simwarning.core

/**
 * Event/deadline-driven domain engine. Call on one serialized owner, using elapsed real time.
 * Android adapters own scheduling and delivering effects; this class performs no I/O.
 */
class MonitorEngine {
    private data class Episode(
        val kind: WarningKind,
        val onset: Long,
        val initial: Boolean,
        val visualAt: Long,
        val alertAnchor: Long,
        var nextAlertAt: Long,
        var intervalMs: Long,
        var visualIssued: Boolean = false,
        var heldAlert: Boolean = false,
    )

    private var lastTime: Long? = null
    private var active = false
    private var initial = true
    private var recoverySince: Long? = null
    private var episode: Episode? = null
    private var visible: WarningKind? = null
    private var lastPolicy: SoundPolicy? = null

    fun update(nowMs: Long, input: MonitorInput): MonitorResult {
        require(nowMs >= 0) { "Monotonic time cannot be negative" }
        require(lastTime == null || nowMs >= lastTime!!) { "Monotonic time cannot move backwards" }
        require(input.intervalMinutes in setOf(2, 4, 6, 8, 10)) { "Interval must be 2, 4, 6, 8, or 10 minutes" }
        lastTime = nowMs
        val effects = mutableListOf<MonitorEffect>()
        val resumedOutput = lastPolicy?.let {
            (!canSound(it) && canSound(input.soundPolicy)) ||
                (!canVibrate(it) && canVibrate(input.soundPolicy))
        } ?: false
        lastPolicy = input.soundPolicy

        val unavailable = when {
            !input.monitoringEnabled -> MonitorStatus.OFF
            !input.permissionsGranted -> MonitorStatus.NEEDS_PERMISSION
            input.airplaneMode || input.allSimsIntentionallyDisabled -> MonitorStatus.INTENTIONALLY_DISCONNECTED
            else -> null
        }
        if (unavailable != null) {
            clearVisible(effects)
            episode = null
            recoverySince = null
            active = false
            initial = true
            return result(unavailable, effects, null)
        }

        if (!active) {
            active = true
            initial = true
        }

        // Reconcile elapsed recovery before the new observation, even if its timer was delayed.
        recoverySince?.let { since ->
            if (nowMs - since >= GRACE_MS) {
                clearVisible(effects)
                episode = null
                recoverySince = null
                initial = false
            }
        }

        if (input.connectivity == Connectivity.CONNECTED) {
            if (!initial && episode == null) return result(MonitorStatus.CONNECTED, effects, null)
            val recoveryStart = recoverySince ?: nowMs.also { recoverySince = it }
            if (nowMs - recoveryStart >= GRACE_MS) {
                clearVisible(effects)
                episode = null
                recoverySince = null
                initial = false
                return result(MonitorStatus.CONNECTED, effects, null)
            }
            val current = episode
            // Unknown status is no longer continuous once it is reliably readable.
            if (current != null && current.kind == WarningKind.CONNECTION_LOSS) {
                rescheduleForIntervalEdit(current, nowMs, input.intervalMinutes)
                if (resumedOutput && nowMs > current.nextAlertAt) current.nextAlertAt = nextAfter(current, nowMs)
                if (nowMs >= current.nextAlertAt) {
                    current.heldAlert = true
                    current.nextAlertAt = nextAfter(current, nowMs)
                }
            }
            val deadlines = mutableListOf(recoveryStart + GRACE_MS)
            if (current?.kind == WarningKind.CONNECTION_LOSS) deadlines += current.nextAlertAt
            return result(MonitorStatus.RECOVERING, effects, deadlines.filter { it > nowMs }.minOrNull())
        }

        val wasRecovering = recoverySince != null
        recoverySince = null
        val kind = if (input.connectivity == Connectivity.DISCONNECTED) WarningKind.CONNECTION_LOSS else WarningKind.STATUS_UNAVAILABLE
        var current = episode
        if (current == null || current.kind != kind || (wasRecovering && kind == WarningKind.STATUS_UNAVAILABLE)) {
            clearVisible(effects)
            current = newEpisode(kind, nowMs, input.intervalMinutes)
            episode = current
        } else {
            rescheduleForIntervalEdit(current, nowMs, input.intervalMinutes)
        }

        // Restoring sound/vibration is not a request to deliver deadlines missed during suppression.
        if (resumedOutput && nowMs > current.nextAlertAt) current.nextAlertAt = nextAfter(current, nowMs)

        if (!current.visualIssued && nowMs >= current.visualAt) {
            show(kind, effects)
            current.visualIssued = true
        }

        var alertDue = false
        if (wasRecovering && current.heldAlert) {
            current.heldAlert = false
            alertDue = true
        }
        if (nowMs >= current.nextAlertAt) {
            alertDue = true
            current.nextAlertAt = nextAfter(current, nowMs)
        }
        if (alertDue) {
            if (visible == null) show(kind, effects)
            alert(kind, input.soundPolicy, effects)
        }

        val status = when {
            kind == WarningKind.STATUS_UNAVAILABLE -> MonitorStatus.STATUS_UNAVAILABLE
            !current.visualIssued && current.initial -> MonitorStatus.INITIAL_GRACE
            !current.visualIssued -> MonitorStatus.LOSS_GRACE
            else -> MonitorStatus.OUTAGE
        }
        val deadlines = mutableListOf(current.nextAlertAt)
        if (!current.visualIssued) deadlines += current.visualAt
        return result(status, effects, deadlines.filter { it > nowMs }.minOrNull())
    }

    /** Called only for a user-dismissed warning, not for service-status notifications. */
    fun dismissWarning() { visible = null }

    private fun newEpisode(kind: WarningKind, now: Long, intervalMinutes: Int): Episode {
        val interval = if (kind == WarningKind.STATUS_UNAVAILABLE) UNKNOWN_INTERVAL_MS else intervalMinutes * MINUTE_MS
        val anchor = if (kind == WarningKind.CONNECTION_LOSS && initial) now + INITIAL_GRACE_MS else now
        val first = if (kind == WarningKind.CONNECTION_LOSS && initial) anchor else now + interval
        return Episode(kind, now, initial, now + if (initial) INITIAL_GRACE_MS else GRACE_MS, anchor, first, interval)
    }

    private fun rescheduleForIntervalEdit(current: Episode, now: Long, minutes: Int) {
        if (current.kind == WarningKind.STATUS_UNAVAILABLE) return
        val interval = minutes * MINUTE_MS
        if (current.intervalMs == interval) return
        current.intervalMs = interval
        current.nextAlertAt = if (current.initial && now < current.alertAnchor) current.alertAnchor else nextAfter(current, now)
    }

    private fun nextAfter(current: Episode, now: Long): Long =
        current.alertAnchor + ((now - current.alertAnchor) / current.intervalMs + 1) * current.intervalMs

    private fun show(kind: WarningKind, effects: MutableList<MonitorEffect>) {
        visible = kind
        effects += MonitorEffect.ShowWarning(kind)
    }

    private fun clearVisible(effects: MutableList<MonitorEffect>) {
        visible?.let { effects += MonitorEffect.ClearWarning(it) }
        visible = null
    }

    private fun alert(kind: WarningKind, policy: SoundPolicy, effects: MutableList<MonitorEffect>) {
        val sound = canSound(policy)
        val vibration = canVibrate(policy)
        if (sound || vibration) effects += MonitorEffect.Alert(kind, sound, vibration)
    }

    private fun canSound(policy: SoundPolicy) =
        policy.ringerMode == RingerMode.NORMAL && policy.dndAllowsSound && policy.channelAllowsSound

    private fun canVibrate(policy: SoundPolicy) =
        policy.ringerMode != RingerMode.SILENT && policy.dndAllowsVibration && policy.channelAllowsVibration

    private fun result(status: MonitorStatus, effects: List<MonitorEffect>, next: Long?) =
        MonitorResult(status, visible, effects.toList(), next)

    private companion object {
        const val MINUTE_MS = 60_000L
        const val GRACE_MS = 30_000L
        const val INITIAL_GRACE_MS = 5 * MINUTE_MS
        const val UNKNOWN_INTERVAL_MS = 10 * MINUTE_MS
    }
}
