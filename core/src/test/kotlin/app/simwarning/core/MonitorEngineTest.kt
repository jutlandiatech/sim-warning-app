package app.simwarning.core

import org.junit.Assert.*
import org.junit.Test

/** Scenario IDs refer to SPECIFICATION.md, not implementation details. Time is virtual. */
class MonitorEngineTest {
    private class Scenario(initial: Connectivity = Connectivity.DISCONNECTED, val base: Long = 0) {
        val engine = MonitorEngine()
        var input = MonitorInput(initial)
        fun at(seconds: Long): MonitorResult = engine.update(base + seconds * 1_000, input)
    }

    private fun ordinary(): Scenario {
        val scenario = Scenario(base = 60_000)
        scenario.engine.update(0, MonitorInput(Connectivity.CONNECTED))
        scenario.engine.update(30_000, MonitorInput(Connectivity.CONNECTED))
        scenario.at(0)
        return scenario
    }

    private fun MonitorResult.hasVisual(kind: WarningKind) = effects.contains(MonitorEffect.ShowWarning(kind))
    private fun MonitorResult.alerts() = effects.filterIsInstance<MonitorEffect.Alert>()
    private val loss = WarningKind.CONNECTION_LOSS
    private val unknown = WarningKind.STATUS_UNAVAILABLE

    @Test fun `A01 ordinary loss below grace has no warning`() {
        val s = ordinary()
        val result = s.at(29)
        assertNull(result.visibleWarning)
        assertTrue(result.effects.isEmpty())
        assertEquals(MonitorStatus.LOSS_GRACE, result.status)
    }

    @Test fun `A02 visual warning at thirty seconds is silent`() {
        val s = ordinary()
        val result = s.at(30)
        assertTrue(result.hasVisual(loss))
        assertEquals(loss, result.visibleWarning)
        assertTrue(result.alerts().isEmpty())
    }

    @Test fun `A02 ordinary default alerts repeat every two minutes`() {
        val s = ordinary()
        s.at(30)
        for (deadline in listOf(120L, 240L, 360L)) {
            assertTrue(s.at(deadline - 1).alerts().isEmpty())
            assertEquals(listOf(MonitorEffect.Alert(loss, true, true)), s.at(deadline).alerts())
            assertTrue(s.at(deadline).effects.isEmpty())
        }
    }

    @Test fun `R12 all five intervals use loss onset`() {
        for (minutes in listOf(2, 4, 6, 8, 10)) {
            val s = ordinary()
            s.input = s.input.copy(intervalMinutes = minutes)
            s.at(1)
            assertTrue(s.at(minutes * 60L - 1).alerts().isEmpty())
            assertEquals(1, s.at(minutes * 60L).alerts().size)
        }
    }

    @Test fun `A03 startup waits five minutes then repeats from five minute anchor`() {
        val s = Scenario()
        assertEquals(MonitorStatus.INITIAL_GRACE, s.at(0).status)
        assertTrue(s.at(299).effects.isEmpty())
        val first = s.at(300)
        assertTrue(first.hasVisual(loss))
        assertEquals(1, first.alerts().size)
        assertTrue(s.at(419).alerts().isEmpty())
        assertEquals(1, s.at(420).alerts().size)
    }

    @Test fun `A09 airplane clears output and suppresses both kinds`() {
        for (connectivity in Connectivity.entries) {
            val s = Scenario(connectivity)
            s.at(0)
            s.at(600)
            s.input = s.input.copy(airplaneMode = true)
            val result = s.at(601)
            assertEquals(MonitorStatus.INTENTIONALLY_DISCONNECTED, result.status)
            assertNull(result.visibleWarning)
            assertTrue(result.alerts().isEmpty())
            assertNull(result.nextDeadlineMs)
            assertTrue(s.at(2000).effects.isEmpty())
        }
    }

    @Test fun `A04 airplane exit gets fresh initial grace`() {
        val s = ordinary()
        s.at(120)
        s.input = s.input.copy(airplaneMode = true)
        s.at(130)
        s.input = s.input.copy(airplaneMode = false)
        s.at(200)
        assertTrue(s.at(499).effects.isEmpty())
        assertEquals(1, s.at(500).alerts().size)
    }

    @Test fun `R08 intentional SIM disabling suppresses unknown warnings`() {
        val s = Scenario(Connectivity.UNKNOWN)
        s.input = s.input.copy(allSimsIntentionallyDisabled = true)
        assertEquals(MonitorStatus.INTENTIONALLY_DISCONNECTED, s.at(0).status)
        assertTrue(s.at(6000).effects.isEmpty())
        s.input = s.input.copy(allSimsIntentionallyDisabled = false, connectivity = Connectivity.DISCONNECTED)
        s.at(6100)
        assertTrue(s.at(6399).effects.isEmpty())
        assertEquals(1, s.at(6400).alerts().size)
    }

    @Test fun `A11 confirmed recovery quietly clears warnings`() {
        val s = ordinary()
        s.at(120)
        s.input = s.input.copy(connectivity = Connectivity.CONNECTED)
        assertEquals(MonitorStatus.RECOVERING, s.at(130).status)
        assertEquals(loss, s.at(159).visibleWarning)
        val recovered = s.at(160)
        assertEquals(MonitorStatus.CONNECTED, recovered.status)
        assertEquals(listOf(MonitorEffect.ClearWarning(loss)), recovered.effects)
        assertNull(recovered.nextDeadlineMs)
        assertTrue(s.at(1000).effects.isEmpty())
    }

    @Test fun `A10 short recovery preserves original outage schedule`() {
        val s = ordinary()
        s.at(30)
        s.input = s.input.copy(connectivity = Connectivity.CONNECTED)
        s.at(50)
        s.input = s.input.copy(connectivity = Connectivity.DISCONNECTED)
        assertEquals(loss, s.at(70).visibleWarning)
        assertEquals(1, s.at(120).alerts().size)
    }

    @Test fun `A12 alert held during recovery and cancelled when recovery succeeds`() {
        val s = ordinary()
        s.at(30)
        s.input = s.input.copy(connectivity = Connectivity.CONNECTED)
        s.at(110)
        assertTrue(s.at(120).alerts().isEmpty())
        assertEquals(MonitorStatus.CONNECTED, s.at(140).status)
        assertTrue(s.at(240).effects.isEmpty())
    }

    @Test fun `A12 failed recovery releases one held alert then retains cadence`() {
        val s = ordinary()
        s.at(30)
        s.input = s.input.copy(connectivity = Connectivity.CONNECTED)
        s.at(110)
        s.at(120)
        s.input = s.input.copy(connectivity = Connectivity.DISCONNECTED)
        assertEquals(1, s.at(130).alerts().size)
        assertTrue(s.at(130).effects.isEmpty())
        assertEquals(1, s.at(240).alerts().size)
    }

    @Test fun `held alert respects sound settings at release`() {
        val s = ordinary()
        s.at(30)
        s.input = s.input.copy(connectivity = Connectivity.CONNECTED)
        s.at(110)
        s.at(120)
        s.input = s.input.copy(connectivity = Connectivity.DISCONNECTED, soundPolicy = SoundPolicy(ringerMode = RingerMode.SILENT))
        assertTrue(s.at(130).alerts().isEmpty())
    }

    @Test fun `A13 DND suppresses outputs without suppressing visual`() {
        val s = ordinary()
        s.input = s.input.copy(soundPolicy = SoundPolicy(dndAllowsSound = false, dndAllowsVibration = false))
        assertTrue(s.at(30).hasVisual(loss))
        assertTrue(s.at(120).alerts().isEmpty())
        assertEquals(loss, s.at(240).visibleWarning)
    }

    @Test fun `A14 silent mode suppresses sound and vibration`() {
        val s = ordinary()
        s.input = s.input.copy(soundPolicy = SoundPolicy(ringerMode = RingerMode.SILENT))
        s.at(30)
        assertTrue(s.at(120).alerts().isEmpty())
    }

    @Test fun `A14 vibrate mode produces vibration only`() {
        val s = ordinary()
        s.input = s.input.copy(soundPolicy = SoundPolicy(ringerMode = RingerMode.VIBRATE))
        assertEquals(listOf(MonitorEffect.Alert(loss, false, true)), s.at(120).alerts())
    }

    @Test fun `system sound and vibration policies are independent`() {
        val s = ordinary()
        s.input = s.input.copy(soundPolicy = SoundPolicy(channelAllowsSound = false))
        assertEquals(listOf(MonitorEffect.Alert(loss, false, true)), s.at(120).alerts())
        s.input = s.input.copy(soundPolicy = SoundPolicy(dndAllowsVibration = false))
        assertEquals(listOf(MonitorEffect.Alert(loss, true, false)), s.at(240).alerts())
    }

    @Test fun `A15 DND exit produces no catch-up`() {
        val s = ordinary()
        s.input = s.input.copy(soundPolicy = SoundPolicy(dndAllowsSound = false, dndAllowsVibration = false))
        s.at(120)
        s.input = s.input.copy(soundPolicy = SoundPolicy())
        assertTrue(s.at(150).alerts().isEmpty())
        assertEquals(1, s.at(240).alerts().size)
    }

    @Test fun `A16 ordinary interval edit retains onset and skips past deadlines`() {
        val s = ordinary()
        s.input = s.input.copy(intervalMinutes = 4)
        s.at(1)
        s.at(240)
        s.input = s.input.copy(intervalMinutes = 2)
        assertTrue(s.at(300).alerts().isEmpty())
        assertEquals(1, s.at(360).alerts().size)
    }

    @Test fun `A28 startup interval edit preserves first alert`() {
        val s = Scenario()
        s.at(0)
        s.input = s.input.copy(intervalMinutes = 4)
        s.at(100)
        assertEquals(1, s.at(300).alerts().size)
        assertTrue(s.at(539).alerts().isEmpty())
        assertEquals(1, s.at(540).alerts().size)
    }

    @Test fun `A29 startup interval edit retains five minute anchor`() {
        val s = Scenario()
        s.input = s.input.copy(intervalMinutes = 4)
        s.at(0)
        s.at(300)
        s.input = s.input.copy(intervalMinutes = 2)
        assertTrue(s.at(480).alerts().isEmpty())
        assertEquals(1, s.at(540).alerts().size)
    }

    @Test fun `A17 dismissed warning is silently reposted at next scheduled alert`() {
        val s = ordinary()
        s.at(30)
        s.engine.dismissWarning()
        assertNull(s.at(50).visibleWarning)
        s.input = s.input.copy(soundPolicy = SoundPolicy(ringerMode = RingerMode.SILENT))
        val result = s.at(120)
        assertTrue(result.hasVisual(loss))
        assertTrue(result.alerts().isEmpty())
    }

    @Test fun `A19 off cancels warnings and deadlines for both types`() {
        for (connectivity in listOf(Connectivity.DISCONNECTED, Connectivity.UNKNOWN)) {
            val s = Scenario(connectivity)
            s.at(0)
            s.at(600)
            s.input = s.input.copy(monitoringEnabled = false)
            val off = s.at(601)
            assertEquals(MonitorStatus.OFF, off.status)
            assertNull(off.visibleWarning)
            assertNull(off.nextDeadlineMs)
            assertTrue(s.at(3600).effects.isEmpty())
        }
    }

    @Test fun `A20 re-enable starts fresh`() {
        val s = ordinary()
        s.at(120)
        s.input = s.input.copy(monitoringEnabled = false)
        s.at(121)
        s.input = s.input.copy(monitoringEnabled = true)
        s.at(200)
        assertTrue(s.at(499).effects.isEmpty())
        assertEquals(1, s.at(500).alerts().size)
    }

    @Test fun `A21 established unknown visual at thirty seconds and alerts every ten minutes`() {
        val s = ordinary()
        s.input = s.input.copy(connectivity = Connectivity.UNKNOWN)
        s.at(1)
        assertNull(s.at(30).visibleWarning)
        assertTrue(s.at(31).hasVisual(unknown))
        assertTrue(s.at(600).alerts().isEmpty())
        assertEquals(listOf(MonitorEffect.Alert(unknown, true, true)), s.at(601).alerts())
        assertEquals(1, s.at(1201).alerts().size)
    }

    @Test fun `A30 startup unknown visuals wait five minutes and alerts wait ten`() {
        val s = Scenario(Connectivity.UNKNOWN)
        s.at(0)
        assertTrue(s.at(299).effects.isEmpty())
        assertTrue(s.at(300).hasVisual(unknown))
        assertTrue(s.at(300).alerts().isEmpty())
        assertEquals(1, s.at(600).alerts().size)
        assertEquals(1, s.at(1200).alerts().size)
    }

    @Test fun `A22 unreadable replaces confirmed outage and cancels old schedule`() {
        val s = ordinary()
        s.at(120)
        s.input = s.input.copy(connectivity = Connectivity.UNKNOWN)
        val result = s.at(130)
        assertTrue(result.effects.contains(MonitorEffect.ClearWarning(loss)))
        assertNull(result.visibleWarning)
        assertTrue(s.at(160).hasVisual(unknown))
        assertTrue(s.at(240).alerts().isEmpty())
        assertEquals(listOf(MonitorEffect.Alert(unknown, true, true)), s.at(730).alerts())
    }

    @Test fun `A23 unreadable to disconnected starts new ordinary loss episode`() {
        val s = ordinary()
        s.input = s.input.copy(connectivity = Connectivity.UNKNOWN)
        s.at(10)
        s.at(610)
        s.input = s.input.copy(connectivity = Connectivity.DISCONNECTED)
        val result = s.at(620)
        assertTrue(result.effects.contains(MonitorEffect.ClearWarning(unknown)))
        assertTrue(s.at(650).hasVisual(loss))
        assertEquals(listOf(MonitorEffect.Alert(loss, true, true)), s.at(740).alerts())
    }

    @Test fun `A23 unreadable to connected confirms quietly after thirty seconds`() {
        val s = Scenario(Connectivity.UNKNOWN)
        s.at(0)
        s.at(600)
        s.input = s.input.copy(connectivity = Connectivity.CONNECTED)
        s.at(610)
        assertEquals(MonitorStatus.CONNECTED, s.at(640).status)
        assertTrue(s.at(1200).effects.isEmpty())
    }

    @Test fun `A38 dismissed unknown notification reposts on ten minute deadline`() {
        val s = Scenario(Connectivity.UNKNOWN)
        s.at(0)
        s.at(300)
        s.engine.dismissWarning()
        s.input = s.input.copy(soundPolicy = SoundPolicy(ringerMode = RingerMode.SILENT))
        assertNull(s.at(400).visibleWarning)
        assertTrue(s.at(600).hasVisual(unknown))
    }

    @Test fun `A24 missing permissions never starts unknown warning schedule`() {
        val s = Scenario(Connectivity.UNKNOWN)
        s.input = s.input.copy(permissionsGranted = false)
        assertEquals(MonitorStatus.NEEDS_PERMISSION, s.at(0).status)
        val later = s.at(6000)
        assertTrue(later.effects.isEmpty())
        assertNull(later.nextDeadlineMs)
    }

    @Test fun `A34 permission revocation clears either warning`() {
        for (connectivity in listOf(Connectivity.DISCONNECTED, Connectivity.UNKNOWN)) {
            val s = Scenario(connectivity)
            s.at(0)
            s.at(600)
            s.input = s.input.copy(permissionsGranted = false)
            val result = s.at(610)
            assertEquals(MonitorStatus.NEEDS_PERMISSION, result.status)
            assertNull(result.visibleWarning)
            assertNull(result.nextDeadlineMs)
            assertTrue(result.alerts().isEmpty())
        }
    }

    @Test fun `A35 permission restoration starts fresh initial grace`() {
        val s = Scenario()
        s.input = s.input.copy(permissionsGranted = false)
        s.at(0)
        s.input = s.input.copy(permissionsGranted = true)
        s.at(100)
        assertTrue(s.at(399).effects.isEmpty())
        assertEquals(1, s.at(400).alerts().size)
    }

    @Test fun `A36 permission restoration does not enable monitoring`() {
        val s = Scenario()
        s.input = s.input.copy(permissionsGranted = false, monitoringEnabled = false)
        s.at(0)
        s.input = s.input.copy(permissionsGranted = true)
        assertEquals(MonitorStatus.OFF, s.at(600).status)
        assertNull(s.at(1000).nextDeadlineMs)
    }

    @Test fun `A31 stable startup connection establishes ordinary monitoring`() {
        val s = Scenario(Connectivity.CONNECTED)
        s.at(0)
        assertEquals(MonitorStatus.CONNECTED, s.at(30).status)
        s.input = s.input.copy(connectivity = Connectivity.DISCONNECTED)
        s.at(40)
        assertTrue(s.at(70).hasVisual(loss))
        assertEquals(1, s.at(160).alerts().size)
    }

    @Test fun `A32 short reconnection during loss grace holds visual until failure`() {
        val s = ordinary()
        s.input = s.input.copy(connectivity = Connectivity.CONNECTED)
        s.at(20)
        assertTrue(s.at(30).effects.isEmpty())
        s.input = s.input.copy(connectivity = Connectivity.DISCONNECTED)
        assertTrue(s.at(40).hasVisual(loss))
        assertEquals(1, s.at(120).alerts().size)
    }

    @Test fun `A33 successful recovery during initial loss grace never warns`() {
        val s = ordinary()
        s.input = s.input.copy(connectivity = Connectivity.CONNECTED)
        s.at(20)
        assertTrue(s.at(30).effects.isEmpty())
        assertEquals(MonitorStatus.CONNECTED, s.at(50).status)
        assertTrue(s.at(120).effects.isEmpty())
    }

    @Test fun `R05 engine supplies next deadline instead of requiring polling`() {
        val s = ordinary()
        assertEquals(90_000L, s.at(1).nextDeadlineMs)
        assertEquals(180_000L, s.at(30).nextDeadlineMs)
        assertEquals(300_000L, s.at(120).nextDeadlineMs)
    }

    @Test fun `repeated unchanged observations do not restart grace or duplicate warnings`() {
        val s = ordinary()
        for (second in 1L..29L) assertTrue(s.at(second).effects.isEmpty())
        assertTrue(s.at(30).hasVisual(loss))
        assertTrue(s.at(31).effects.isEmpty())
    }

    @Test fun `interval outside agreed choices is rejected`() {
        for (invalid in listOf(-1, 0, 1, 3, 11)) {
            assertThrows(IllegalArgumentException::class.java) {
                MonitorEngine().update(0, MonitorInput(Connectivity.DISCONNECTED, intervalMinutes = invalid))
            }
        }
    }

    @Test fun `time cannot run backwards`() {
        val engine = MonitorEngine()
        engine.update(100, MonitorInput(Connectivity.CONNECTED))
        assertThrows(IllegalArgumentException::class.java) { engine.update(99, MonitorInput(Connectivity.CONNECTED)) }
    }

    @Test fun `negative monotonic time is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { MonitorEngine().update(-1, MonitorInput(Connectivity.CONNECTED)) }
    }

    @Test fun `R16 delayed callback recognizes completed recovery before a new loss`() {
        val s = ordinary()
        s.at(30)
        s.input = s.input.copy(connectivity = Connectivity.CONNECTED)
        s.at(50)
        // The scheduler was delayed: continuous recovery completed at second 80.
        s.input = s.input.copy(connectivity = Connectivity.DISCONNECTED)
        val newLoss = s.at(100)
        assertNull(newLoss.visibleWarning)
        assertEquals(MonitorStatus.LOSS_GRACE, newLoss.status)
        assertTrue(s.at(130).hasVisual(loss))
        assertEquals(1, s.at(220).alerts().size)
    }

    @Test fun `A31 delayed startup callback still recognizes thirty seconds of stable service`() {
        val s = Scenario(Connectivity.CONNECTED)
        s.at(0)
        s.input = s.input.copy(connectivity = Connectivity.DISCONNECTED)
        assertEquals(MonitorStatus.LOSS_GRACE, s.at(100).status)
        assertTrue(s.at(130).hasVisual(loss))
    }

    @Test fun `R26 DND exit after delayed deadlines must not replay overdue audio`() {
        val s = ordinary()
        s.input = s.input.copy(soundPolicy = SoundPolicy(dndAllowsSound = false, dndAllowsVibration = false))
        s.at(120)
        s.input = s.input.copy(soundPolicy = SoundPolicy())
        assertTrue(s.at(650).alerts().isEmpty())
        assertEquals(1, s.at(720).alerts().size)
    }

    @Test fun `R26 silent exit after delayed deadlines must not replay overdue alerts`() {
        val s = ordinary()
        s.input = s.input.copy(soundPolicy = SoundPolicy(ringerMode = RingerMode.SILENT))
        s.at(120)
        s.input = s.input.copy(soundPolicy = SoundPolicy())
        assertTrue(s.at(650).alerts().isEmpty())
        assertEquals(1, s.at(720).alerts().size)
    }
}
