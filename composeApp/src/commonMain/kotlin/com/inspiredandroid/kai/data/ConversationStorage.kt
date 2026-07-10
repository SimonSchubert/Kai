package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.TerminalLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

expect fun readLegacyConversationFile(): ByteArray?
expect fun deleteLegacyConversationFile()

private const val MAX_SHELL_TRANSCRIPT_CHARS = 10_000

class ConversationStorage(
    private val appSettings: AppSettings,
    private val persistence: ConversationPersistence,
) {
    private val mutableConversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = mutableConversations.asStateFlow()

    fun loadConversations() {
        val loaded = persistence.loadAll()
        mutableConversations.value = loaded
        if (loaded.isEmpty()) {
            migrateLegacy()
        }
    }

    fun saveConversation(conversation: Conversation) {
        var saved = conversation
        mutableConversations.update { current ->
            val index = current.indexOfFirst { it.id == conversation.id }
            if (index >= 0) {
                val existing = current[index]
                // Chat-layer save paths rebuild Conversation without the shell
                // transcript; preserve the stored tail rather than wiping it.
                val merged = if (conversation.shellTranscript.isEmpty() && existing.shellTranscript.isNotEmpty()) {
                    conversation.copy(shellTranscript = existing.shellTranscript)
                } else {
                    conversation
                }
                saved = merged
                current.toMutableList().apply { set(index, merged) }
            } else {
                current + conversation
            }
        }
        persistence.save(saved, mutableConversations.value)
    }

    /**
     * Persist a shell transcript snapshot for [conversationId]. No-op when the
     * conversation isn't in the saved set yet (chat hasn't been saved). The
     * stored transcript is trimmed from the head until the joined text is
     * within the per-conversation char budget — older lines drop first so the
     * tail (most recent activity) survives a restart.
     */
    fun updateShellTranscript(conversationId: String, lines: List<TerminalLine>) {
        val trimmed = trimToCharLimit(lines, MAX_SHELL_TRANSCRIPT_CHARS)
        var changed: Conversation? = null
        mutableConversations.update { current ->
            val idx = current.indexOfFirst { it.id == conversationId }
            if (idx < 0) {
                current
            } else if (current[idx].shellTranscript == trimmed) {
                current
            } else {
                val updated = current[idx].copy(shellTranscript = trimmed)
                changed = updated
                current.toMutableList().apply { set(idx, updated) }
            }
        }
        changed?.let { persistence.saveShellTranscript(it, mutableConversations.value) }
    }

    private fun trimToCharLimit(lines: List<TerminalLine>, limit: Int): List<TerminalLine> {
        if (lines.isEmpty()) return lines
        val totalChars = lines.sumOf { it.text.length }
        if (totalChars <= limit) return lines
        val tail = ArrayDeque<TerminalLine>()
        var running = 0
        for (i in lines.indices.reversed()) {
            val line = lines[i]
            if (running + line.text.length <= limit) {
                tail.addFirst(line)
                running += line.text.length
            } else {
                break
            }
        }
        if (tail.isNotEmpty()) return tail.toList()
        // Single most-recent line is itself larger than the budget — keep its
        // tail so the user still sees something on next launch.
        val last = lines.last()
        return listOf(last.withText(last.text.takeLast(limit)))
    }

    fun deleteConversation(id: String) {
        mutableConversations.update { current ->
            current.filter { it.id != id }
        }
        persistence.delete(id, mutableConversations.value)
    }

    private fun migrateLegacy() {
        val legacyData = readLegacyConversationFile() ?: return
        val key = appSettings.getEncryptionKey() ?: return
        val decrypted = ByteArray(legacyData.size)
        for (i in legacyData.indices) {
            decrypted[i] = (legacyData[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        val conversations = try {
            ConversationJson.decodeFromString<ConversationsData>(decrypted.decodeToString()).conversations
        } catch (_: Exception) {
            emptyList()
        }
        mutableConversations.value = conversations
        persistence.replaceAll(conversations)
        deleteLegacyConversationFile()
    }
}
