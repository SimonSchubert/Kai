package com.inspiredandroid.kai.splinterlands

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.SharedJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SplinterlandsStore(private val appSettings: AppSettings) {

    private val json = SharedJson
    private val mutex = Mutex()

    fun isEnabled(): Boolean = appSettings.isSplinterlandsEnabled()

    fun setEnabled(enabled: Boolean) {
        appSettings.setSplinterlandsEnabled(enabled)
    }

    // ── Global LLM instance for all accounts ──

    fun getInstanceId(): String = appSettings.getSplinterlandsInstanceId()

    fun getModelName(): String {
        val instanceId = getInstanceId()
        if (instanceId.isBlank()) return ""
        return appSettings.getInstanceModelId(instanceId)
    }

    fun getModelName(instanceId: String): String {
        if (instanceId.isBlank()) return ""
        return appSettings.getInstanceModelId(instanceId)
    }

    fun setInstanceId(instanceId: String) {
        appSettings.setSplinterlandsInstanceId(instanceId)
    }

    // ── Multi-service LLM instances (priority order) ──

    fun getInstanceIds(): List<String> {
        val raw = appSettings.getSplinterlandsInstanceIdsJson()
        if (raw.isEmpty()) {
            // Migrate from single instance if set
            val single = getInstanceId()
            return if (single.isNotBlank()) listOf(single) else emptyList()
        }
        return try {
            json.decodeFromString<List<String>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setInstanceIds(ids: List<String>) {
        appSettings.setSplinterlandsInstanceIdsJson(json.encodeToString(ids))
        // Keep legacy single field in sync for backwards compat
        appSettings.setSplinterlandsInstanceId(ids.firstOrNull() ?: "")
    }

    // ── Multi-account support ──

    fun getAccounts(): List<SplinterlandsAccount> {
        val raw = appSettings.getSplinterlandsAccountJson()
        if (raw.isEmpty()) return emptyList()
        return try {
            // Try parsing as list first (new format)
            json.decodeFromString<List<SplinterlandsAccount>>(raw)
        } catch (_: Exception) {
            // Fallback: try parsing as single account (migration from old format)
            try {
                val single = json.decodeFromString<SplinterlandsAccount>(raw)
                val migrated = if (single.id.isEmpty()) single.copy(id = generateAccountId()) else single
                listOf(migrated)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    /** Legacy single-account getter for BattleRunner compatibility. */
    fun getAccount(): SplinterlandsAccount? = getAccounts().firstOrNull()

    fun getAccountById(id: String): SplinterlandsAccount? = getAccounts().find { it.id == id }

    suspend fun saveAccount(account: SplinterlandsAccount) = mutex.withLock {
        val accounts = getAccounts().toMutableList()
        val idx = accounts.indexOfFirst { it.id == account.id }
        if (idx >= 0) {
            accounts[idx] = account
        } else {
            accounts.add(account)
        }
        appSettings.setSplinterlandsAccountJson(json.encodeToString(accounts))
    }

    suspend fun removeAccount(accountId: String) = mutex.withLock {
        val accounts = getAccounts().filter { it.id != accountId }
        appSettings.setSplinterlandsAccountJson(json.encodeToString(accounts))
        // Clear per-account posting key
        appSettings.setSplinterlandsPostingKey(accountId, "")
    }

    fun getPostingKey(accountId: String): String = appSettings.getSplinterlandsPostingKey(accountId)

    /** Legacy getter — returns first account's posting key. */
    fun getPostingKey(): String {
        val firstId = getAccounts().firstOrNull()?.id ?: return appSettings.getSplinterlandsPostingKey()
        return getPostingKey(firstId)
    }

    suspend fun setPostingKey(accountId: String, key: String) {
        appSettings.setSplinterlandsPostingKey(accountId, key)
    }

    fun getBattleLog(): List<BattleLogEntry> {
        val raw = appSettings.getSplinterlandsBattleLogJson()
        if (raw.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<BattleLogEntry>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addBattleLogEntry(entry: BattleLogEntry) = mutex.withLock {
        val log = getBattleLog().toMutableList()
        log.add(0, entry)
        while (log.size > 500) log.removeAt(log.lastIndex)
        appSettings.setSplinterlandsBattleLogJson(json.encodeToString(log))
    }

    suspend fun clearBattleLog() = mutex.withLock {
        appSettings.setSplinterlandsBattleLogJson("")
    }

    internal fun generateAccountId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return buildString { repeat(8) { append(chars.random()) } }
    }
}
