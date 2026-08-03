package com.inspiredandroid.kai.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract for the shared settings-backed JSON persistence used by every store: an unwritten or
 * corrupt blob degrades to the default instead of throwing, reads never write, and
 * read-modify-write goes through one lock.
 */
class SettingsJsonTest {

    @Serializable
    private data class Item(val id: String, val value: Int = 0)

    @Serializable
    private data class Config(val enabled: Boolean = true, val count: Int = 3)

    /** Stand-in for a pair of AppSettings accessors, with visibility into how often we wrote. */
    private class FakeSlot(var raw: String = "") {
        var writes = 0
            private set

        fun read(): String = raw

        fun write(value: String) {
            raw = value
            writes++
        }
    }

    private fun FakeSlot.list(
        recover: ((String) -> List<Item>?)? = null,
        migrate: ((List<Item>) -> List<Item>?)? = null,
        onWrite: ((List<Item>) -> Unit)? = null,
    ) = SettingsJsonList(
        read = ::read,
        write = ::write,
        itemSerializer = serializer<Item>(),
        label = "test",
        onWrite = onWrite,
        recover = recover,
        migrate = migrate,
    )

    @Test
    fun `unwritten list reads as empty without writing`() {
        val slot = FakeSlot()
        val list = slot.list()

        assertEquals(emptyList(), list.get())
        assertEquals(0, slot.writes)
        assertEquals("", slot.raw)
    }

    @Test
    fun `corrupt list degrades to empty and leaves the raw value alone`() {
        val slot = FakeSlot("{not json at all")
        val list = slot.list()

        assertEquals(emptyList(), list.get())
        assertEquals(0, slot.writes)
        assertEquals("{not json at all", slot.raw)
    }

    @Test
    fun `update round-trips through the persisted string`() = runTest {
        val slot = FakeSlot()
        val list = slot.list()

        list.update { it + Item("a", 1) }
        list.update { it + Item("b", 2) }

        assertEquals(listOf(Item("a", 1), Item("b", 2)), list.get())
        // Re-reading through a fresh accessor proves the state lives in the string, not the memo.
        assertEquals(listOf(Item("a", 1), Item("b", 2)), slot.list().get())
    }

    @Test
    fun `onWrite observes every write but not reads`() = runTest {
        val slot = FakeSlot()
        val observed = mutableListOf<List<Item>>()
        val list = slot.list(onWrite = { observed.add(it) })

        list.get()
        assertEquals(0, observed.size)

        list.update { it + Item("a") }
        assertEquals(listOf(listOf(Item("a"))), observed)
    }

    @Test
    fun `migrate hook upgrades rows and persists the upgrade once`() {
        val slot = FakeSlot("""[{"id":"a","value":0},{"id":"b","value":0}]""")
        val list = slot.list(migrate = { decoded ->
            decoded.map { if (it.value == 0) it.copy(value = 9) else it }.takeIf { it != decoded }
        })

        assertEquals(listOf(Item("a", 9), Item("b", 9)), list.get())
        assertEquals(1, slot.writes)

        // Second load has nothing left to upgrade, so it must not write again.
        assertEquals(listOf(Item("a", 9), Item("b", 9)), list.get())
        assertEquals(1, slot.writes)
    }

    @Test
    fun `recover hook reads a legacy shape that no longer decodes`() {
        val slot = FakeSlot("""{"id":"legacy","value":7}""")
        val list = slot.list(recover = { raw ->
            listOf(SharedJson.decodeFromString<Item>(raw))
        })

        assertEquals(listOf(Item("legacy", 7)), list.get())
        assertEquals(0, slot.writes)
    }

    @Test
    fun `a write behind the accessor's back is picked up on the next read`() {
        val slot = FakeSlot()
        val list = slot.list()
        assertEquals(emptyList(), list.get())

        slot.raw = """[{"id":"external","value":1}]"""

        assertEquals(listOf(Item("external", 1)), list.get())
    }

    @Test
    fun `value falls back to the default when unwritten or corrupt`() {
        val unwritten = FakeSlot()
        val corrupt = FakeSlot("]]not json[[")

        listOf(unwritten, corrupt).forEach { slot ->
            val value = SettingsJsonValue(
                read = slot::read,
                write = slot::write,
                serializer = serializer<Config>(),
                label = "test",
                default = { Config() },
            )
            assertEquals(Config(), value.get())
            assertEquals(0, slot.writes)
        }
    }

    @Test
    fun `value update round-trips`() = runTest {
        val slot = FakeSlot()
        val value = SettingsJsonValue(
            read = slot::read,
            write = slot::write,
            serializer = serializer<Config>(),
            label = "test",
            default = { Config() },
        )

        value.update { it.copy(count = it.count + 1) }

        assertEquals(Config(enabled = true, count = 4), value.get())
        assertTrue(slot.raw.contains("\"count\":4"))
    }

    @Test
    fun `a fresh install reads an empty task list without touching settings`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)
        val keysBefore = settings.keys

        assertEquals(emptyList(), TaskStore(appSettings).getAllTasks())
        assertEquals(keysBefore, settings.keys)
    }

    /**
     * A blank blob used to reach `TaskStore`'s decode and log an error before returning empty —
     * it was the one list store without a blank guard. The shared decoder gives every store the
     * same treatment.
     */
    @Test
    fun `a blank tasks blob reads as empty`() {
        val appSettings = AppSettings(MapSettings())
        appSettings.setScheduledTasksJson("")

        assertEquals(emptyList(), TaskStore(appSettings).getAllTasks())
    }
}
