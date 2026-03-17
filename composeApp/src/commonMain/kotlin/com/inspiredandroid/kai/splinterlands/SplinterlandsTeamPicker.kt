package com.inspiredandroid.kai.splinterlands

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ── Stat extraction ──

private fun atLevel(element: kotlinx.serialization.json.JsonElement?, level: Int): Int {
    if (element == null) return 0
    return when {
        element is JsonArray -> {
            val arr = element.jsonArray
            if (arr.isEmpty()) {
                0
            } else {
                arr[minOf(level - 1, arr.size - 1).coerceAtLeast(0)].jsonPrimitive.int
            }
        }

        else -> try {
            element.jsonPrimitive.int
        } catch (_: Exception) {
            0
        }
    }
}

private fun getAttackType(stats: JsonObject, level: Int): String = when {
    atLevel(stats["attack"], level) > 0 -> "melee"
    atLevel(stats["ranged"], level) > 0 -> "ranged"
    atLevel(stats["magic"], level) > 0 -> "magic"
    else -> "none"
}

private fun getAttackPower(stats: JsonObject, level: Int): Int = maxOf(
    atLevel(stats["attack"], level),
    atLevel(stats["ranged"], level),
    atLevel(stats["magic"], level),
)

private fun getAbilities(stats: JsonObject, level: Int): List<String> {
    val abilitiesList = stats["abilities"] ?: return emptyList()
    if (abilitiesList !is JsonArray) return emptyList()
    val result = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (i in 0 until minOf(level, abilitiesList.jsonArray.size)) {
        val tier = abilitiesList.jsonArray[i]
        if (tier is JsonArray) {
            for (a in tier.jsonArray) {
                val name = a.jsonPrimitive.content
                if (name.isNotBlank() && seen.add(name)) result.add(name)
            }
        }
    }
    return result
}

private fun safeInt(stats: JsonObject, key: String): Int = try {
    stats[key]?.jsonPrimitive?.int ?: 0
} catch (_: Exception) {
    0
}

private fun getSummonerBuffs(stats: JsonObject): SummonerBuffs {
    val abilities = mutableListOf<String>()
    val abList = stats["abilities"]
    if (abList is JsonArray) {
        for (a in abList.jsonArray) {
            val name = try {
                a.jsonPrimitive.content
            } catch (_: Exception) {
                null
            }
            if (name != null) abilities.add(name)
        }
    }
    return SummonerBuffs(
        attack = safeInt(stats, "attack"),
        ranged = safeInt(stats, "ranged"),
        magic = safeInt(stats, "magic"),
        armor = safeInt(stats, "armor"),
        health = safeInt(stats, "health"),
        speed = safeInt(stats, "speed"),
        abilities = abilities,
    )
}

// ── Card entry building ──

fun buildCardEntry(card: JsonObject, detail: JsonObject): CardEntry {
    val color = detail["color"]?.jsonPrimitive?.content ?: ""
    val rarityInt = detail["rarity"]?.jsonPrimitive?.int ?: 1
    val level = card["level"]?.jsonPrimitive?.int ?: 1
    val stats = detail["stats"]?.jsonObject ?: JsonObject(emptyMap())
    return CardEntry(
        uid = card["uid"]!!.jsonPrimitive.content,
        detailId = card["card_detail_id"]!!.jsonPrimitive.int,
        color = color,
        splinter = COLOR_TO_SPLINTER[color] ?: color,
        mana = atLevel(stats["mana"], level),
        rarity = RARITY_INT_TO_NAME[rarityInt] ?: "Common",
        attackType = getAttackType(stats, level),
        attackPower = getAttackPower(stats, level),
        speed = atLevel(stats["speed"], level),
        armor = atLevel(stats["armor"], level),
        health = atLevel(stats["health"], level),
        abilities = getAbilities(stats, level),
        isGladiator = card["edition"]?.jsonPrimitive?.int == 6,
        name = detail["name"]?.jsonPrimitive?.content ?: "?",
    )
}

fun buildSummonerEntry(card: JsonObject, detail: JsonObject): SummonerEntry {
    val color = detail["color"]?.jsonPrimitive?.content ?: ""
    val rarityInt = detail["rarity"]?.jsonPrimitive?.int ?: 1
    val level = card["level"]?.jsonPrimitive?.int ?: 1
    val stats = detail["stats"]?.jsonObject ?: JsonObject(emptyMap())
    return SummonerEntry(
        uid = card["uid"]!!.jsonPrimitive.content,
        detailId = card["card_detail_id"]!!.jsonPrimitive.int,
        color = color,
        splinter = COLOR_TO_SPLINTER[color] ?: color,
        mana = atLevel(stats["mana"], level),
        rarity = RARITY_INT_TO_NAME[rarityInt] ?: "Common",
        attackType = getAttackType(stats, level),
        attackPower = getAttackPower(stats, level),
        speed = atLevel(stats["speed"], level),
        armor = atLevel(stats["armor"], level),
        health = atLevel(stats["health"], level),
        buffs = getSummonerBuffs(stats),
        name = detail["name"]?.jsonPrimitive?.content ?: "?",
    )
}

// ── Ruleset parsing and filtering ──

fun parseRulesets(rulesetStr: String): Set<String> {
    if (rulesetStr.isBlank()) return emptySet()
    return rulesetStr.split("|").map { it.trim() }.filter { it.isNotBlank() }.toSet()
}

fun getMaxMonsters(rulesets: Set<String>): Int {
    var limit = 6
    if ("Four's a Crowd" in rulesets || "FabFour" in rulesets) limit = minOf(limit, 4)
    if ("High Five" in rulesets || "FiveAlive" in rulesets) limit = minOf(limit, 5)
    return limit
}

private val MONSTER_ONLY_RULESETS = setOf(
    "Keep Your Distance", "Broken Arrows", "Lost Magic",
    "Up Close & Personal", "Going the Distance", "Wands Out",
    "Might Makes Right", "Shades of Gray",
    "Need for Speed", "Heavy Metal", "Beefcakes",
)

fun <T> applyRulesetFilters(
    entries: List<T>,
    rulesets: Set<String>,
    isSummoner: Boolean,
    color: (T) -> String,
    rarity: (T) -> String,
    attackType: (T) -> String,
    attackPower: (T) -> Int,
    mana: (T) -> Int,
    speed: (T) -> Int,
    armor: (T) -> Int,
    health: (T) -> Int,
): List<T> {
    var filtered = entries
    for (ruleset in rulesets) {
        if (isSummoner && ruleset in MONSTER_ONLY_RULESETS) continue
        filtered = when (ruleset) {
            "Lost Legendaries" -> filtered.filter { rarity(it) != "Legendary" }
            "Rise of the Commons" -> filtered.filter { rarity(it) in listOf("Common", "Rare") }
            "Elite Force" -> filtered.filter { rarity(it) != "Common" }
            "Keep Your Distance" -> filtered.filter { attackType(it) != "melee" }
            "Broken Arrows" -> filtered.filter { attackType(it) != "ranged" }
            "Lost Magic" -> filtered.filter { attackType(it) != "magic" }
            "Up Close & Personal" -> filtered.filter { attackType(it) == "melee" }
            "Going the Distance" -> filtered.filter { attackType(it) == "ranged" }
            "Wands Out" -> filtered.filter { attackType(it) == "magic" }
            "Might Makes Right" -> filtered.filter { attackPower(it) >= 3 }
            "Even Stevens" -> filtered.filter { mana(it) % 2 == 0 }
            "Odd Ones Out" -> filtered.filter { mana(it) % 2 == 1 }
            "Little League" -> filtered.filter { mana(it) <= 4 }
            "Junior Varsity" -> filtered.filter { mana(it) <= 6 }
            "Taking Sides" -> filtered.filter { color(it) != "Gray" }
            "Shades of Gray" -> filtered.filter { color(it) == "Gray" }
            "Need for Speed" -> filtered.filter { speed(it) >= 3 }
            "Heavy Metal" -> filtered.filter { armor(it) > 0 }
            "Beefcakes" -> filtered.filter { health(it) >= 5 }
            else -> filtered
        }
    }
    return filtered
}

// ── Team picking ──

fun pickTeam(
    cards: JsonArray,
    matchInfo: JsonObject,
    cardDetails: JsonArray,
): TeamSelection? {
    val manaCap = matchInfo["mana_cap"]?.jsonPrimitive?.int ?: 20
    val inactiveStr = matchInfo["inactive"]?.jsonPrimitive?.content ?: ""
    val inactiveColors = buildInactiveColors(inactiveStr)

    val rulesets = parseRulesets(matchInfo["ruleset"]?.jsonPrimitive?.content ?: "")
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

    // Gladiator handling — keep gladiators only if some summoner has Conscript
    val hasConscript = filteredSummoners.any { "Conscript" in it.buffs.abilities }
    if (!hasConscript) {
        filteredMonsters = filteredMonsters.filter { !it.isGladiator }
    }

    // Deduplicate by detailId
    val dedupSummoners = filteredSummoners.distinctBy { it.detailId }
    val dedupMonsters = filteredMonsters.distinctBy { it.detailId }

    // Sort monsters by mana descending
    val sortedMonsters = dedupMonsters.sortedByDescending { it.mana }

    // Try each summoner until a valid team is found
    for (summoner in dedupSummoners) {
        val remaining = manaCap - summoner.mana
        if (remaining <= 0) continue
        val gladLimit = if ("Conscript" in summoner.buffs.abilities) 1 else 0

        if (summoner.color == "Gold") {
            // Dragon: try each ally color
            val allyCandidates = COLOR_TO_SPLINTER.keys.filter {
                it !in inactiveColors && COLOR_TO_SPLINTER[it] !in inactiveColors && it != "Gold" && it != "Gray"
            }
            var bestTeam = emptyList<CardEntry>()
            var bestMana = 0
            for (ally in allyCandidates) {
                val validColors = mutableSetOf(ally, "Gray")
                if ("Taking Sides" in rulesets) validColors.remove("Gray")
                if ("Shades of Gray" in rulesets) {
                    validColors.clear()
                    validColors.add("Gray")
                }
                val (team, manaUsed) = pickMonsters(sortedMonsters, remaining, validColors, maxMonsters, gladLimit)
                if (team.isNotEmpty() && manaUsed > bestMana) {
                    bestTeam = team
                    bestMana = manaUsed
                }
            }
            if (bestTeam.isNotEmpty()) {
                return withAllyColor(summoner, bestTeam)
            }
        } else {
            val validColors = mutableSetOf(summoner.color, "Gray")
            if ("Taking Sides" in rulesets) validColors.remove("Gray")
            if ("Shades of Gray" in rulesets) {
                validColors.clear()
                validColors.add("Gray")
            }
            val (team, _) = pickMonsters(sortedMonsters, remaining, validColors, maxMonsters, gladLimit)
            if (team.isNotEmpty()) {
                return TeamSelection(summoner.uid, team.map { it.uid }, null)
            }
        }
    }

    return null
}

private fun pickMonsters(
    sorted: List<CardEntry>,
    remainingMana: Int,
    validColors: Set<String>,
    maxMonsters: Int,
    gladLimit: Int = 0,
): Pair<List<CardEntry>, Int> {
    val team = mutableListOf<CardEntry>()
    var manaUsed = 0
    var gladCount = 0
    val usedIds = mutableSetOf<Int>()
    for (m in sorted) {
        if (m.detailId in usedIds) continue
        if (m.color !in validColors) continue
        if (m.mana > remainingMana - manaUsed) continue
        if (m.isGladiator && gladCount >= gladLimit) continue
        team.add(m)
        manaUsed += m.mana
        usedIds.add(m.detailId)
        if (m.isGladiator) gladCount++
        if (team.size >= maxMonsters) break
    }
    return team to manaUsed
}

private fun withAllyColor(summoner: SummonerEntry, team: List<CardEntry>): TeamSelection {
    val monsterLookup = team.associateBy { it.uid }
    val allyColor = determineDragonAllyColor(summoner.color, team.map { it.uid }, monsterLookup)
    return TeamSelection(summoner.uid, team.map { it.uid }, allyColor)
}

internal fun buildInactiveColors(inactiveStr: String): Set<String> {
    val raw = inactiveStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    val colors = mutableSetOf<String>()
    for (v in raw) {
        colors.add(v)
        SPLINTER_COLORS[v]?.let { colors.add(it) }
        COLOR_TO_SPLINTER[v]?.let { colors.add(it) }
    }
    return colors
}

internal fun determineDragonAllyColor(summonerColor: String?, monsterUids: List<String>, monsterLookup: Map<String, CardEntry>): String? {
    if (summonerColor != "Gold") return null
    val colorCounts = mutableMapOf<String, Int>()
    for (uid in monsterUids) {
        val c = monsterLookup[uid]?.color ?: continue
        if (c != "Gray") colorCounts[c] = (colorCounts[c] ?: 0) + 1
    }
    return colorCounts.maxByOrNull { it.value }?.key
}

// ── Team hash and secret ──

fun generateSecret(length: Int = 10): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return buildString {
        repeat(length) { append(chars.random()) }
    }
}

fun generateTeamHash(summoner: String, monsters: List<String>, secret: String): String {
    val payload = (listOf(summoner) + monsters + listOf(secret)).joinToString(",")
    return md5Hex(payload)
}

private fun md5Hex(input: String): String {
    // KMP MD5 implementation
    val bytes = input.encodeToByteArray()
    return md5(bytes).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

// Minimal MD5 implementation for KMP (no java.security dependency in common)
private fun md5(message: ByteArray): ByteArray {
    val s = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )
    val k = IntArray(64) { i ->
        val t = kotlin.math.abs(kotlin.math.sin((i + 1).toDouble()))
        (t * 4294967296.0).toLong().toInt()
    }

    val originalLen = message.size
    val numBlocks = ((originalLen + 8) / 64) + 1
    val totalLen = numBlocks * 64
    val paddedMessage = ByteArray(totalLen)
    message.copyInto(paddedMessage)
    paddedMessage[originalLen] = 0x80.toByte()
    val bitsLen = (originalLen.toLong() * 8)
    for (i in 0..7) {
        paddedMessage[totalLen - 8 + i] = ((bitsLen ushr (i * 8)) and 0xFF).toByte()
    }

    var a0 = 0x67452301
    var b0 = 0xEFCDAB89.toInt()
    var c0 = 0x98BADCFE.toInt()
    var d0 = 0x10325476

    for (block in 0 until numBlocks) {
        val m = IntArray(16) { i ->
            val offset = block * 64 + i * 4
            (paddedMessage[offset].toInt() and 0xFF) or
                ((paddedMessage[offset + 1].toInt() and 0xFF) shl 8) or
                ((paddedMessage[offset + 2].toInt() and 0xFF) shl 16) or
                ((paddedMessage[offset + 3].toInt() and 0xFF) shl 24)
        }

        var a = a0
        var b = b0
        var c = c0
        var d = d0

        for (i in 0..63) {
            val f: Int
            val g: Int
            when {
                i < 16 -> {
                    f = (b and c) or (b.inv() and d)
                    g = i
                }

                i < 32 -> {
                    f = (d and b) or (d.inv() and c)
                    g = (5 * i + 1) % 16
                }

                i < 48 -> {
                    f = b xor c xor d
                    g = (3 * i + 5) % 16
                }

                else -> {
                    f = c xor (b or d.inv())
                    g = (7 * i) % 16
                }
            }
            val temp = d
            d = c
            c = b
            val sum = a + f + k[i] + m[g]
            b += sum.rotateLeft(s[i])
            a = temp
        }
        a0 += a
        b0 += b
        c0 += c
        d0 += d
    }

    val digest = ByteArray(16)
    for (i in 0..3) {
        digest[i] = ((a0 ushr (i * 8)) and 0xFF).toByte()
        digest[i + 4] = ((b0 ushr (i * 8)) and 0xFF).toByte()
        digest[i + 8] = ((c0 ushr (i * 8)) and 0xFF).toByte()
        digest[i + 12] = ((d0 ushr (i * 8)) and 0xFF).toByte()
    }
    return digest
}

// ── LLM prompt building ──

val GAME_RULES_TEXT = """
SPLINTERLANDS COMPLETE GAME RULES REFERENCE

=== COMBAT BASICS ===

POSITIONS: Monsters occupy positions 1-6. Position 1 is the frontline (tank). When position 1 dies, all monsters shift forward.

ATTACK TYPES:
- Melee: Attacks from position 1 ONLY. Cannot attack from positions 2-6 unless the unit has Reach (pos 2), Sneak, Opportunity, Charge, or a ruleset grants one of these.
- Ranged: Attacks from positions 2-6. CANNOT attack from position 1 unless the unit has Close Range or the Close Range ruleset is active. If pushed to pos 1 by deaths, ranged stops attacking.
- Magic: Attacks from ANY position. Bypasses armor and hits HP directly, UNLESS target has Void Armor or Weak Magic ruleset is active.

DAMAGE RESOLUTION:
- Melee/Ranged hits armor first. If armor is depleted, remaining damage is lost UNLESS attacker has Piercing (excess carries to HP).
- Magic ignores armor entirely (hits HP directly), unless Void Armor or Weak Magic applies.
- Shield ability: halves melee/ranged damage (rounded up); attacks of 1 deal 0.
- Void ability: halves magic damage (rounded up); attacks of 1 deal 0.
- Forcefield: takes only 1 damage from attacks with 5+ power.

SPEED & ATTACK ORDER:
- Fastest attacks first (reversed by Reverse Speed ruleset).
- Tiebreakers: Magic > Ranged > Melee > No Attack, then higher rarity, then higher level, then random.

HIT/MISS MECHANICS:
- Base accuracy: 100%.
- Each point defender's speed exceeds attacker's: -10% accuracy.
- Dodge ability: +25% evasion vs melee/ranged.
- Flying ability: +25% evasion vs melee/ranged from non-Flying attackers.
- Blind ability: +15% miss chance on all enemy melee/ranged.
- These stack (Flying + Dodge = 50% base evasion).
- Magic CANNOT miss unless target has Phase (subjects magic to normal hit/miss).
- True Strike: attacks never miss, ignores Blind.
- Snare: attacks vs Flying cannot miss, removes Flying.

HEALING:
- Heal: restores 1/3 of own max HP per round (rounded down).
- Tank Heal: restores 1/3 of position 1 ally's max HP per round (rounded up).
- Triage: heals most-damaged backline ally, max HP / 3 rounded down, minimum 2.
- Repair: restores armor on ally with most armor damage.

STATUS EFFECTS:
- Poisoned: lose 2 HP at start of each round. 50% chance to apply on hit.
- Stunned: skip next turn. 50% chance to apply on hit.
- Afflicted: cannot be healed. 50% chance to apply on hit.
- Burning: lose 2 HP. 33% chance to spread to adjacent units each round. Can be cleansed.
- Enraged: +50% melee attack and speed (rounded up) when damaged.
- Exhausted: skip turn and cannot Retaliate (from Weary ability).

ELEMENTS: Fire, Water, Earth, Life, Death, Dragon, Neutral.
- Summoner determines your element. All monsters must match summoner's element or be Neutral.
- Dragon summoners pick ONE secondary element; all non-Neutral monsters must be that color or Dragon.
- Neutral is always available unless Taking Sides ruleset is active.

=== ALL ABILITIES ===

Affliction: hit has 50% chance to prevent target from being healed.
Ambush: acts before battle begins (during Ambush round).
Amplify: increases Magic Reflect, Return Fire, and Thorns damage by 1 to all enemies.
Armored Strike: additional melee attack equal to armor stat.
Backfire: if enemy misses this unit, attacker takes 2 damage.
Blast: splash damage to monsters adjacent to target (main damage / 2, rounded up).
Blind: all enemy melee/ranged attacks have +15% miss chance.
Bloodlust: +1 to all stats on each kill.
Camouflage: cannot be targeted unless in position 1.
Charge: can use melee attacks from any position.
Cleanse: removes all negative effects from position 1 ally.
Cleanse Rearguard: cleanses last allied backline unit's magic debuffs.
Close Range: ranged can attack from position 1.
Conscript: allows using one additional Gladiator card.
Corrosive Ward: when hit by melee, deals 2 armor damage to attacker and reduces attacker's max armor by 2.
Cripple: each hit reduces target's max HP by 1.
Deathblow: 2x damage if target is the last enemy monster.
Demoralize: -1 melee attack to all enemies (min 1).
Dispel: clears all positive status effects on hit target (including Bloodlust/Martyr buffs).
Divine Shield: first hit deals no damage.
Dodge: +25% evasion vs melee/ranged.
Double Strike: attacks twice per round.
Echo: Repair and all healing/cleansing abilities trigger twice per round.
Electrified: deals 1 damage to all allied units at start of each round.
Enfeeble: -1 to target's melee power after each hit.
Enrage: +50% melee attack and speed when damaged (rounded up).
Execute: if target has 2 or less HP after hit, attacks same target again.
Expose: 80% chance to remove Forcefield/Lookout/Reflection Shield/Shield/Void/Void Armor/Immunity on hit. Always removes Immunity first.
Flank: if in position 1, the unit in position 2 gains Reach.
Flying: +25% evasion vs melee/ranged from non-Flying attackers. Immune to Earthquake damage.
Forcefield: takes only 1 damage from attacks with 5+ power.
Fury: double damage vs targets with Taunt.
Giant Killer: double damage vs targets costing 10+ mana.
Halving: first hit halves target's attack (rounded down).
Headwinds: -1 ranged attack to all enemies.
Heal: restores 1/3 of own max HP per round (rounded down).
Immunity: immune to negative status effects.
Impede: -1 to target's speed after each hit.
Incendiary: at start of round 2+, applies Burning. Burning has 33% chance to spread to adjacent units, then all Burning units lose 2 HP.
Inspire: +1 melee attack to all allies.
Kindred Spirit: adjacent units with Kindred Will gain True Strike at battle start.
Kindred Will: adjacent units with Kindred Spirit gain Ambush at battle start.
Knock Out: double damage vs stunned targets.
Last Stand: +50% all stats when last unit alive (rounded up).
Life Leech: gains max HP equal to damage dealt to enemy HP.
Lookout: adjacent units take 1 less damage from Sneak/Snipe/Opportunity attackers. Team takes half damage from Ambush.
Magic Reflect: returns magic damage / 2 (rounded up) to attacker.
Martyr: when this unit dies, adjacent allies get +1 to all stats.
Mimic: at round 2+, gains random enemy ability. 25% chance to gain attacker's ability when hit.
Opportunity: attacks from any position, targets lowest HP enemy.
Oppress: double damage vs targets with no attack.
Painforge: gains additional attack when damaged by poison, burning, or allied damage (Reckless/Electrified).
Phase: magic attacks can miss this unit (normal hit/miss calculation applies).
Piercing: excess melee/ranged damage beyond armor carries to HP.
Poison: 50% chance to apply Poisoned (2 HP loss per round).
Poison Burst: on death, 100% chance to poison attacker, 50% chance to poison units adjacent to attacker.
Protect: +2 armor to all allies.
Reach: melee can attack from position 2.
Rebirth: self-resurrects with 1 HP once per battle on death.
Recharge: skips a turn, then hits for 3x damage.
Redemption: on death, deals 1 melee damage to all enemies.
Reflection Shield: immune to Blast, Thorns, Return Fire, Magic Reflect damage.
Repair: restores armor on ally with most armor damage.
Resurrect: revives first dead ally at 1 HP (once per battle).
Retaliate: 50% chance to counter-attack melee attackers.
Return Fire: returns ranged damage / 2 (rounded up) to attacker.
Rust: -2 armor to all enemies.
Scattershot: attacks random enemy target.
Scavenger: +1 max HP each time any monster dies.
Shatter: destroys all target armor on hit.
Shield: halves melee/ranged damage (rounded up); attacks of 1 deal 0.
Shield Ward: unit in front gains Shield at battle start.
Shroud of Reflection Shield: adjacent units gain Reflection Shield at battle start.
Silence: -1 magic attack to all enemies.
Slow: -1 speed to all enemies.
Snare: attacks vs Flying cannot miss, removes Flying.
Sneak: targets last enemy instead of first. If last has Camouflage, targets second-to-last.
Snipe: targets enemy ranged/magic/no-attack units not in position 1.
Soul Siphon: self-resurrects with 100% armor and 50% max HP as long as a non-Weary ally exists.
Spite: on death, 100% chance to counterattack.
Strengthen: +1 HP to all allies.
Stun: 50% chance to stun target (skip next turn).
Swiftness: +1 speed to all allies.
Tank Heal: restores 1/3 of position 1 ally's max HP per round (rounded up).
Taunt: all enemies must target this unit.
Thorns: returns 2 melee damage to attacker.
Trample: on kill, attacks next enemy in line.
Triage: heals most-damaged backline ally (max HP / 3, rounded down, min 2).
True Strike: attacks never miss. Ignores Blind.
Void: halves magic damage (rounded up); attacks of 1 deal 0.
Void Armor: magic hits armor before HP.
Weaken: -1 HP to all enemies (min 1).
Weapons Training: adjacent no-attack monsters gain this unit's attack (max 3). Cannot be dispelled.
Weary: 10% chance per round (increasing by 10%/round, max 80%) to become Exhausted (skip turn, no Retaliate).
Wingbreak: always targets first Flying enemy regardless of position. +2 damage vs Flying.

=== ALL RULESETS ===

Aim True: all melee/ranged attacks always hit (grants True Strike).
Aimless: all monsters have Scattershot.
Amplify: all monsters have Amplify.
Arcane Dampening: all units have Void.
Are You Not Entertained: allows one additional Gladiator card.
Armored Up: all monsters get +2 armor.
Back to Basics: all monsters lose all abilities. Raw stats only.
Backlash: all units that miss take 2 true damage.
Blood Moon: all units have Bloodlust.
Born Again: all monsters have Rebirth.
Briar Patch: all monsters have Thorns. Avoid melee.
Broken Arrows: ranged monsters cannot be used.
Brute Force: units with highest individual attack power attack first.
Close Range: ranged can attack from position 1 (grants Close Range).
Collateral Damage: all units have Reckless debilitation.
Counterspell: all monsters have Magic Reflect. Avoid magic.
Death Has No Power: units gain Final Rest (on-defeat abilities don't trigger).
Deflection Shield: all units have Reflection Shield (immune to Blast/Thorns/Return Fire/Magic Reflect).
Dragon Breath: all ranged/magic attacks inflict Burning on target.
Earthquake: non-Flying take 2 damage per round. Prefer Flying monsters.
Equal Opportunity: all monsters have Opportunity (target lowest HP from any position).
Equalizer: all HP equals the highest base HP on either team. Pick low-mana high-attack monsters.
Even Stevens: only even-mana monsters allowed (0 is even).
Explosive Weaponry: all monsters have Blast (splash damage).
Ferocity: all monsters have Fury (double damage vs Taunt).
Fire & Regret: all monsters have Return Fire. Avoid ranged.
Fog of War: Sneak/Snipe/Opportunity removed. Only position 1 targeted.
Four's a Crowd: max 4 units.
Frostbite: all units have Weary. Non-attacking units take 2 true damage per round.
Global Warming: all units start with Burning status (2 dmg/round, can spread).
Going the Distance: only ranged monsters allowed.
Healed Out: all healing removed. Raw HP/armor matters.
Heavy Hitters: all monsters have Knock Out (double damage vs stunned).
Heavy Metal: only armored units allowed.
Hey Jealousy: all units target highest HP enemy. Melee attacks from any position.
High Five: max 5 units.
Holy Protection: all monsters have Divine Shield (first hit ignored).
Junior Varsity: only units costing 6 or less mana.
Keep Your Distance: melee monsters cannot be used.
Little League: only monsters/summoners costing 4 or less mana.
Lost Legendaries: legendary monsters cannot be used.
Lost Magic: magic monsters cannot be used.
Melee Mayhem: melee can attack from any position (grants Charge).
Might Makes Right: only units with 3+ attack power.
Need for Speed: only units with 3+ speed.
No Pain No Gain: all units have Painforge.
Now You See Me: all units have Camouflage.
Noxious Fumes: all monsters start Poisoned (2 dmg/round). High HP crucial.
Odd Ones Out: only odd-mana monsters allowed (0 is not odd).
Reverse Speed: slowest attacks first, highest dodge. Pick slow heavy hitters.
Rise of the Commons: only Common and Rare monsters.
Shades of Gray: only Neutral monsters.
Silenced Summoners: summoners give no buffs/debuffs/abilities.
Stampede: Trample can chain multiple times per attack.
Standard: no special rules.
Super Sneak: all melee have Sneak (hit last enemy). All melee can attack. Protect backline.
Taking Sides: neutral monsters cannot be used.
Target Practice: all ranged/magic have Snipe (target backline ranged/magic).
Thick Skinned: all units have Shield.
Tis But Scratches: all units have Cripple.
Unprotected: all armor is 0, armor abilities don't work. Magic less valuable.
Up Close & Personal: only melee monsters allowed.
Wands Out: only magic monsters allowed.
Weak Magic: magic hits armor first (grants Void Armor).
What Doesn't Kill You: all monsters have Enrage (+50% melee/speed when damaged).
""".trimIndent()

private val SYSTEM_PROMPT_TEMPLATE = """
You are a Splinterlands battle expert. Given the match constraints and available cards, pick the best team.

{game_rules}

TEAM BUILDING RULES (CRITICAL — violating these makes the team invalid):
1. Pick exactly 1 summoner and 1-{max_monsters} monsters, using their numeric ID.
2. Total mana (summoner + all monsters) must not exceed {mana_cap}. Use at least 70% of available mana.
3. COLOR RULE: Every monster must be the SAME color as the summoner, or Neutral (Gray).
   - Example: Life summoner → only Life and Neutral monsters. Fire monsters are FORBIDDEN.
   - Dragon summoners: pick ONE ally color. ALL non-Neutral monsters must be that ONE color. Dragon-type monsters are always allowed.
4. GLADIATOR RULE: [GLAD] monsters are FORBIDDEN unless your chosen summoner has [CONSCRIPT]. If the summoner is [CONSCRIPT], you may include exactly 1 [GLAD] monster. If the summoner is NOT [CONSCRIPT], you MUST NOT pick any [GLAD] monster.
5. Each monster ID can only be used once (no duplicates).

ACTIVE RULESETS FOR THIS MATCH:
{rulesets_desc}
Apply these rulesets when picking your team — they change which abilities/attack types are effective.

IMPORTANT: Do NOT analyze every card. Just pick a strong team and output the answer.
Respond with ONLY valid JSON — no markdown, no explanation, no analysis.
Use plain integers for IDs (e.g. 13 not "S13").
{{"summoner": <number>, "monsters": [<number>, ...], "mana_total": <number>}}
""".trimIndent()

val RULESET_STRATEGY_HINTS = mapOf(
    "Melee Mayhem" to "All melee attack from any position - load up on melee",
    "Super Sneak" to "All melee have Sneak (hit last position) - protect backline",
    "Equal Opportunity" to "All units target lowest HP - avoid low-HP glass cannons",
    "Explosive Weaponry" to "All units have Blast (splash) - spread HP, avoid clustering",
    "Earthquake" to "Non-flying take 2 dmg/round - prefer flying monsters",
    "Noxious Fumes" to "All units poisoned - high HP matters most",
    "Reverse Speed" to "Slowest attacks first - pick slow heavy hitters",
    "Equalizer" to "All HP = highest base HP - pick low-mana, high-attack monsters",
    "Back to Basics" to "All abilities removed - raw stats matter most",
    "Aim True" to "Attacks never miss - speed for dodge is useless",
    "Target Practice" to "All ranged/magic have Snipe - protect non-melee backline",
    "Briar Patch" to "Thorns return 2 melee damage - avoid melee if possible",
    "Counterspell" to "Magic Reflect on all - avoid magic if possible",
    "Fire & Regret" to "Return Fire on all - avoid ranged if possible",
    "Holy Protection" to "All have Divine Shield - multi-hit is better",
    "Fog of War" to "No Sneak/Snipe/Opportunity - only pos 1 targeted",
    "Healed Out" to "No healing - raw HP/armor is king",
    "Unprotected" to "All armor is 0 - armor-based monsters weaker",
    "Armored Up" to "All +2 armor - magic bypasses armor, prefer magic",
    "Weak Magic" to "Magic hits armor first - prefer physical damage",
    "Close Range" to "Ranged attacks from pos 1 - ranged monsters can tank",
    "Born Again" to "All resurrect once at 1 HP - every unit gets second life",
    "Blood Moon" to "Bloodlust on all - each kill buffs the killer",
    "Maneuvers" to "All have Reach - melee from position 2 works",
    "Stampede" to "Trample chains - big melee can chain kills",
    "What Doesn't Kill You" to "All Enrage when damaged - high base stats amplify",
)

data class LlmPromptResult(
    val systemPrompt: String,
    val userMessage: String,
    val idMap: Map<Int, String>,
)

/**
 * Remove monsters that can't be played with any available summoner.
 * A monster is playable if it matches at least one summoner's color or is Neutral.
 * Dragon summoners can use any color, so if any Dragon summoner exists, all monsters are playable.
 */
fun filterUnplayable(monsters: List<CardEntry>, summoners: List<SummonerEntry>): List<CardEntry> {
    val summonerColors = summoners.map { it.color }.toSet()
    // Dragon summoner present — every monster is playable
    if ("Gold" in summonerColors) return monsters
    val playable = summonerColors + "Gray"
    return monsters.filter { it.color in playable }
}

fun buildLlmPrompt(
    summoners: List<SummonerEntry>,
    monsters: List<CardEntry>,
    matchInfo: JsonObject,
    maxMonsters: Int,
): LlmPromptResult {
    val manaCap = matchInfo["mana_cap"]?.jsonPrimitive?.int ?: 20
    val rulesetsStr = matchInfo["ruleset"]?.jsonPrimitive?.content ?: ""
    val rulesets = rulesetsStr.split("|").map { it.trim() }.filter { it.isNotBlank() }

    val rulesetLines = rulesets.map { r ->
        val hint = RULESET_STRATEGY_HINTS[r]
        if (hint != null) "- $r: $hint" else "- $r"
    }
    val rulesetsDesc = if (rulesetLines.isNotEmpty()) rulesetLines.joinToString("\n") else "- Standard (no special rules)"

    val system = SYSTEM_PROMPT_TEMPLATE
        .replace("{game_rules}", GAME_RULES_TEXT)
        .replace("{max_monsters}", maxMonsters.toString())
        .replace("{mana_cap}", manaCap.toString())
        .replace("{rulesets_desc}", rulesetsDesc)

    // Filter out unplayable monsters
    val filteredMonsters = filterUnplayable(monsters, summoners)

    val idMap = mutableMapOf<Int, String>()

    val summonerLines = summoners.mapIndexed { i, s ->
        val sid = i + 1
        idMap[sid] = s.uid
        val parts = mutableListOf("S$sid: ${s.name}", s.splinter, "${s.mana}m")
        val buffStrs = mutableListOf<String>()
        if (s.buffs.attack != 0) buffStrs.add("${if (s.buffs.attack > 0) "+" else ""}${s.buffs.attack} attack")
        if (s.buffs.ranged != 0) buffStrs.add("${if (s.buffs.ranged > 0) "+" else ""}${s.buffs.ranged} ranged")
        if (s.buffs.magic != 0) buffStrs.add("${if (s.buffs.magic > 0) "+" else ""}${s.buffs.magic} magic")
        if (s.buffs.armor != 0) buffStrs.add("${if (s.buffs.armor > 0) "+" else ""}${s.buffs.armor} armor")
        if (s.buffs.health != 0) buffStrs.add("${if (s.buffs.health > 0) "+" else ""}${s.buffs.health} health")
        if (s.buffs.speed != 0) buffStrs.add("${if (s.buffs.speed > 0) "+" else ""}${s.buffs.speed} speed")
        if (buffStrs.isNotEmpty()) parts.add(buffStrs.joinToString(", "))
        if (s.buffs.abilities.isNotEmpty()) parts.add(s.buffs.abilities.joinToString(", "))
        val conscriptTag = if ("Conscript" in s.buffs.abilities) " [CONSCRIPT]" else ""
        parts.joinToString(" | ") + conscriptTag
    }

    val monsterLines = filteredMonsters.mapIndexed { i, m ->
        val mid = i + 1
        idMap[1000 + mid] = m.uid
        val gladTag = if (m.isGladiator) " [GLAD]" else ""
        val line = "M$mid: ${m.name} | ${m.splinter} | ${m.attackType} | ${m.mana}m | ${m.attackPower}atk ${m.speed}spd ${m.armor}arm ${m.health}hp$gladTag"
        if (m.abilities.isNotEmpty()) "$line | ${m.abilities.joinToString(", ")}" else line
    }

    val userMsg = buildString {
        appendLine("Mana cap: $manaCap")
        appendLine("Max monsters: $maxMonsters")
        appendLine()
        appendLine("SUMMONERS (${summoners.size} available):")
        summonerLines.forEach { appendLine(it) }
        appendLine()
        appendLine("MONSTERS (${filteredMonsters.size} available):")
        monsterLines.forEach { appendLine(it) }
        appendLine()
        appendLine("Pick a strong team NOW. Do NOT list or analyze every card. Just output JSON immediately.")
        append("""{"summoner": <S number>, "monsters": [<M number>, ...], "mana_total": <number>}""")
    }

    return LlmPromptResult(system, userMsg, idMap)
}

// ── LLM response parsing ──

data class LlmPick(
    val summonerUid: String,
    val monsterUids: List<String>,
)

fun parseLlmPick(responseText: String, idMap: Map<Int, String>): LlmPick? {
    var text = responseText.trim()
    if (text.startsWith("```")) {
        text = text.substringAfter("\n").substringBeforeLast("```").trim()
    }

    val jsonStr = try {
        kotlinx.serialization.json.Json.parseToJsonElement(text)
        text
    } catch (_: Exception) {
        val start = text.indexOf("{")
        val end = text.lastIndexOf("}") + 1
        if (start >= 0 && end > start) {
            val extracted = text.substring(start, end)
            // Try extracted as-is, then try fixing unquoted S/M identifiers
            try {
                kotlinx.serialization.json.Json.parseToJsonElement(extracted)
                extracted
            } catch (_: Exception) {
                val fixed = extracted.replace(Regex("""(?<=[:\[,\s])([SM]\d+)(?=[,\]\s}])"""), "\"$1\"")
                try {
                    kotlinx.serialization.json.Json.parseToJsonElement(fixed)
                    fixed
                } catch (_: Exception) {
                    return null
                }
            }
        } else {
            return null
        }
    }

    val pick = try {
        kotlinx.serialization.json.Json.parseToJsonElement(jsonStr).jsonObject
    } catch (_: Exception) {
        return null
    }

    val rawSummoner = pick["summoner"]?.jsonPrimitive?.content ?: return null
    val rawMonsters = pick["monsters"]?.jsonArray?.map { it.jsonPrimitive.content } ?: return null

    fun resolveId(raw: String, prefix: String, offset: Int): String? {
        val cleaned = raw.trim().uppercase().removePrefix(prefix)
        val num = cleaned.toIntOrNull() ?: return null
        return idMap[offset + num]
    }

    val summonerUid = resolveId(rawSummoner, "S", 0) ?: return null
    val monsterUids = rawMonsters.mapNotNull { resolveId(it, "M", 1000) }
    if (monsterUids.isEmpty()) return null

    return LlmPick(summonerUid, monsterUids)
}

// ── Team validation ──

fun validateTeam(
    summonerUid: String,
    monsterUids: List<String>,
    summoners: List<SummonerEntry>,
    monsters: List<CardEntry>,
    manaCap: Int,
    maxMonsters: Int,
    rulesets: Set<String>,
): List<String> {
    val issues = mutableListOf<String>()
    val summonerEntry = summoners.find { it.uid == summonerUid }
    if (summonerEntry == null) {
        issues.add("invalid summoner")
        return issues
    }

    val monsterLookup = monsters.associateBy { it.uid }
    val totalMana = summonerEntry.mana + monsterUids.sumOf { monsterLookup[it]?.mana ?: 0 }

    if (totalMana > manaCap) {
        issues.add("mana exceeded: $totalMana > $manaCap")
    }

    if (monsterUids.size > maxMonsters) {
        issues.add("too many monsters: ${monsterUids.size} > $maxMonsters")
    }

    // Color check
    val summonerColor = summonerEntry.color
    if (summonerColor == "Gold") {
        val nonNeutral = monsterUids.mapNotNull { uid ->
            val c = monsterLookup[uid]?.color
            if (c != null && c != "Gray" && c != "Gold") c else null
        }
        val unique = nonNeutral.toSet()
        if (unique.size > 1) {
            issues.add("dragon mixed ally colors: $unique — pick ONE ally color")
        }
        val validColors = if (unique.size == 1) setOf(unique.first(), "Gray", "Gold") else setOf("Gray", "Gold")
        for (uid in monsterUids) {
            val m = monsterLookup[uid] ?: continue
            if (m.color !in validColors) {
                issues.add("${m.name} is ${m.splinter} — not allowed with this summoner")
            }
        }
    } else {
        val validColors = setOf(summonerColor, "Gray")
        for (uid in monsterUids) {
            val m = monsterLookup[uid] ?: continue
            if (m.color !in validColors) {
                issues.add("${m.name} is ${m.splinter} — not allowed with this summoner")
            }
        }
    }

    // Gladiator check — max 1, only with Conscript summoner
    val hasConscript = "Conscript" in summonerEntry.buffs.abilities
    val gladLimit = if (hasConscript) 1 else 0
    val gladCount = monsterUids.count { monsterLookup[it]?.isGladiator == true }
    if (gladCount > gladLimit) {
        issues.add("too many gladiators: $gladCount, max $gladLimit (summoner ${if (hasConscript) "has" else "does NOT have"} Conscript)")
    }

    // Duplicate check
    if (monsterUids.size != monsterUids.toSet().size) {
        issues.add("duplicate monsters")
    }

    return issues
}

fun buildFeedbackMessage(issues: List<String>): String = buildString {
    appendLine("Your team is INVALID:")
    for (issue in issues) {
        appendLine("- $issue")
    }
    appendLine()
    append("Fix these errors. Same constraints apply. Return ONLY valid JSON.")
}

// ── Silent fixes (last resort) ──

fun applyFixes(
    summonerUid: String,
    monsterUids: List<String>,
    summoners: List<SummonerEntry>,
    allMonsters: List<CardEntry>,
    manaCap: Int,
    maxMonsters: Int,
    rulesets: Set<String>,
): TeamSelection? {
    val summonerEntry = summoners.find { it.uid == summonerUid } ?: return null
    val monsterLookup = allMonsters.associateBy { it.uid }

    // Deduplicate
    var fixed = monsterUids.distinct().toMutableList()

    // Trim to max_monsters
    if (fixed.size > maxMonsters) {
        fixed = fixed.take(maxMonsters).toMutableList()
    }

    // Trim mana
    var totalMana = summonerEntry.mana + fixed.sumOf { monsterLookup[it]?.mana ?: 0 }
    if (totalMana > manaCap) {
        val trimmed = mutableListOf<String>()
        var used = summonerEntry.mana
        for (uid in fixed) {
            val mMana = monsterLookup[uid]?.mana ?: 0
            if (used + mMana <= manaCap) {
                trimmed.add(uid)
                used += mMana
            }
        }
        fixed = trimmed
    }

    // Fix colors
    val summonerColor = summonerEntry.color
    val validColors: Set<String>
    if (summonerColor == "Gold") {
        val allyColor = determineDragonAllyColor(summonerColor, fixed, monsterLookup)
        validColors = if (allyColor != null) setOf(allyColor, "Gray", "Gold") else setOf("Gray", "Gold")
    } else {
        validColors = setOf(summonerColor, "Gray")
    }
    fixed = fixed.filter { monsterLookup[it]?.color in validColors }.toMutableList()

    // Fix gladiators — max 1 with Conscript, 0 otherwise
    val hasConscript = "Conscript" in summonerEntry.buffs.abilities
    val gladLimit = if (hasConscript) 1 else 0
    var gladCount = 0
    fixed = fixed.filter { uid ->
        val m = monsterLookup[uid] ?: return@filter true
        if (m.isGladiator) {
            if (gladCount < gladLimit) {
                gladCount++
                true
            } else {
                false
            }
        } else {
            true
        }
    }.toMutableList()

    // Auto-fill empty slots
    val usedMana = summonerEntry.mana + fixed.sumOf { monsterLookup[it]?.mana ?: 0 }
    var remaining = manaCap - usedMana
    val usedDetailIds = fixed.mapNotNull { monsterLookup[it]?.detailId }.toMutableSet()
    val uidSet = fixed.toMutableSet()

    if (remaining > 0 && fixed.size < maxMonsters) {
        val candidates = allMonsters
            .filter { it.uid !in uidSet && it.detailId !in usedDetailIds && it.color in validColors && it.mana <= remaining }
            .filter { !(it.isGladiator && gladCount >= gladLimit) }
            .sortedByDescending { it.mana }
        for (m in candidates) {
            if (fixed.size >= maxMonsters) break
            if (m.mana <= remaining) {
                fixed.add(m.uid)
                remaining -= m.mana
                uidSet.add(m.uid)
                usedDetailIds.add(m.detailId)
            }
        }
    }

    if (fixed.isEmpty()) return null

    // Efficiency check
    val finalMana = summonerEntry.mana + fixed.sumOf { monsterLookup[it]?.mana ?: 0 }
    if (manaCap > 0 && finalMana.toDouble() / manaCap < 0.7) return null

    // Determine ally color for Dragon
    val allyColor = determineDragonAllyColor(summonerColor, fixed, monsterLookup)

    return TeamSelection(summonerUid, fixed, allyColor)
}

// ── Legacy single-call parser (delegates to new functions) ──

fun parseLlmResponse(
    responseText: String,
    idMap: Map<Int, String>,
    summoners: List<SummonerEntry>,
    monsters: List<CardEntry>,
    manaCap: Int,
    maxMonsters: Int,
    rulesets: Set<String>,
): TeamSelection? {
    val pick = parseLlmPick(responseText, idMap) ?: return null
    val issues = validateTeam(pick.summonerUid, pick.monsterUids, summoners, monsters, manaCap, maxMonsters, rulesets)
    return if (issues.isEmpty()) {
        val summonerEntry = summoners.find { it.uid == pick.summonerUid } ?: return null
        val allyColor = determineDragonAllyColor(summonerEntry.color, pick.monsterUids, monsters.associateBy { it.uid })
        TeamSelection(pick.summonerUid, pick.monsterUids, allyColor)
    } else {
        applyFixes(pick.summonerUid, pick.monsterUids, summoners, monsters, manaCap, maxMonsters, rulesets)
    }
}
