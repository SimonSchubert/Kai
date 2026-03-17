package com.inspiredandroid.kai.splinterlands

import com.inspiredandroid.kai.DaemonController
import com.inspiredandroid.kai.data.DataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val APP_VERSION = "splinterlands/0.7.176"
private const val SLEEP_BETWEEN_BATTLES_MS = 30_000L
private const val MAX_CONSECUTIVE_ERRORS = 5
private const val TEAM_DEADLINE_MS = 180_000L
private val CANCELABLE_PHASES = setOf(
    BattlePhase.WaitingForOpponent,
    BattlePhase.FetchingCollection,
    BattlePhase.PickingTeam,
)
private val MID_BATTLE_PHASES = setOf(
    BattlePhase.FetchingCollection,
    BattlePhase.PickingTeam,
    BattlePhase.SubmittingTeam,
    BattlePhase.WaitingForResult,
)

class SplinterlandsBattleRunner(
    private val store: SplinterlandsStore,
    private val api: SplinterlandsApi,
    private val dataRepository: DataRepository,
    private val daemonController: DaemonController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _statuses = MutableStateFlow<Map<String, BattleStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, BattleStatus>> = _statuses

    private val jobs = mutableMapOf<String, Job>()
    private val stopRequested = mutableSetOf<String>()
    private val battleActivities = mutableMapOf<String, MutableList<String>>()

    private fun activity(accountId: String, message: String) {
        battleActivities.getOrPut(accountId) { mutableListOf() }.add(message)
    }

    fun getStatus(accountId: String): BattleStatus = _statuses.value[accountId] ?: BattleStatus()

    fun start(accountId: String) {
        if (jobs[accountId]?.isActive == true) return
        daemonController.start()
        updateStatus(accountId) { BattleStatus(isRunning = true, phase = BattlePhase.LoggingIn) }
        jobs[accountId] = scope.launch {
            try {
                runBattleLoop(accountId)
            } catch (_: CancellationException) {
                // Normal stop
            } catch (e: Exception) {
                updateStatus(accountId) { it.copy(phase = BattlePhase.Error, errorMessage = e.message ?: "Unknown error", isRunning = false) }
            }
        }
    }

    /** Legacy start for first account. */
    fun start() {
        val firstId = store.getAccounts().firstOrNull()?.id ?: return
        start(firstId)
    }

    fun stop(accountId: String) {
        val phase = getStatus(accountId).phase
        if (phase in MID_BATTLE_PHASES) {
            stopGracefully(accountId)
        } else {
            jobs[accountId]?.cancel()
            jobs.remove(accountId)
            stopRequested.remove(accountId)
            updateStatus(accountId) { it.copy(isRunning = false, phase = BattlePhase.Idle) }
        }
    }

    fun stopGracefully(accountId: String) {
        stopRequested.add(accountId)
        updateStatus(accountId) { it.copy(isStopping = true) }
    }

    fun stop() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        stopRequested.clear()
        _statuses.value = emptyMap()
    }

    private fun updateStatus(accountId: String, transform: (BattleStatus) -> BattleStatus) {
        _statuses.update { map ->
            val current = map[accountId] ?: BattleStatus()
            map + (accountId to transform(current))
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun runBattleLoop(accountId: String) {
        val account = store.getAccountById(accountId) ?: throw RuntimeException("No Splinterlands account configured")
        val postingKey = store.getPostingKey(accountId)
        if (postingKey.isBlank()) throw RuntimeException("No posting key configured")
        val username = account.username.lowercase()

        // Login
        updatePhase(accountId, BattlePhase.LoggingIn)
        val jwt = api.login(username, postingKey)

        // Fetch card details
        val cardDetails = api.getCardDetails()

        var consecutiveErrors = 0
        var battlesPlayed = 0

        while (true) {
            try {
                battlesPlayed++
                val result = runOneBattle(accountId, username, postingKey, jwt, cardDetails)
                when (result.outcome) {
                    BattleOutcome.Win -> {
                        updateStatus(accountId) { it.copy(wins = it.wins + 1) }
                        logBattle(accountId, username, true, result.opponent, result.mana, result.rulesets, result.battleId)
                    }

                    BattleOutcome.Loss -> {
                        updateStatus(accountId) { it.copy(losses = it.losses + 1) }
                        logBattle(accountId, username, false, result.opponent, result.mana, result.rulesets, result.battleId)
                    }

                    BattleOutcome.Skip -> {
                        updateStatus(accountId) { it.copy(skips = it.skips + 1) }
                    }

                    BattleOutcome.NoEnergy -> {
                        updateStatus(accountId) { it.copy(phase = BattlePhase.Finished, isRunning = false) }
                        return
                    }
                }
                consecutiveErrors = 0

                // Check graceful stop after battle completes
                if (accountId in stopRequested) {
                    stopRequested.remove(accountId)
                    updateStatus(accountId) { it.copy(phase = BattlePhase.Finished, isRunning = false, isStopping = false) }
                    return
                }
            } catch (e: CancellationException) {
                val phase = getStatus(accountId).phase
                if (phase in CANCELABLE_PHASES) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        tryCancelMatch(username, postingKey, jwt)
                    }
                }
                throw e
            } catch (e: Exception) {
                consecutiveErrors++
                updateStatus(accountId) { it.copy(errors = it.errors + 1, errorMessage = e.message ?: "") }
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    updateStatus(accountId) { it.copy(phase = BattlePhase.Error, isRunning = false, errorMessage = "Too many consecutive errors") }
                    return
                }
                delay(10_000)
                continue
            }

            updatePhase(accountId, BattlePhase.Idle)
            delay(SLEEP_BETWEEN_BATTLES_MS)
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun runOneBattle(
        accountId: String,
        username: String,
        postingKey: String,
        jwt: String,
        cardDetails: JsonArray,
    ): BattleResult {
        // Reset battle state
        battleActivities[accountId] = mutableListOf()
        updateStatus(accountId) {
            it.copy(
                battleStartedAtMs = Clock.System.now().toEpochMilliseconds(),
                currentOpponent = "",
                currentMana = 0,
                currentRulesets = "",
                llmPickedTeam = null,
                teamDeadlineMs = 0L,
                serviceStatuses = emptyMap(),
                winningServiceName = "",
            )
        }

        // Check energy
        updatePhase(accountId, BattlePhase.CheckingEnergy)
        val energy = api.getEnergyPublic(username)
        activity(accountId, "Energy: $energy")
        updateStatus(accountId) { it.copy(energy = energy) }
        if (energy <= 0) return BattleResult(BattleOutcome.NoEnergy)

        // Check outstanding match
        updatePhase(accountId, BattlePhase.FindingMatch)
        var existing = api.getOutstandingMatch(username, jwt)

        // Ignore non-ranked matches
        if (existing != null && existing["mana_cap"]?.jsonPrimitive?.contentOrNull == null) existing = null

        // If team already submitted, wait for result
        if (existing != null && existing["opponent"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true &&
            existing["team_hash"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
        ) {
            val mana = existing["mana_cap"]?.jsonPrimitive?.intOrNull ?: 0
            val rules = existing["ruleset"]?.jsonPrimitive?.contentOrNull ?: ""
            val trxId = existing["id"]?.jsonPrimitive?.contentOrNull ?: existing["trx_id"]?.jsonPrimitive?.contentOrNull ?: ""
            updateStatus(accountId) { it.copy(currentMana = mana, currentRulesets = rules) }
            updatePhase(accountId, BattlePhase.WaitingForResult)
            val battle = api.getBattleResult(trxId, jwt, timeoutMs = 120_000)
            val winner = battle["winner"]?.jsonPrimitive?.contentOrNull
            val opponentName = extractOpponentName(battle, username)
            updateStatus(accountId) { it.copy(currentOpponent = opponentName) }
            val outcome = if (winner == username) BattleOutcome.Win else BattleOutcome.Loss
            return BattleResult(outcome, opponentName, mana, rules, trxId)
        }

        val matchInfo: JsonObject
        if (existing != null && existing["opponent"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true) {
            matchInfo = existing
        } else if (existing != null) {
            updatePhase(accountId, BattlePhase.WaitingForOpponent)
            matchInfo = api.pollForMatch(username, jwt, timeoutMs = 180_000)
        } else {
            updatePhase(accountId, BattlePhase.FindingMatch)
            val nonce = generateSecret()
            val findData = """{"match_type":"Wild Ranked","app":"$APP_VERSION","n":"$nonce"}"""
            val signedTx = buildSignedCustomJson(username, postingKey, "sm_find_match", findData)
            val result = api.postBattleTx(signedTx, jwt)

            if (result["success"]?.jsonPrimitive?.content?.toBoolean() != true) {
                val error = result["error"]?.jsonPrimitive?.content ?: ""
                if ("energy" in error.lowercase()) return BattleResult(BattleOutcome.NoEnergy)
                return BattleResult(BattleOutcome.Skip)
            }

            updatePhase(accountId, BattlePhase.WaitingForOpponent)
            matchInfo = api.pollForMatch(username, jwt, timeoutMs = 180_000)
        }

        val opponent = matchInfo["opponent_player"]?.jsonPrimitive?.contentOrNull
            ?: matchInfo["opponent"]?.jsonPrimitive?.contentOrNull ?: ""
        val matchMana = matchInfo["mana_cap"]?.jsonPrimitive?.intOrNull ?: 0
        val matchRulesets = matchInfo["ruleset"]?.jsonPrimitive?.contentOrNull ?: ""
        val expirationStr = matchInfo["submit_expiration_date"]?.jsonPrimitive?.contentOrNull
        val teamDeadlineMs = if (expirationStr != null) {
            try {
                kotlinx.datetime.Instant.parse(expirationStr).toEpochMilliseconds() - 10_000
            } catch (_: Exception) {
                Clock.System.now().toEpochMilliseconds() + TEAM_DEADLINE_MS
            }
        } else {
            Clock.System.now().toEpochMilliseconds() + TEAM_DEADLINE_MS
        }
        val deadlineSec = (teamDeadlineMs - Clock.System.now().toEpochMilliseconds()) / 1000
        activity(accountId, "Matched vs $opponent ($matchMana mana, $matchRulesets)")
        activity(accountId, "Team deadline: ${deadlineSec}s${if (expirationStr != null) " (from server)" else " (fallback)"}")
        updateStatus(accountId) {
            it.copy(
                currentOpponent = opponent,
                currentMana = matchMana,
                currentRulesets = matchRulesets,
                teamDeadlineMs = teamDeadlineMs,
            )
        }
        val trxId = matchInfo["id"]?.jsonPrimitive?.contentOrNull ?: matchInfo["trx_id"]?.jsonPrimitive?.contentOrNull ?: ""

        // Fetch collection
        updatePhase(accountId, BattlePhase.FetchingCollection)
        val cards = api.getCollection(username, jwt)
        activity(accountId, "Collection: ${cards.size} cards")

        // Pick team
        updatePhase(accountId, BattlePhase.PickingTeam)
        val team = pickTeamWithLlm(accountId, cards, matchInfo, cardDetails, teamDeadlineMs)
        if (team == null) {
            activity(accountId, "No valid team found")
            return BattleResult(BattleOutcome.Skip, opponent, matchMana, matchRulesets)
        }
        activity(accountId, "Team picked (${team.monsterUids.size} monsters)")

        val secret = generateSecret()
        val teamHash = generateTeamHash(team.summonerUid, team.monsterUids, secret)

        // Submit team
        updatePhase(accountId, BattlePhase.SubmittingTeam)
        val nonce = generateSecret()
        val monstersJson = team.monsterUids.joinToString(",") { "\"$it\"" }
        val allyColorJson = if (team.allyColor != null) "\"${team.allyColor}\"" else "null"
        val submitData = """{"trx_id":"$trxId","team_hash":"$teamHash","summoner":"${team.summonerUid}","monsters":[$monstersJson],"secret":"$secret","match_type":"Ranked Wild","allyColor":$allyColorJson,"app":"$APP_VERSION","n":"$nonce"}"""
        val signedTx = buildSignedCustomJson(username, postingKey, "sm_submit_team", submitData)
        val submitResult = api.postBattleTx(signedTx, jwt)

        if (submitResult["success"]?.jsonPrimitive?.content?.toBoolean() != true) {
            val error = submitResult["error"]?.jsonPrimitive?.content ?: ""
            if ("already been submitted" !in error) {
                return BattleResult(BattleOutcome.Loss, opponent, matchMana, matchRulesets)
            }
        }
        activity(accountId, "Team submitted")

        // Wait for result
        updatePhase(accountId, BattlePhase.WaitingForResult)
        val battle = api.getBattleResult(trxId, jwt, timeoutMs = 120_000)
        val winner = battle["winner"]?.jsonPrimitive?.contentOrNull
        val opponentName = extractOpponentName(battle, username).ifBlank { opponent }
        updateStatus(accountId) { it.copy(currentOpponent = opponentName) }
        val outcome = if (winner == username) BattleOutcome.Win else BattleOutcome.Loss
        activity(accountId, "Result: ${if (outcome == BattleOutcome.Win) "Victory" else "Defeat"} vs $opponentName")
        return BattleResult(outcome, opponentName, matchMana, matchRulesets, trxId)
    }

    private suspend fun pickTeamWithLlm(
        accountId: String,
        cards: JsonArray,
        matchInfo: JsonObject,
        cardDetails: JsonArray,
        teamDeadlineMs: Long = Clock.System.now().toEpochMilliseconds() + TEAM_DEADLINE_MS,
    ): TeamSelection? {
        // Build summoner/monster lists for LLM
        val manaCap = matchInfo["mana_cap"]?.jsonPrimitive?.intOrNull ?: 20
        val inactiveStr = matchInfo["inactive"]?.jsonPrimitive?.contentOrNull ?: ""
        val inactiveColors = buildInactiveColors(inactiveStr)
        val rulesets = parseRulesets(matchInfo["ruleset"]?.jsonPrimitive?.contentOrNull ?: "")
        val maxMonsters = getMaxMonsters(rulesets)

        val detailById = mutableMapOf<Int, JsonObject>()
        for (cd in cardDetails) {
            val obj = cd.jsonObject
            val id = obj["id"]?.jsonPrimitive?.int ?: continue
            detailById[id] = obj
        }

        val summoners = mutableListOf<SummonerEntry>()
        val monsters = mutableListOf<CardEntry>()
        for (cardEl in cards) {
            val card = cardEl.jsonObject
            val detailId = card["card_detail_id"]?.jsonPrimitive?.int ?: continue
            val detail = detailById[detailId] ?: continue
            val cardType = detail["type"]?.jsonPrimitive?.content ?: continue
            val color = detail["color"]?.jsonPrimitive?.content ?: continue
            val splinter = COLOR_TO_SPLINTER[color] ?: color
            if (color in inactiveColors || splinter in inactiveColors) continue
            when (cardType) {
                "Summoner" -> summoners.add(buildSummonerEntry(card, detail))
                "Monster" -> monsters.add(buildCardEntry(card, detail))
            }
        }

        val filteredSummoners = applyRulesetFilters(
            summoners, rulesets, true,
            { it.color }, { it.rarity }, { it.attackType }, { it.attackPower },
            { it.mana }, { it.speed }, { it.armor }, { it.health },
        )
        if (filteredSummoners.isEmpty()) return null

        var filteredMonsters = applyRulesetFilters(
            monsters, rulesets, false,
            { it.color }, { it.rarity }, { it.attackType }, { it.attackPower },
            { it.mana }, { it.speed }, { it.armor }, { it.health },
        )

        val hasConscript = filteredSummoners.any { "Conscript" in it.buffs.abilities }
        if (!hasConscript) {
            filteredMonsters = filteredMonsters.filter { !it.isGladiator }
        }

        val dedupSummoners = filteredSummoners.distinctBy { it.detailId }
        val dedupMonsters = filteredMonsters.distinctBy { it.detailId }

        // Query all configured services in parallel
        val instanceIds = store.getInstanceIds()
        if (instanceIds.isNotEmpty()) {
            activity(accountId, "LLM: ${dedupSummoners.size} summoners, ${dedupMonsters.size} monsters")
            activity(accountId, "LLM: querying ${instanceIds.size} services")

            val prompt = buildLlmPrompt(dedupSummoners, dedupMonsters, matchInfo, maxMonsters)
            val fullPrompt = prompt.systemPrompt + "\n\n" + prompt.userMessage

            // Set all services to Querying
            updateStatus(accountId) { status ->
                status.copy(serviceStatuses = instanceIds.associateWith { LlmServiceStatus.Querying })
            }

            try {
                val result = queryServicesInParallel(
                    accountId, instanceIds, fullPrompt, prompt, dedupSummoners, dedupMonsters,
                    manaCap, maxMonsters, rulesets, teamDeadlineMs,
                )
                if (result != null) return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                activity(accountId, "LLM error: ${e.message}")
            }
        }

        // Fallback to simple picker
        activity(accountId, "Fallback to auto picker")
        updateStatus(accountId) { it.copy(llmPickedTeam = false) }
        return pickTeam(cards, matchInfo, cardDetails)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun queryServicesInParallel(
        accountId: String,
        instanceIds: List<String>,
        fullPrompt: String,
        prompt: LlmPromptResult,
        dedupSummoners: List<SummonerEntry>,
        dedupMonsters: List<CardEntry>,
        manaCap: Int,
        maxMonsters: Int,
        rulesets: Set<String>,
        teamDeadlineMs: Long,
    ): TeamSelection? = coroutineScope {
        // Channel receives results in completion order (not priority order)
        val resultChannel = Channel<ServiceResult>(instanceIds.size)

        instanceIds.forEachIndexed { index, instanceId ->
            launch {
                val modelName = store.getModelName(instanceId)
                val result = try {
                    val llmTimeout = teamDeadlineMs - Clock.System.now().toEpochMilliseconds() - 5_000
                    if (llmTimeout < 10_000) {
                        ServiceResult(index, instanceId, null, modelName, null, listOf("deadline"))
                    } else {
                        activity(accountId, "LLM $instanceId: querying (timeout ${llmTimeout / 1000}s)")
                        val response = dataRepository.askSilentlyWithInstance(instanceId, fullPrompt, timeoutMs = llmTimeout.coerceAtLeast(10_000))
                        processServiceResponse(instanceId, index, modelName, accountId, response, prompt, dedupSummoners, dedupMonsters, manaCap, maxMonsters, rulesets)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    activity(accountId, "LLM $instanceId: error: ${e.message}")
                    ServiceResult(index, instanceId, null, modelName, null, listOf(e.message ?: "error"))
                }
                resultChannel.send(result)
            }
        }

        // Collect results in completion order — return as soon as best possible result is known
        var bestResult: ServiceResult? = null
        val serviceStatusUpdates = instanceIds.associateWith { LlmServiceStatus.Querying }.toMutableMap()
        val completedPriorities = mutableSetOf<Int>()

        repeat(instanceIds.size) {
            val result = resultChannel.receive()
            val status = if (result.team != null) {
                LlmServiceStatus.ValidResponse
            } else if (result.pick != null) {
                LlmServiceStatus.InvalidResponse
            } else {
                LlmServiceStatus.Failed
            }
            serviceStatusUpdates[result.instanceId] = status
            completedPriorities.add(result.priority)
            updateStatus(accountId) { it.copy(serviceStatuses = serviceStatusUpdates.toMap()) }

            if (result.team != null) {
                if (bestResult == null || result.priority < bestResult.priority) {
                    bestResult = result
                }
            }

            // If we have a valid result and all higher-priority services have finished, use it now.
            // Returning from coroutineScope cancels remaining child coroutines.
            val best = bestResult
            if (best != null && (0 until best.priority).all { it in completedPriorities }) {
                serviceStatusUpdates[best.instanceId] = LlmServiceStatus.Selected
                updateStatus(accountId) {
                    it.copy(
                        llmPickedTeam = true,
                        serviceStatuses = serviceStatusUpdates.toMap(),
                        winningServiceName = best.modelName.ifBlank { best.instanceId },
                    )
                }
                activity(accountId, "LLM: selected ${best.modelName.ifBlank { best.instanceId }}")
                return@coroutineScope best.team
            }
        }

        null
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun logBattle(accountId: String, account: String, won: Boolean, opponent: String, mana: Int, rulesets: String, battleId: String = "") {
        val status = getStatus(accountId)
        val llmPicked = status.llmPickedTeam
        store.addBattleLogEntry(
            BattleLogEntry(
                opponent = opponent,
                won = won,
                mana = mana,
                rulesets = rulesets,
                timestampMs = Clock.System.now().toEpochMilliseconds(),
                account = account,
                llmPicked = llmPicked,
                modelName = if (llmPicked == true) status.winningServiceName.ifBlank { store.getModelName() } else "",
                activity = battleActivities[accountId]?.toList() ?: emptyList(),
                battleId = battleId,
            ),
        )
    }

    private suspend fun tryCancelMatch(username: String, postingKey: String, jwt: String) {
        try {
            val nonce = generateSecret()
            val cancelData = """{"match_type":"Ranked","app":"$APP_VERSION","n":"$nonce"}"""
            val signedTx = buildSignedCustomJson(username, postingKey, "sm_cancel_match", cancelData)
            api.postBattleTx(signedTx, jwt)
        } catch (_: Exception) {
        }
    }

    private fun extractOpponentName(battle: JsonObject, username: String): String {
        val player1 = battle["player_1"]?.jsonPrimitive?.contentOrNull
        val player2 = battle["player_2"]?.jsonPrimitive?.contentOrNull
        if (player1 != null && player2 != null) {
            return if (player1.equals(username, ignoreCase = true)) player2 else player1
        }
        val winner = battle["winner"]?.jsonPrimitive?.contentOrNull
        return if (winner != null && !winner.equals(username, ignoreCase = true)) winner else ""
    }

    private fun updatePhase(accountId: String, phase: BattlePhase) {
        updateStatus(accountId) { it.copy(phase = phase) }
    }

    private data class ServiceResult(
        val priority: Int,
        val instanceId: String,
        val team: TeamSelection?,
        val modelName: String,
        val pick: LlmPick?,
        val issues: List<String>,
    )

    private fun processServiceResponse(
        instanceId: String,
        index: Int,
        modelName: String,
        accountId: String,
        response: String,
        prompt: LlmPromptResult,
        dedupSummoners: List<SummonerEntry>,
        dedupMonsters: List<CardEntry>,
        manaCap: Int,
        maxMonsters: Int,
        rulesets: Set<String>,
    ): ServiceResult {
        if (response.isBlank()) {
            activity(accountId, "LLM $instanceId: empty response")
            return ServiceResult(index, instanceId, null, modelName, null, listOf("empty response"))
        }
        activity(accountId, "LLM $instanceId: response (${response.length} chars)")

        val pick = parseLlmPick(response, prompt.idMap)
        if (pick == null) {
            activity(accountId, "LLM $instanceId: parse failed")
            return ServiceResult(index, instanceId, null, modelName, null, listOf("parse failed"))
        }

        val issues = validateTeam(pick.summonerUid, pick.monsterUids, dedupSummoners, dedupMonsters, manaCap, maxMonsters, rulesets)
        if (issues.isEmpty()) {
            activity(accountId, "LLM $instanceId: valid team")
            val summonerEntry = dedupSummoners.find { it.uid == pick.summonerUid }
            val allyColor = determineDragonAllyColor(summonerEntry?.color, pick.monsterUids, dedupMonsters.associateBy { it.uid })
            return ServiceResult(index, instanceId, TeamSelection(pick.summonerUid, pick.monsterUids, allyColor), modelName, pick, emptyList())
        }

        // Try silent fixes
        activity(accountId, "LLM $instanceId: invalid - ${issues.joinToString("; ")}")
        val fixed = applyFixes(pick.summonerUid, pick.monsterUids, dedupSummoners, dedupMonsters, manaCap, maxMonsters, rulesets)
        if (fixed != null) {
            activity(accountId, "LLM $instanceId: fixed")
            return ServiceResult(index, instanceId, fixed, modelName, pick, emptyList())
        }
        return ServiceResult(index, instanceId, null, modelName, pick, issues)
    }

    private enum class BattleOutcome {
        Win,
        Loss,
        Skip,
        NoEnergy,
    }

    private data class BattleResult(
        val outcome: BattleOutcome,
        val opponent: String = "",
        val mana: Int = 0,
        val rulesets: String = "",
        val battleId: String = "",
    )
}
