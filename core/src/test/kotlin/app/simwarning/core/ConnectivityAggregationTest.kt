package app.simwarning.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectivityAggregationTest {
    @Test fun `A05 any normal SIM proves mobile service despite other failures`() {
        for (other in SimService.entries) {
            assertEquals(Connectivity.CONNECTED, aggregateServices(listOf(SimService.NORMAL, other)))
            assertEquals(Connectivity.CONNECTED, aggregateServices(listOf(other, SimService.NORMAL)))
        }
    }

    @Test fun `A06 emergency only on every SIM is unavailable normal service`() {
        assertEquals(Connectivity.DISCONNECTED, aggregateServices(listOf(SimService.EMERGENCY_ONLY)))
        assertEquals(Connectivity.DISCONNECTED, aggregateServices(listOf(SimService.EMERGENCY_ONLY, SimService.UNAVAILABLE)))
    }

    @Test fun `R09 unknown plus unavailable cannot prove total outage`() {
        assertEquals(Connectivity.UNKNOWN, aggregateServices(listOf(SimService.UNKNOWN, SimService.UNAVAILABLE)))
    }

    @Test fun `R08 empty subscription list does not imply intentional disabling or confirmed outage`() {
        assertEquals(Connectivity.UNKNOWN, aggregateServices(emptyList()))
    }

    @Test fun `all observed SIMs unavailable means no normal service`() {
        assertEquals(Connectivity.DISCONNECTED, aggregateServices(listOf(SimService.UNAVAILABLE, SimService.UNAVAILABLE)))
    }
}
