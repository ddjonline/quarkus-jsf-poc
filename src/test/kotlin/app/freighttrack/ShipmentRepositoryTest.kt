package app.freighttrack

import app.freighttrack.data.ShipmentRepository
import app.freighttrack.domain.Shipment
import app.freighttrack.domain.ShipmentStatus
import app.freighttrack.domain.TrackingEventState
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@QuarkusTest
class ShipmentRepositoryTest {

    @Inject
    lateinit var repo: ShipmentRepository

    @Test
    fun `findByPro returns 4821763 as IN_TRANSIT with 3 ordered events`() {
        val shipment = repo.findByPro("4821763")
        assertNotNull(shipment)
        assertEquals("00004821763", shipment!!.proNumber)
        assertEquals("PRO-4821763", shipment.displayPro)
        assertEquals(ShipmentStatus.IN_TRANSIT, shipment.status)
        assertEquals("Memphis, TN", shipment.origin)
        assertEquals("Nashville, TN", shipment.destination)
        assertEquals(3, shipment.timeline.size)
        assertEquals(TrackingEventState.CURRENT, shipment.timeline.last().state)
        assertEquals("En route", shipment.timeline.last().title)
    }

    @Test
    fun `findByPro with canonical padded key returns same`() {
        val shipment = repo.findByPro("00004821763")
        assertNotNull(shipment)
        assertEquals("PRO-4821763", shipment!!.displayPro)
    }

    @Test
    fun `findByPro returns 3390045 as DELIVERED`() {
        val shipment = repo.findByPro("3390045")
        assertNotNull(shipment)
        assertEquals("00003390045", shipment!!.proNumber)
        assertEquals(ShipmentStatus.DELIVERED, shipment.status)
        assertEquals(4, shipment.timeline.size)
    }

    @Test
    fun `findByPro with PRO- prefix resolves`() {
        val shipment = repo.findByPro("PRO-3390045")
        assertNotNull(shipment)
        assertEquals("00003390045", shipment!!.proNumber)
    }

    @Test
    fun `findByPro with unknown pro returns null`() {
        val shipment = repo.findByPro("00000000000")
        assertNull(shipment)
    }

    @Test
    fun `findByPro with all zeros returns null`() {
        val shipment = repo.findByPro("0000000")
        assertNull(shipment)
    }

    @Test
    fun `normalizePro strips PRO- and zero-pads`() {
        assertEquals("00004821763", ShipmentRepository.normalizePro("4821763"))
        assertEquals("00003390045", ShipmentRepository.normalizePro("PRO-3390045"))
        assertEquals("00000000001", ShipmentRepository.normalizePro("1"))
        assertEquals("00000000000", ShipmentRepository.normalizePro("0000000"))
    }

    @Test
    fun `normalizePro handles spaces and non-digits`() {
        assertEquals("00004821763", ShipmentRepository.normalizePro("PRO 4821763"))
        assertEquals("00004821763", ShipmentRepository.normalizePro(" 4821763 "))
    }

    @Test
    fun `normalizePro returns empty for blank input`() {
        assertEquals("", ShipmentRepository.normalizePro(""))
        assertEquals("", ShipmentRepository.normalizePro("   "))
        assertEquals("", ShipmentRepository.normalizePro("PRO-"))
    }

    @Test
    fun `all returns both seeded shipments`() {
        val shipments = repo.all()
        assertEquals(2, shipments.size)
        val pros = shipments.map { it.proNumber }.sorted()
        assertEquals("00003390045", pros[0])
        assertEquals("00004821763", pros[1])
    }
}
