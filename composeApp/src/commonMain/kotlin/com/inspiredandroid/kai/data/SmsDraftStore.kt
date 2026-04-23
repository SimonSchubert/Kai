package com.inspiredandroid.kai.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SmsDraftStore(private val appSettings: AppSettings) {

    private val json = SharedJson
    private val mutex = Mutex()
    private val _drafts = MutableStateFlow(loadPersisted())
    val drafts: StateFlow<List<SmsDraft>> = _drafts.asStateFlow()

    private fun loadPersisted(): List<SmsDraft> {
        val raw = appSettings.getSmsDraftsJson()
        if (raw.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<SmsDraft>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(list: List<SmsDraft>) {
        _drafts.value = list
        appSettings.setSmsDraftsJson(json.encodeToString(list))
    }

    suspend fun addDraft(draft: SmsDraft) = mutex.withLock {
        // Cap at MAX_DRAFTS — oldest dropped, protecting against runaway AI.
        val merged = (_drafts.value + draft).takeLast(MAX_DRAFTS)
        persist(merged)
    }

    suspend fun removeDraft(id: String) = mutex.withLock {
        persist(_drafts.value.filterNot { it.id == id })
    }

    suspend fun updateStatus(id: String, status: SmsDraftStatus, error: String? = null) = mutex.withLock {
        val current = _drafts.value.find { it.id == id } ?: return@withLock
        if (current.status == status && current.lastError == error) return@withLock
        persist(
            _drafts.value.map { draft ->
                if (draft.id == id) draft.copy(status = status, lastError = error) else draft
            },
        )
    }

    fun getDraft(id: String): SmsDraft? = _drafts.value.find { it.id == id }

    fun getPending(): List<SmsDraft> = _drafts.value.filter { it.status == SmsDraftStatus.PENDING }

    companion object {
        private const val MAX_DRAFTS = 20
    }
}
