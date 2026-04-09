package com.inspiredandroid.kai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.isSplinterlandsSupported
import com.inspiredandroid.kai.splinterlands.BattleStatus
import com.inspiredandroid.kai.splinterlands.SplinterlandsAccount
import com.inspiredandroid.kai.splinterlands.SplinterlandsApi
import com.inspiredandroid.kai.splinterlands.SplinterlandsBattleRunner
import com.inspiredandroid.kai.splinterlands.SplinterlandsStore
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class SplinterlandsViewModel(
    private val dataRepository: DataRepository,
    private val splinterlandsStore: SplinterlandsStore,
    private val splinterlandsBattleRunner: SplinterlandsBattleRunner,
    private val splinterlandsApi: SplinterlandsApi,
    private val backgroundDispatcher: CoroutineContext = getBackgroundDispatcher(),
) : ViewModel() {

    private val splinterlandsEnergy = mutableMapOf<String, Int>()
    private val splinterlandsAvatarUrls = mutableMapOf<String, String>()
    private val battleStatusJobs = mutableMapOf<String, Job>()

    private fun buildFullState(): SplinterlandsUiState = SplinterlandsUiState(
        showSplinterlandsSection = isSplinterlandsSupported,
        isSplinterlandsEnabled = splinterlandsStore.isEnabled(),
        splinterlandsAccounts = buildSplinterlandsAccountStates().toImmutableList(),
        splinterlandsInstanceIds = splinterlandsStore.getInstanceIds().toImmutableList(),
        splinterlandsBattleLog = splinterlandsStore.getBattleLog().toImmutableList(),
        splinterlandsAvailableServices = dataRepository.getServiceEntries().toImmutableList(),
        onToggleSplinterlands = ::onToggleSplinterlands,
        onTestAndAddSplinterlandsAccount = ::onTestAndAddSplinterlandsAccount,
        onRemoveSplinterlandsAccount = ::onRemoveSplinterlandsAccount,
        onAddSplinterlandsService = ::onAddSplinterlandsService,
        onRemoveSplinterlandsService = ::onRemoveSplinterlandsService,
        onReorderSplinterlandsServices = ::onReorderSplinterlandsServices,
        onStartSplinterlandsBattle = ::onStartSplinterlandsBattle,
        onStopSplinterlandsBattle = ::onStopSplinterlandsBattle,
        onClearSplinterlandsBattleLog = ::onClearSplinterlandsBattleLog,
    )

    private val _state = MutableStateFlow(buildFullState())

    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    fun onScreenVisible() {
        _state.update {
            it.copy(splinterlandsAvailableServices = dataRepository.getServiceEntries().toImmutableList())
        }
        if (splinterlandsStore.isEnabled()) fetchSplinterlandsAccountInfo()
    }

    private fun buildSplinterlandsAccountStates(): List<SplinterlandsAccountUiState> {
        val statuses = splinterlandsBattleRunner.statuses.value
        return splinterlandsStore.getAccounts().map { account ->
            val bs = statuses[account.id] ?: BattleStatus()
            val energy = if (bs.energy >= 0) bs.energy else splinterlandsEnergy[account.id] ?: -1
            SplinterlandsAccountUiState(
                accountId = account.id,
                username = account.username,
                battleStatus = bs,
                energy = energy,
                avatarUrl = splinterlandsAvatarUrls[account.id] ?: "",
            )
        }
    }

    private fun fetchSplinterlandsAccountInfo() {
        val accounts = splinterlandsStore.getAccounts().filter { it.username.isNotBlank() }
        if (accounts.isEmpty()) return
        viewModelScope.launch(backgroundDispatcher) {
            accounts.map { account ->
                async { fetchAccountAvatarAndEnergy(account.id, account.username.lowercase()) }
            }.awaitAll()
            _state.update { it.copy(splinterlandsAccounts = buildSplinterlandsAccountStates().toImmutableList()) }
        }
    }

    private suspend fun fetchAccountAvatarAndEnergy(accountId: String, username: String) {
        try {
            val avatarId = splinterlandsApi.getAvatarId(username)
            if (avatarId > 0) {
                splinterlandsAvatarUrls[accountId] =
                    "https://d36mxiodymuqjm.cloudfront.net/website/icons/avatars/avatar_$avatarId.png"
            }
        } catch (_: Exception) { }
        try {
            val energy = splinterlandsApi.getEnergyPublic(username)
            splinterlandsEnergy[accountId] = energy
        } catch (_: Exception) { }
    }

    private fun onToggleSplinterlands(enabled: Boolean) {
        splinterlandsStore.setEnabled(enabled)
        if (!enabled) splinterlandsBattleRunner.stop()
        _state.update {
            it.copy(
                isSplinterlandsEnabled = enabled,
                splinterlandsAccounts = buildSplinterlandsAccountStates().toImmutableList(),
            )
        }
        if (enabled) fetchSplinterlandsAccountInfo()
    }

    private fun onTestAndAddSplinterlandsAccount(username: String, postingKey: String) {
        _state.update { it.copy(splinterlandsAddStatus = SplinterlandsAddStatus.Testing) }
        viewModelScope.launch(backgroundDispatcher) {
            try {
                val uname = username.trim().lowercase()
                splinterlandsApi.login(uname, postingKey)
                val id = splinterlandsStore.generateAccountId()
                @Suppress("DEPRECATION")
                splinterlandsStore.saveAccount(SplinterlandsAccount(id = id, username = uname))
                splinterlandsStore.setPostingKey(id, postingKey)
                fetchAccountAvatarAndEnergy(id, uname)
                _state.update {
                    it.copy(
                        splinterlandsAccounts = buildSplinterlandsAccountStates().toImmutableList(),
                        splinterlandsAddStatus = SplinterlandsAddStatus.Idle,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(splinterlandsAddStatus = SplinterlandsAddStatus.Error(e.message ?: "Login failed"))
                }
            }
        }
    }

    private fun onRemoveSplinterlandsAccount(accountId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            splinterlandsBattleRunner.stop(accountId)
            splinterlandsStore.removeAccount(accountId)
            _state.update { it.copy(splinterlandsAccounts = buildSplinterlandsAccountStates().toImmutableList()) }
        }
    }

    private fun onAddSplinterlandsService(instanceId: String) {
        val current = splinterlandsStore.getInstanceIds().toMutableList()
        if (instanceId !in current) {
            current.add(instanceId)
            splinterlandsStore.setInstanceIds(current)
        }
        _state.update { it.copy(splinterlandsInstanceIds = current.toImmutableList()) }
    }

    private fun onRemoveSplinterlandsService(instanceId: String) {
        val current = splinterlandsStore.getInstanceIds().filter { it != instanceId }
        splinterlandsStore.setInstanceIds(current)
        _state.update { it.copy(splinterlandsInstanceIds = current.toImmutableList()) }
    }

    private fun onReorderSplinterlandsServices(orderedIds: List<String>) {
        splinterlandsStore.setInstanceIds(orderedIds)
        _state.update { it.copy(splinterlandsInstanceIds = orderedIds.toImmutableList()) }
    }

    private fun onStartSplinterlandsBattle(accountId: String) {
        splinterlandsBattleRunner.start(accountId)
        battleStatusJobs[accountId]?.cancel()
        battleStatusJobs[accountId] = viewModelScope.launch {
            var lastBattleCount = _state.value.splinterlandsBattleLog.size
            splinterlandsBattleRunner.statuses.collect {
                val accounts = buildSplinterlandsAccountStates()
                val totalBattles = accounts.sumOf { a -> a.battleStatus.wins + a.battleStatus.losses }
                val battleLogChanged = totalBattles != lastBattleCount
                if (battleLogChanged) lastBattleCount = totalBattles
                _state.update { s ->
                    s.copy(
                        splinterlandsAccounts = accounts.toImmutableList(),
                        splinterlandsBattleLog = if (battleLogChanged) splinterlandsStore.getBattleLog().toImmutableList() else s.splinterlandsBattleLog,
                    )
                }
            }
        }
    }

    private fun onStopSplinterlandsBattle(accountId: String) {
        battleStatusJobs[accountId]?.cancel()
        battleStatusJobs.remove(accountId)
        splinterlandsBattleRunner.stop(accountId)
        _state.update { it.copy(splinterlandsAccounts = buildSplinterlandsAccountStates().toImmutableList()) }
    }

    private fun onClearSplinterlandsBattleLog() {
        viewModelScope.launch(backgroundDispatcher) {
            splinterlandsStore.clearBattleLog()
            _state.update { it.copy(splinterlandsBattleLog = persistentListOf()) }
        }
    }
}
