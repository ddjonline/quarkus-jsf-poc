package app.freighttrack

import app.freighttrack.session.RedisSessionStore
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
@TestProfile(RedisOnlyTestProfile::class)
class RedisSessionStoreTest {

    @Inject
    lateinit var store: RedisSessionStore

    @Test
    fun `load for missing key returns empty TrackingSearch`() {
        val search = store.load(UUID.randomUUID().toString())
        assertNotNull(search)
        assertEquals(1, search.entries.size)
        assertTrue(search.results.isEmpty())
    }

@Test
    fun `save and load preserves input values and results`() {
        val id = UUID.randomUUID().toString()
        val search = store.load(id)
        search.entries.clear()
        search.entries.add(app.freighttrack.domain.ProNumberEntry(value = "4821763", checked = true))
        search.entries.add(app.freighttrack.domain.ProNumberEntry(value = "3390045", checked = true))
        assertEquals(2, search.entries.size)
        store.save(id, search)

        val loaded = store.load(id)
        assertEquals(2, loaded.entries.size)
        assertEquals("4821763", loaded.entries[0].value)
        assertTrue(loaded.entries[0].checked)
        assertEquals("3390045", loaded.entries[1].value)
        assertTrue(loaded.entries[1].checked)
    }

    @Test
    fun `clear removes session key`() {
        val id = UUID.randomUUID().toString()
        store.save(id, store.load(id))
        store.clear(id)

        val loaded = store.load(id)
        assertEquals(1, loaded.entries.size)
        assertTrue(loaded.results.isEmpty())
    }
}