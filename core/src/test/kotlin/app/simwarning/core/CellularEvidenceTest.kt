package app.simwarning.core

import org.junit.Assert.*
import org.junit.Test

class CellularEvidenceTest {
    @Test fun `F02 data-only or voice cellular registration proves service`() {
        assertEquals(SimService.NORMAL, classifyCellular(CellularEvidence(true, true)))
    }
    @Test fun `F02 Wi-Fi calling alone never proves cellular service`() {
        assertEquals(SimService.UNKNOWN, classifyCellular(CellularEvidence(legacyVoiceInService = true)))
        assertEquals(SimService.UNAVAILABLE, classifyCellular(CellularEvidence(cellularRegistrationAvailable = true, legacyVoiceInService = true)))
    }
    @Test fun `F02 emergency service without normal registration remains unavailable`() {
        assertEquals(SimService.EMERGENCY_ONLY, classifyCellular(CellularEvidence(true, false, true)))
    }
    @Test fun `F02 normal data registration remains sufficient despite voice emergency indication`() {
        assertEquals(SimService.NORMAL, classifyCellular(CellularEvidence(true, true, true)))
    }
    @Test fun `F01 legacy voice needs cellular bearer evidence`() {
        assertEquals(SimService.NORMAL, classifyCellular(CellularEvidence(legacyVoiceInService = true, legacyVoiceNetworkIsCellular = true)))
        assertEquals(SimService.UNKNOWN, classifyCellular(CellularEvidence(legacyVoiceNetworkIsCellular = true)))
    }
    @Test fun `F03 confirmed absent SIM differs from empty uncertain inventory`() {
        assertEquals(SimService.UNAVAILABLE, classifyCellular(CellularEvidence(definitelyNoSim = true)))
        assertEquals(SimService.UNKNOWN, classifyCellular(CellularEvidence()))
    }
}
