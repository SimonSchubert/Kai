package com.inspiredandroid.kai.splinterlands

import kotlinx.serialization.Serializable

@Serializable
data class SplinterlandsAccount(
    val id: String = "",
    val username: String,
    @Deprecated("Use SplinterlandsStore.getInstanceId() instead")
    val instanceId: String = "",
)

@Serializable
data class BattleLogEntry(
    val opponent: String,
    val won: Boolean,
    val mana: Int = 0,
    val rulesets: String = "",
    val timestampMs: Long = 0L,
    val account: String = "",
    val llmPicked: Boolean? = null,
    val modelName: String = "",
    val activity: List<String> = emptyList(),
    val battleId: String = "",
)

@Serializable
enum class BattlePhase {
    Idle,
    LoggingIn,
    CheckingEnergy,
    FindingMatch,
    WaitingForOpponent,
    FetchingCollection,
    PickingTeam,
    SubmittingTeam,
    WaitingForResult,
    Finished,
    Error,
}

enum class LlmServiceStatus {
    Querying,
    ValidResponse,
    InvalidResponse,
    Failed,
    Selected,
}

data class BattleStatus(
    val phase: BattlePhase = BattlePhase.Idle,
    val isRunning: Boolean = false,
    val isStopping: Boolean = false,
    val wins: Int = 0,
    val losses: Int = 0,
    val skips: Int = 0,
    val errors: Int = 0,
    val energy: Int = -1,
    val currentOpponent: String = "",
    val currentMana: Int = 0,
    val currentRulesets: String = "",
    val llmPickedTeam: Boolean? = null,
    val battleStartedAtMs: Long = 0L,
    val teamDeadlineMs: Long = 0L,
    val errorMessage: String = "",
    val serviceStatuses: Map<String, LlmServiceStatus> = emptyMap(),
    val winningServiceName: String = "",
)

// Card data structures matching the Splinterlands API

data class CardEntry(
    val uid: String,
    val detailId: Int,
    val color: String,
    val splinter: String,
    val mana: Int,
    val rarity: String,
    val attackType: String,
    val attackPower: Int,
    val speed: Int,
    val armor: Int,
    val health: Int,
    val abilities: List<String>,
    val isGladiator: Boolean,
    val name: String,
)

data class SummonerEntry(
    val uid: String,
    val detailId: Int,
    val color: String,
    val splinter: String,
    val mana: Int,
    val rarity: String,
    val attackType: String,
    val attackPower: Int,
    val speed: Int,
    val armor: Int,
    val health: Int,
    val buffs: SummonerBuffs,
    val name: String,
)

data class SummonerBuffs(
    val attack: Int = 0,
    val ranged: Int = 0,
    val magic: Int = 0,
    val armor: Int = 0,
    val health: Int = 0,
    val speed: Int = 0,
    val abilities: List<String> = emptyList(),
)

data class TeamSelection(
    val summonerUid: String,
    val monsterUids: List<String>,
    val allyColor: String?,
)

val SPLINTER_COLORS = mapOf(
    "Fire" to "Red",
    "Water" to "Blue",
    "Earth" to "Green",
    "Life" to "White",
    "Death" to "Black",
    "Dragon" to "Gold",
    "Neutral" to "Gray",
)

val COLOR_TO_SPLINTER = SPLINTER_COLORS.entries.associate { (k, v) -> v to k }

val RARITY_INT_TO_NAME = mapOf(1 to "Common", 2 to "Rare", 3 to "Epic", 4 to "Legendary")
