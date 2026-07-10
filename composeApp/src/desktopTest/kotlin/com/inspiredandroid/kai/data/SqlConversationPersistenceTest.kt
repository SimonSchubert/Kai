package com.inspiredandroid.kai.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.inspiredandroid.kai.TerminalLine
import com.inspiredandroid.kai.db.KaiDatabase
import com.russhwolf.settings.MapSettings
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlConversationPersistenceTest {

    private fun createPersistence(settings: MapSettings = MapSettings()): SqlConversationPersistence {
        val driver = JdbcSqliteDriver(url = JdbcSqliteDriver.IN_MEMORY, schema = KaiDatabase.Schema)
        return SqlConversationPersistence(KaiDatabase(driver), AppSettings(settings))
    }

    private fun conversation(id: String, createdAt: Long = 1000L, vararg messages: String) = Conversation(
        id = id,
        messages = messages.mapIndexed { index, content ->
            Conversation.Message(
                id = "$id-msg$index",
                role = if (index % 2 == 0) "user" else "assistant",
                content = content,
                attachments = if (index == 0) listOf(Attachment(data = "aGk=", mimeType = "text/plain", fileName = "a.txt")) else emptyList(),
            )
        },
        createdAt = createdAt,
        updatedAt = createdAt + 1000L,
        title = "Title $id",
        type = Conversation.TYPE_CHAT,
        shellTranscript = listOf(TerminalLine.Command("echo $id")),
    )

    @Test
    fun `save and loadAll round-trips conversations with messages and transcript`() {
        val persistence = createPersistence()
        val c1 = conversation("c1", 1000L, "Hello", "Hi!")
        val c2 = conversation("c2", 2000L, "Ping")
        persistence.save(c1, emptyList())
        persistence.save(c2, emptyList())

        val loaded = persistence.loadAll()

        assertEquals(listOf(c1, c2), loaded)
    }

    @Test
    fun `conversations load ordered by createdAt regardless of save order`() {
        val persistence = createPersistence()
        persistence.save(conversation("newer", 5000L), emptyList())
        persistence.save(conversation("older", 1000L), emptyList())

        assertEquals(listOf("older", "newer"), persistence.loadAll().map { it.id })
    }

    @Test
    fun `re-saving a conversation replaces its messages without touching others`() {
        val persistence = createPersistence()
        val other = conversation("other", 1000L, "Untouched")
        persistence.save(other, emptyList())
        persistence.save(conversation("c1", 2000L, "One", "Two", "Three"), emptyList())
        persistence.save(conversation("c1", 2000L, "One"), emptyList())

        val loaded = persistence.loadAll()
        assertEquals(1, loaded.single { it.id == "c1" }.messages.size)
        assertEquals("Untouched", loaded.single { it.id == "other" }.messages.single().content)
    }

    @Test
    fun `delete removes conversation and its messages`() {
        val persistence = createPersistence()
        persistence.save(conversation("c1", 1000L, "Hello"), emptyList())
        persistence.save(conversation("c2", 2000L, "Other"), emptyList())

        persistence.delete("c1", emptyList())

        assertEquals(listOf("c2"), persistence.loadAll().map { it.id })
    }

    @Test
    fun `saveShellTranscript updates only the transcript`() {
        val persistence = createPersistence()
        val original = conversation("c1", 1000L, "Hello", "Hi!")
        persistence.save(original, emptyList())

        val updated = original.copy(shellTranscript = listOf(TerminalLine.Output("new output")))
        persistence.saveShellTranscript(updated, emptyList())

        val loaded = persistence.loadAll().single()
        assertEquals(listOf<TerminalLine>(TerminalLine.Output("new output")), loaded.shellTranscript)
        assertEquals(2, loaded.messages.size)
    }

    @Test
    fun `replaceAll swaps the full content`() {
        val persistence = createPersistence()
        persistence.save(conversation("old", 1000L, "Hello"), emptyList())

        persistence.replaceAll(listOf(conversation("new1", 1000L), conversation("new2", 2000L)))

        assertEquals(listOf("new1", "new2"), persistence.loadAll().map { it.id })
    }

    @Test
    fun `pending settings key is imported into the database and removed`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)
        val fromKey = listOf(conversation("imported", 1000L, "Hello", "Hi!"))
        appSettings.setConversationsJson(ConversationJson.encodeToString(ConversationsData(conversations = fromKey)))

        val persistence = createPersistence(settings)
        persistence.save(conversation("preexisting", 500L), emptyList())

        val loaded = persistence.loadAll()

        assertEquals(listOf("imported"), loaded.map { it.id })
        assertEquals(2, loaded.single().messages.size)
        assertNull(appSettings.getConversationsJson())
    }

    @Test
    fun `blank pending key clears the database`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)
        val persistence = createPersistence(settings)
        persistence.save(conversation("c1", 1000L, "Hello"), emptyList())

        appSettings.setConversationsJson("")

        assertTrue(persistence.loadAll().isEmpty())
        assertNull(appSettings.getConversationsJson())
    }
}
