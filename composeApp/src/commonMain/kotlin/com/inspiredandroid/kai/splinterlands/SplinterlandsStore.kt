package com.inspiredandroid.kai.splinterlands

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.SettingsJsonList
import com.inspiredandroid.kai.data.SharedJson
import com.inspiredandroid.kai.data.getInstanceModelId
import kotlinx.serialization.serializer

class SplinterlandsStore(private val appSettings: AppSettings) {

    private val json = SharedJson

    private val instanceIds = SettingsJsonList(
        read = appSettings::getSplinterlandsInstanceIdsJson,
        write = appSettings::setSplinterlandsInstanceIdsJson,
        itemSerializer = serializer<String>(),
        label = "SplinterlandsStore.instanceIds",
    )

    private val accounts = SettingsJsonList(
        read = appSettings::getSplinterlandsAccountJson,
        write = appSettings::setSplinterlandsAccountJson,
        itemSerializer = serializer<SplinterlandsAccount>(),
        label = "SplinterlandsStore.accounts",
        // Pre-multi-account installs persisted a single account object rather than a list.
        recover = { raw ->
            runCatching {
                val single = json.decodeFromString<SplinterlandsAccount>(raw)
                listOf(if (single.id.isEmpty()) single.copy(id = generateAccountId()) else single)
            }.getOrNull()
        },
    )

    private val battleLog = SettingsJsonList(
        read = appSettings::getSplinterlandsBattleLogJson,
        write = appSettings::setSplinterlandsBattleLogJson,
        itemSerializer = serializer<BattleLogEntry>(),
        label = "SplinterlandsStore.battleLog",
    )

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

    // ── Multi-service LLM instances (priority order) ──

    fun getInstanceIds(): List<String> {
        val stored = instanceIds.get()
        if (stored.isNotEmpty()) return stored
        // Migrate from single instance if set
        val single = getInstanceId()
        return if (single.isNotBlank()) listOf(single) else emptyList()
    }

    fun setInstanceIds(ids: List<String>) {
        instanceIds.set(ids)
        // Keep legacy single field in sync for backwards compat
        appSettings.setSplinterlandsInstanceId(ids.firstOrNull() ?: "")
    }

    // ── Multi-account support ──

    fun getAccounts(): List<SplinterlandsAccount> = accounts.get()

    fun getAccountById(id: String): SplinterlandsAccount? = getAccounts().find { it.id == id }

    suspend fun saveAccount(account: SplinterlandsAccount) {
        accounts.update { current ->
            if (current.any { it.id == account.id }) {
                current.map { if (it.id == account.id) account else it }
            } else {
                current + account
            }
        }
    }

    suspend fun removeAccount(accountId: String) {
        accounts.update { current -> current.filterNot { it.id == accountId } }
        // Clear per-account posting key
        appSettings.setSplinterlandsPostingKey(accountId, "")
    }

    fun getPostingKey(accountId: String): String = appSettings.getSplinterlandsPostingKey(accountId)

    suspend fun setPostingKey(accountId: String, key: String) {
        appSettings.setSplinterlandsPostingKey(accountId, key)
    }

    fun getBattleLog(): List<BattleLogEntry> = battleLog.get()

    suspend fun addBattleLogEntry(entry: BattleLogEntry) {
        battleLog.update { (listOf(entry) + it).take(MAX_BATTLE_LOG_ENTRIES) }
    }

    suspend fun clearBattleLog() {
        battleLog.update { emptyList() }
    }

    internal fun generateAccountId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return buildString { repeat(8) { append(chars.random()) } }
    }

    companion object {
        private const val MAX_BATTLE_LOG_ENTRIES = 500
    }
}
