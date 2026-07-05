package app.freighttrack

import app.freighttrack.web.TrackingBean
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class TrackingBeanTest {

    @Inject
    lateinit var bean: TrackingBean

    @BeforeEach
    fun setUp() {
        bean.reset()
    }

    @Test
    fun `addNumber up to max`() {
        assertEquals(1, bean.getCount())
        for (i in 2..10) {
            bean.addNumber()
        }
        assertEquals(10, bean.getCount())
        assertTrue(bean.isAddDisabled())
    }

    @Test
    fun `addNumber blocked at max`() {
        for (i in 1..9) bean.addNumber()
        assertEquals(10, bean.getCount())
        bean.addNumber()
        assertEquals(10, bean.getCount())
    }

    @Test
    fun `addNumber preserves existing entries`() {
        bean.getEntries()[0].value = "4821763"
        bean.addNumber()
        assertEquals(2, bean.getCount())
        assertEquals("4821763", bean.getEntries()[0].value)
        assertTrue(bean.getEntries()[1].isBlank())
    }

    @Test
    fun `removeNumber respects min`() {
        assertEquals(1, bean.getCount())
        assertFalse(bean.isRemoveVisible())

        bean.addNumber()
        assertEquals(2, bean.getCount())
        assertTrue(bean.isRemoveVisible())

        val entryId = bean.getEntries()[1].id
        bean.removeNumber(entryId)
        assertEquals(1, bean.getCount())
    }

    @Test
    fun `searchActive false when all blank`() {
        assertFalse(bean.isSearchActive())
    }

    @Test
    fun `searchActive false when populated but unchecked`() {
        bean.getEntries()[0].value = "4821763"
        bean.getEntries()[0].checked = false
        assertFalse(bean.isSearchActive())
    }

    @Test
    fun `searchActive true when populated and checked`() {
        bean.getEntries()[0].value = "4821763"
        bean.getEntries()[0].checked = true
        assertTrue(bean.isSearchActive())
    }

    @Test
    fun `findButtonLabel singular`() {
        bean.getEntries()[0].value = "4821763"
        bean.getEntries()[0].checked = true
        assertEquals("Find My Shipment", bean.getFindButtonLabel())
    }

    @Test
    fun `findButtonLabel ignores unchecked entries`() {
        bean.getEntries()[0].value = "4821763"
        bean.getEntries()[0].checked = false
        assertEquals("Find My Shipment", bean.getFindButtonLabel())
        assertFalse(bean.isSearchActive())
    }

    @Test
    fun `findButtonLabel plural when multiple checked and non-blank`() {
        bean.getEntries()[0].value = "4821763"
        bean.getEntries()[0].checked = true
        bean.addNumber()
        bean.getEntries()[1].value = "3390045"
        bean.getEntries()[1].checked = true
        assertEquals("Find My Shipments (2)", bean.getFindButtonLabel())
    }

    @Test
    fun `validateEntry marks valid PRO as checked`() {
        bean.getEntries()[0].value = "4821763"
        bean.getEntries()[0].checked = false
        bean.validateEntry(bean.getEntries()[0].id)
        assertTrue(bean.getEntries()[0].checked)
    }

    @Test
    fun `validateEntry marks invalid input as unchecked`() {
        bean.getEntries()[0].value = "abc"
        bean.getEntries()[0].checked = true
        bean.validateEntry(bean.getEntries()[0].id)
        assertFalse(bean.getEntries()[0].checked)
    }

    @Test
    fun `validateEntry on one row does not clear other rows`() {
        bean.getEntries()[0].value = "4821763"
        bean.getEntries()[0].checked = true
        bean.addNumber()
        bean.getEntries()[1].value = "3390045"
        bean.getEntries()[1].checked = true
        bean.findShipments()
        assertEquals(2, bean.getResultsCount())

        bean.validateEntry(bean.getEntries()[0].id)
        assertEquals("4821763", bean.getEntries()[0].value)
        assertTrue(bean.getEntries()[0].checked)
        assertEquals("3390045", bean.getEntries()[1].value)
        assertTrue(bean.getEntries()[1].checked)
        assertEquals(2, bean.getResultsCount())
    }

    @Test
    fun `session preserves inputs and results across redis load cycle`() {
        bean.getEntries()[0].value = "4821763"
        bean.getEntries()[0].checked = true
        bean.findShipments()
        assertTrue(bean.isHasResults())

        val capturedResults = bean.getResultsCount()
        val capturedValue = bean.getEntries()[0].value
        val capturedChecked = bean.getEntries()[0].checked

        bean.init() // simulate fresh request — reloads from Redis
        assertEquals(capturedValue, bean.getEntries()[0].value)
        assertEquals(capturedChecked, bean.getEntries()[0].checked)
        assertEquals(capturedResults, bean.getResultsCount())
    }

    @Test
    fun `findShipments only processes checked entries`() {
        bean.getEntries()[0].value = "4821763"
        bean.getEntries()[0].checked = true
        bean.addNumber()
        bean.getEntries()[1].value = "3390045"
        bean.getEntries()[1].checked = false
        bean.findShipments()
        assertTrue(bean.isHasResults())
        assertEquals(1, bean.getResultsCount())
    }

    @Test
    fun `removeNumber removes associated result`() {
        bean.getEntries()[0].value = "4821763"
        bean.getEntries()[0].checked = true
        bean.addNumber()
        bean.getEntries()[1].value = "3390045"
        bean.getEntries()[1].checked = true
        bean.findShipments()
        assertEquals(2, bean.getResultsCount())

        val firstId = bean.getEntries()[0].id
        bean.removeNumber(firstId)
        assertEquals(1, bean.getResultsCount())
        assertEquals("00003390045", bean.getResults()[0].queriedPro)
    }

    @Test
    fun `reset clears entries and results`() {
        bean.getEntries()[0].value = "4821763"
        bean.getEntries()[0].checked = true
        bean.findShipments()
        assertTrue(bean.isHasResults())

        bean.reset()
        assertEquals(1, bean.getCount())
        assertTrue(bean.getEntries()[0].isBlank())
        assertFalse(bean.isHasResults())
    }
}
