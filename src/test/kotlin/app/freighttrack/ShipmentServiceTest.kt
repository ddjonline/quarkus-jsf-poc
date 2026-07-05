package app.freighttrack

import app.freighttrack.domain.LookupState
import app.freighttrack.domain.ShipmentStatus
import app.freighttrack.domain.TrackingEventState
import app.freighttrack.service.ShipmentService
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@QuarkusTest
class ShipmentServiceTest {

    @Inject
    lateinit var service: ShipmentService

    @Test
    fun `find with short PRO returns canonical shipment`() {
        val shipment = service.find("4821763")
        assertNotNull(shipment)
        assertEquals("00004821763", shipment!!.proNumber)
        assertEquals(ShipmentStatus.IN_TRANSIT, shipment.status)
        assertEquals(3, shipment.timeline.size)
        assertEquals(TrackingEventState.CURRENT, shipment.timeline.last().state)
    }

    @Test
    fun `find with PRO- prefix resolves`() {
        val shipment = service.find("PRO-3390045")
        assertNotNull(shipment)
        assertEquals(ShipmentStatus.DELIVERED, shipment!!.status)
    }

    @Test
    fun `find with all zeros returns null`() {
        val shipment = service.find("0000000")
        assertNull(shipment)
    }

    @Test
    fun `lookup dedupes and preserves order`() {
        val results = service.lookup(listOf("4821763", "PRO-3390045", "4821763"))
        assertEquals(2, results.size)
        assertEquals("00004821763", results[0].queriedPro)
        assertEquals(LookupState.LOADED, results[0].state)
        assertEquals("00003390045", results[1].queriedPro)
        assertEquals(LookupState.LOADED, results[1].state)
    }

    @Test
    fun `lookup filters blanks`() {
        val results = service.lookup(listOf("", "   ", "4821763"))
        assertEquals(1, results.size)
        assertEquals("00004821763", results[0].queriedPro)
    }

    @Test
    fun `lookup marks unknown as NOT_FOUND`() {
        val results = service.lookup(listOf("00000000001"))
        assertEquals(1, results.size)
        assertEquals(LookupState.NOT_FOUND, results[0].state)
        assertNull(results[0].shipment)
    }

    @Test
    fun `lookup with valid and unknown mixed`() {
        val results = service.lookup(listOf("4821763", "99999999999"))
        assertEquals(2, results.size)
        assertEquals(LookupState.LOADED, results[0].state)
        assertEquals(LookupState.NOT_FOUND, results[1].state)
    }
}
