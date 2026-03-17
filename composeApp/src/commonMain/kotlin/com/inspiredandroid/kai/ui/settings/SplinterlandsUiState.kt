package com.inspiredandroid.kai.ui.settings

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.data.ServiceEntry
import com.inspiredandroid.kai.splinterlands.BattleLogEntry
import com.inspiredandroid.kai.splinterlands.BattleStatus

@Immutable
data class SplinterlandsUiState(
    val showSplinterlandsSection: Boolean = false,
    val isSplinterlandsEnabled: Boolean = false,
    val splinterlandsAccounts: List<SplinterlandsAccountUiState> = emptyList(),
    val splinterlandsInstanceIds: List<String> = emptyList(),
    val splinterlandsBattleLog: List<BattleLogEntry> = emptyList(),
    val splinterlandsAvailableServices: List<ServiceEntry> = emptyList(),
    val splinterlandsAddStatus: SplinterlandsAddStatus = SplinterlandsAddStatus.Idle,
    val onToggleSplinterlands: (Boolean) -> Unit = {},
    val onTestAndAddSplinterlandsAccount: (String, String) -> Unit = { _, _ -> },
    val onRemoveSplinterlandsAccount: (String) -> Unit = {},
    val onAddSplinterlandsService: (String) -> Unit = {},
    val onRemoveSplinterlandsService: (String) -> Unit = {},
    val onReorderSplinterlandsServices: (List<String>) -> Unit = {},
    val onStartSplinterlandsBattle: (String) -> Unit = {},
    val onStopSplinterlandsBattle: (String) -> Unit = {},
    val onClearSplinterlandsBattleLog: () -> Unit = {},
)

@Immutable
data class SplinterlandsAccountUiState(
    val accountId: String,
    val username: String,
    val battleStatus: BattleStatus = BattleStatus(),
    val energy: Int = -1,
    val avatarUrl: String = "",
)

sealed interface SplinterlandsAddStatus {
    data object Idle : SplinterlandsAddStatus
    data object Testing : SplinterlandsAddStatus
    data class Error(val message: String) : SplinterlandsAddStatus
}
