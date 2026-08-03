package com.inspiredandroid.kai.data

import kotlinx.serialization.serializer

class EmailStore(private val appSettings: AppSettings) {

    private val json = SharedJson
    private val accounts = SettingsJsonList(
        read = appSettings::getEmailAccountsJson,
        write = appSettings::setEmailAccountsJson,
        itemSerializer = serializer<EmailAccount>(),
        label = "EmailStore",
    )
    private val pendingQueue = PendingQueue<EmailMessage, Pair<String, Long>>(
        readJson = appSettings::getEmailPendingJson,
        writeJson = appSettings::setEmailPendingJson,
        serializer = serializer<EmailMessage>(),
        label = "EmailStore.pending",
        keyOf = { it.accountId to it.uid },
    )

    fun getAccounts(): List<EmailAccount> = accounts.get()

    fun getAccount(id: String): EmailAccount? = getAccounts().find { it.id == id }

    suspend fun addAccount(account: EmailAccount): EmailAccount {
        accounts.update { current -> current.filterNot { it.id == account.id } + account }
        return account
    }

    suspend fun removeAccount(id: String): Boolean {
        var removed = false
        accounts.update { current ->
            removed = current.any { it.id == id }
            if (removed) current.filterNot { it.id == id } else current
        }
        if (removed) {
            appSettings.removeEmailPassword(id)
            removeSyncState(id)
        }
        return removed
    }

    // Password management (stored separately for security)
    fun getPassword(accountId: String): String = appSettings.getEmailPassword(accountId)

    suspend fun setPassword(accountId: String, password: String) {
        appSettings.setEmailPassword(accountId, password)
    }

    // Sync state — one settings key per account, so it can't be a single accessor instance.
    fun getSyncState(accountId: String): EmailSyncState = decodeJsonOr(
        raw = appSettings.getEmailSyncStateJson(accountId),
        serializer = serializer<EmailSyncState>(),
        label = "EmailStore.syncState",
    ) { EmailSyncState(accountId = accountId) }

    suspend fun updateSyncState(state: EmailSyncState) {
        appSettings.setEmailSyncStateJson(state.accountId, json.encodeToString(state))
    }

    private fun removeSyncState(accountId: String) {
        appSettings.setEmailSyncStateJson(accountId, "")
    }

    fun getAllSyncStates(): Map<String, EmailSyncState> = getAccounts().associate { it.id to getSyncState(it.id) }

    // Capped FIFO so a disabled or slow heartbeat can't let the buffer grow unbounded.
    fun getPending(): List<EmailMessage> = pendingQueue.get()

    suspend fun addPending(emails: List<EmailMessage>) = pendingQueue.add(emails)

    suspend fun removePending(emails: List<EmailMessage>) = pendingQueue.remove(emails)
}
