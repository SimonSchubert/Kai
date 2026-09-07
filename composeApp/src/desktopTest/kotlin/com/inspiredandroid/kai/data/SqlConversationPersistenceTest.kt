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

    private fun createDatabase(): KaiDatabase = KaiDatabase(JdbcSqliteDriver(url = JdbcSqliteDriver.IN_MEMORY, schema = KaiDatabase.Schema))

    private fun createPersistence(
        settings: MapSettings = MapSettings(),
        database: KaiDatabase = createDatabase(),
    ): SqlConversationPersistence = SqlConversationPersistence(database, AppSettings(settings))

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
    fun `attachment too large for a database row is dropped, keeping the message`() {
        val database = createDatabase()
        val persistence = createPersistence(database = database)
        val huge = Conversation.Message(
            id = "m0",
            role = "user",
            content = "What does this PDF say?",
            attachments = listOf(Attachment(data = "A".repeat(3_000_000), mimeType = "application/pdf", fileName = "big.pdf")),
        )
        persistence.save(conversation("c1").copy(messages = listOf(huge)), emptyList())

        val loaded = persistence.loadAll().single().messages.single()
        assertEquals("What does this PDF say?", loaded.content)
        assertTrue(loaded.attachments.isEmpty())
        assertTrue(storedMessageBytes(database).single() < CURSOR_WINDOW_BYTES)
    }

    @Test
    fun `message text beyond the row budget is truncated instead of stored whole`() {
        val database = createDatabase()
        val persistence = createPersistence(database = database)
        val huge = Conversation.Message(id = "m0", role = "assistant", content = "x".repeat(3_000_000))
        persistence.save(conversation("c1").copy(messages = listOf(huge)), emptyList())

        val loaded = persistence.loadAll().single().messages.single()
        assertTrue(loaded.content.length < huge.content.length)
        assertTrue(storedMessageBytes(database).single() < CURSOR_WINDOW_BYTES)
    }

    @Test
    fun `oversized rows written by older versions are skipped, not crashed on`() {
        val database = createDatabase()
        val persistence = createPersistence(database = database)
        persistence.save(conversation("c1", 1000L, "Hello"), emptyList())
        // What an older build stored: a row far past Android's CursorWindow, which
        // aborted the whole load with SQLiteBlobTooBigException.
        database.conversationQueries.insertMessage(
            conversationId = "c1",
            orderIndex = 99L,
            messageJson = ConversationJson.encodeToString(
                Conversation.Message(id = "huge", role = "user", content = "x".repeat(3_000_000)),
            ),
        )

        val loaded = persistence.loadAll().single()
        assertEquals(listOf("Hello"), loaded.messages.map { it.content })
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

    /** Byte size of every stored message row, read back without the persistence-side filter. */
    private fun storedMessageBytes(database: KaiDatabase): List<Int> = database.conversationQueries.selectAllMessages(Long.MAX_VALUE).executeAsList()
        .map { it.messageJson.encodeToByteArray().size }

    private companion object {
        /** Android's per-row CursorWindow limit — the size the crash in issue #475 hit. */
        const val CURSOR_WINDOW_BYTES = 2_000_000
    }
}
