package com.inspiredandroid.kai.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.serializer

class SmsDraftStore(appSettings: AppSettings) {

    private val _drafts = MutableStateFlow<List<SmsDraft>>(emptyList())
    val drafts: StateFlow<List<SmsDraft>> = _drafts.asStateFlow()

    private val persisted = SettingsJsonList(
        read = appSettings::getSmsDraftsJson,
        write = appSettings::setSmsDraftsJson,
        itemSerializer = serializer<SmsDraft>(),
        label = "SmsDraftStore",
        // Every write mirrors into the flow the review banner observes, so reads below can stay
        // in memory.
        onWrite = { _drafts.value = it },
    )

    init {
        _drafts.value = persisted.get()
    }

    suspend fun addDraft(draft: SmsDraft) {
        // Cap at MAX_DRAFTS — oldest dropped, protecting against runaway AI.
        persisted.update { (it + draft).takeLast(MAX_DRAFTS) }
    }

    suspend fun removeDraft(id: String) {
        persisted.update { current -> current.filterNot { it.id == id } }
    }

    suspend fun updateStatus(id: String, status: SmsDraftStatus, error: String? = null) {
        persisted.update { current ->
            val existing = current.find { it.id == id } ?: return@update current
            if (existing.status == status && existing.lastError == error) return@update current
            current.map { draft -> if (draft.id == id) draft.copy(status = status, lastError = error) else draft }
        }
    }

    fun getDraft(id: String): SmsDraft? = _drafts.value.find { it.id == id }

    fun getPending(): List<SmsDraft> = _drafts.value.filter { it.status == SmsDraftStatus.PENDING }

    companion object {
        private const val MAX_DRAFTS = 20
    }
}
