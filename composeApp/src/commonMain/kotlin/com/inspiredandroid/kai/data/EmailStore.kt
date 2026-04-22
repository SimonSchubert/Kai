package com.inspiredandroid.kai.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EmailStore(private val appSettings: AppSettings) {

    private val json = SharedJson
    private val mutex = Mutex()

    fun getAccounts(): List<EmailAccount> {
        val raw = appSettings.getEmailAccountsJson()
        if (raw.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<EmailAccount>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getAccount(id: String): EmailAccount? = getAccounts().find { it.id == id }

    suspend fun addAccount(account: EmailAccount): EmailAccount = mutex.withLock {
        val accounts = getAccounts().toMutableList()
        accounts.removeAll { it.id == account.id }
        accounts.add(account)
        appSettings.setEmailAccountsJson(json.encodeToString(accounts))
        account
    }

    suspend fun removeAccount(id: String): Boolean = mutex.withLock {
        val accounts = getAccounts().toMutableList()
        val removed = accounts.removeAll { it.id == id }
        if (removed) {
            appSettings.setEmailAccountsJson(json.encodeToString(accounts))
            appSettings.removeEmailPassword(id)
            removeSyncState(id)
        }
        removed
    }

    // Password management (stored separately for security)
    fun getPassword(accountId: String): String = appSettings.getEmailPassword(accountId)

    suspend fun setPassword(accountId: String, password: String) {
        appSettings.setEmailPassword(accountId, password)
    }

    // Sync state
    fun getSyncState(accountId: String): EmailSyncState {
        val raw = appSettings.getEmailSyncStateJson(accountId)
        if (raw.isEmpty()) return EmailSyncState(accountId = accountId)
        return try {
            json.decodeFromString<EmailSyncState>(raw)
        } catch (_: Exception) {
            EmailSyncState(accountId = accountId)
        }
    }

    suspend fun updateSyncState(state: EmailSyncState) = mutex.withLock {
        appSettings.setEmailSyncStateJson(state.accountId, json.encodeToString(state))
    }

    private fun removeSyncState(accountId: String) {
        appSettings.setEmailSyncStateJson(accountId, "")
    }

    fun getAllSyncStates(): Map<String, EmailSyncState> =
        getAccounts().associate { it.id to getSyncState(it.id) }

    // Capped FIFO so a disabled or slow heartbeat can't let the buffer grow unbounded.
    fun getPending(): List<EmailMessage> {
        val raw = appSettings.getEmailPendingJson()
        if (raw.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<EmailMessage>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addPending(emails: List<EmailMessage>) = mutex.withLock {
        if (emails.isEmpty()) return@withLock
        val merged = (getPending() + emails).takeLast(MAX_PENDING)
        appSettings.setEmailPendingJson(json.encodeToString(merged))
    }

    suspend fun removePending(emails: List<EmailMessage>) = mutex.withLock {
        if (emails.isEmpty()) return@withLock
        val keys = emails.map { it.accountId to it.uid }.toSet()
        val remaining = getPending().filterNot { (it.accountId to it.uid) in keys }
        appSettings.setEmailPendingJson(json.encodeToString(remaining))
    }

    companion object {
        private const val MAX_PENDING = 100
    }
}
