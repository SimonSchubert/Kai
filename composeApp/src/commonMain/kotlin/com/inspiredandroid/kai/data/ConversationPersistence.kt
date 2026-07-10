package com.inspiredandroid.kai.data

import app.cash.sqldelight.db.SqlDriver
import com.inspiredandroid.kai.TerminalLine
import com.inspiredandroid.kai.db.KaiDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val ConversationJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Persistence backend for [ConversationStorage]. SQL-capable platforms store one
 * row per conversation and one row per message, so a save costs one conversation,
 * not the whole history. Platforms without a SQL driver (wasm) fall back to the
 * settings-key JSON blob. The `snapshot` parameter carries the full up-to-date
 * conversation list for backends that can only write wholesale.
 */
interface ConversationPersistence {
    fun loadAll(): List<Conversation>
    fun replaceAll(conversations: List<Conversation>)
    fun save(conversation: Conversation, snapshot: List<Conversation>)
    fun delete(id: String, snapshot: List<Conversation>)
    fun saveShellTranscript(conversation: Conversation, snapshot: List<Conversation>)
}

/** Returns null on platforms without a bundled SQLite (wasm). */
expect fun createConversationSqlDriver(): SqlDriver?

fun createConversationPersistence(appSettings: AppSettings): ConversationPersistence {
    val driver = createConversationSqlDriver()
    return if (driver != null) {
        SqlConversationPersistence(KaiDatabase(driver), appSettings)
    } else {
        SettingsConversationPersistence(appSettings)
    }
}

class SqlConversationPersistence(
    private val database: KaiDatabase,
    private val appSettings: AppSettings,
) : ConversationPersistence {

    private val queries get() = database.conversationQueries

    override fun loadAll(): List<Conversation> {
        importPendingJson()
        val messagesByConversation = queries.selectAllMessages().executeAsList()
            .groupBy({ it.conversationId }) { decodeMessage(it.messageJson) }
        return queries.selectAllConversations().executeAsList().map { row ->
            Conversation(
                id = row.id,
                messages = messagesByConversation[row.id].orEmpty().filterNotNull(),
                createdAt = row.createdAt,
                updatedAt = row.updatedAt,
                title = row.title,
                type = row.type,
                shellTranscript = decodeTranscript(row.shellTranscriptJson),
            )
        }
    }

    override fun replaceAll(conversations: List<Conversation>) {
        database.transaction {
            queries.deleteAllMessages()
            queries.deleteAllConversations()
            conversations.forEach { insert(it) }
        }
    }

    override fun save(conversation: Conversation, snapshot: List<Conversation>) {
        database.transaction {
            queries.deleteMessages(conversation.id)
            insert(conversation)
        }
    }

    override fun delete(id: String, snapshot: List<Conversation>) {
        database.transaction {
            queries.deleteMessages(id)
            queries.deleteConversation(id)
        }
    }

    override fun saveShellTranscript(conversation: Conversation, snapshot: List<Conversation>) {
        queries.updateShellTranscript(
            shellTranscriptJson = ConversationJson.encodeToString(conversation.shellTranscript),
            id = conversation.id,
        )
    }

    /**
     * The settings key doubles as an import inbox: settings import (and the one-time
     * migration from the pre-database format) writes the full conversation set there.
     * A present key always replaces the database content — including an unparseable
     * or blank value, which import writes to mean "clear conversations".
     */
    private fun importPendingJson() {
        val pending = appSettings.getConversationsJson() ?: return
        val conversations = try {
            ConversationJson.decodeFromString<ConversationsData>(pending).conversations
        } catch (_: Exception) {
            emptyList()
        }
        replaceAll(conversations)
        appSettings.removeConversationsJson()
    }

    private fun insert(conversation: Conversation) {
        queries.upsertConversation(
            id = conversation.id,
            title = conversation.title,
            type = conversation.type,
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt,
            shellTranscriptJson = ConversationJson.encodeToString(conversation.shellTranscript),
        )
        conversation.messages.forEachIndexed { index, message ->
            queries.insertMessage(
                conversationId = conversation.id,
                orderIndex = index.toLong(),
                messageJson = ConversationJson.encodeToString(message),
            )
        }
    }

    private fun decodeMessage(json: String): Conversation.Message? = try {
        ConversationJson.decodeFromString<Conversation.Message>(json)
    } catch (_: Exception) {
        null
    }

    private fun decodeTranscript(json: String): List<TerminalLine> = try {
        ConversationJson.decodeFromString<List<TerminalLine>>(json)
    } catch (_: Exception) {
        emptyList()
    }
}

class SettingsConversationPersistence(private val appSettings: AppSettings) : ConversationPersistence {

    override fun loadAll(): List<Conversation> {
        val data = appSettings.getConversationsJson() ?: return emptyList()
        return try {
            ConversationJson.decodeFromString<ConversationsData>(data).conversations
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun replaceAll(conversations: List<Conversation>) = writeAll(conversations)

    override fun save(conversation: Conversation, snapshot: List<Conversation>) = writeAll(snapshot)

    override fun delete(id: String, snapshot: List<Conversation>) = writeAll(snapshot)

    override fun saveShellTranscript(conversation: Conversation, snapshot: List<Conversation>) = writeAll(snapshot)

    private fun writeAll(conversations: List<Conversation>) {
        appSettings.setConversationsJson(
            ConversationJson.encodeToString(ConversationsData(conversations = conversations)),
        )
    }
}
