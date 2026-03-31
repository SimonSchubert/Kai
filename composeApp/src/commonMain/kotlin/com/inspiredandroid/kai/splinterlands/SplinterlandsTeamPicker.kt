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

// ── Ruleset context ──

private data class RulesetContext(
    // Attack type positioning
    val meleeFromAnyPosition: Boolean,
    val superSneak: Boolean,
    val rangedFromPos1: Boolean,
    val fogOfWar: Boolean,
    val nowYouSeeMe: Boolean,
    // Damage type penalties (reflect rulesets)
    val avoidMelee: Boolean,
    val avoidMagic: Boolean,
    val avoidRanged: Boolean,
    val preferMagic: Boolean,
    // Damage type bonuses
    val armoredUp: Boolean,
    val weakMagic: Boolean,
    val unprotected: Boolean,
    val arcaneDampening: Boolean,
    // Stat/survival shifts
    val earthquake: Boolean,
    val reverseSpeed: Boolean,
    val noxiousFumes: Boolean,
    val globalWarming: Boolean,
    val equalizer: Boolean,
    val backToBasics: Boolean,
    val healedOut: Boolean,
    // Targeting changes
    val equalOpportunity: Boolean,
    val targetPractice: Boolean,
    val heyJealousy: Boolean,
    val aimless: Boolean,
    val ferocity: Boolean,
    // Combat modifiers
    val aimTrue: Boolean,
    val backlash: Boolean,
    val holyProtection: Boolean,
    val explosiveWeaponry: Boolean,
    val bloodMoon: Boolean,
    val heavyHitters: Boolean,
    val whatDoesntKillYou: Boolean,
    val stampede: Boolean,
    val bruteForce: Boolean,
    val tisButScratches: Boolean,
    // Death/resurrection
    val bornAgain: Boolean,
    val deathHasNoPower: Boolean,
    // Other
    val silencedSummoners: Boolean,
    val deflectionShield: Boolean,
    val amplify: Boolean,
    val dragonBreath: Boolean,
    val frostbite: Boolean,
    val collateralDamage: Boolean,
    val noPainNoGain: Boolean,
    val areYouNotEntertained: Boolean,
    // Enemy composition (both players face same card restrictions)
    val enemyNoMelee: Boolean,
    val enemyNoRanged: Boolean,
    val enemyNoMagic: Boolean,
    val enemyOnlyMelee: Boolean,
    val enemyOnlyRanged: Boolean,
    val enemyOnlyMagic: Boolean,
)

private fun buildRulesetContext(rulesets: Set<String>): RulesetContext = RulesetContext(
    meleeFromAnyPosition = "Melee Mayhem" in rulesets || "Super Sneak" in rulesets || "Hey Jealousy" in rulesets,
    superSneak = "Super Sneak" in rulesets,
    rangedFromPos1 = "Close Range" in rulesets,
    fogOfWar = "Fog of War" in rulesets,
    nowYouSeeMe = "Now You See Me" in rulesets,
    avoidMelee = "Briar Patch" in rulesets,
    avoidMagic = "Counterspell" in rulesets,
    avoidRanged = "Fire & Regret" in rulesets,
    preferMagic = "Thick Skinned" in rulesets,
    armoredUp = "Armored Up" in rulesets,
    weakMagic = "Weak Magic" in rulesets,
    unprotected = "Unprotected" in rulesets,
    arcaneDampening = "Arcane Dampening" in rulesets,
    earthquake = "Earthquake" in rulesets,
    reverseSpeed = "Reverse Speed" in rulesets,
    noxiousFumes = "Noxious Fumes" in rulesets,
    globalWarming = "Global Warming" in rulesets,
    equalizer = "Equalizer" in rulesets,
    backToBasics = "Back to Basics" in rulesets,
    healedOut = "Healed Out" in rulesets,
    equalOpportunity = "Equal Opportunity" in rulesets,
    targetPractice = "Target Practice" in rulesets,
    heyJealousy = "Hey Jealousy" in rulesets,
    aimless = "Aimless" in rulesets,
    ferocity = "Ferocity" in rulesets,
    aimTrue = "Aim True" in rulesets,
    backlash = "Backlash" in rulesets,
    holyProtection = "Holy Protection" in rulesets,
    explosiveWeaponry = "Explosive Weaponry" in rulesets,
    bloodMoon = "Blood Moon" in rulesets,
    heavyHitters = "Heavy Hitters" in rulesets,
    whatDoesntKillYou = "What Doesn't Kill You" in rulesets,
    stampede = "Stampede" in rulesets,
    bruteForce = "Brute Force" in rulesets,
    tisButScratches = "Tis But Scratches" in rulesets,
    bornAgain = "Born Again" in rulesets,
    deathHasNoPower = "Death Has No Power" in rulesets,
    silencedSummoners = "Silenced Summoners" in rulesets,
    deflectionShield = "Deflection Shield" in rulesets,
    amplify = "Amplify" in rulesets,
    dragonBreath = "Dragon Breath" in rulesets,
    frostbite = "Frostbite" in rulesets,
    collateralDamage = "Collateral Damage" in rulesets,
    noPainNoGain = "No Pain No Gain" in rulesets,
    areYouNotEntertained = "Are You Not Entertained" in rulesets,
    enemyNoMelee = "Keep Your Distance" in rulesets,
    enemyNoRanged = "Broken Arrows" in rulesets,
    enemyNoMagic = "Lost Magic" in rulesets,
    enemyOnlyMelee = "Up Close & Personal" in rulesets,
    enemyOnlyRanged = "Going the Distance" in rulesets,
    enemyOnlyMagic = "Wands Out" in rulesets,
)

// ── Monster and summoner scoring ──

private enum class Position { TANK, BACKLINE }

private fun scoreMonster(m: CardEntry, pos: Position, ctx: RulesetContext): Double {
    var score = 0.0

    // ── Base stats ──
    score += m.health * 1.5
    if (!ctx.unprotected) score += m.armor * 1.0
    score += m.attackPower * 2.0
    score += m.speed * 0.5

    // ── Attack type positioning ──
    if (m.attackType == "melee") {
        val hasPositional = !ctx.backToBasics && (
            "Reach" in m.abilities || "Sneak" in m.abilities ||
                "Opportunity" in m.abilities || "Charge" in m.abilities
            )
        if (pos == Position.BACKLINE && !ctx.meleeFromAnyPosition && !hasPositional) {
            score -= m.attackPower * 2.0
        }
    }
    if (m.attackType == "ranged") {
        val hasCloseRange = !ctx.backToBasics && "Close Range" in m.abilities
        if (pos == Position.TANK && !ctx.rangedFromPos1 && !hasCloseRange) {
            score -= m.attackPower * 2.0
        }
    }
    if (m.attackType == "none") {
        if (ctx.frostbite) score -= 8.0
    }

    // ── Position-specific ability bonuses (gated by backToBasics) ──
    if (!ctx.backToBasics) {
        if (pos == Position.TANK) {
            score += m.health * 1.0
            if (!ctx.unprotected) score += m.armor * 1.5
            // Shield halves melee/ranged -- worthless if enemy is pure magic
            if ("Shield" in m.abilities && !ctx.enemyOnlyMagic) score += 8.0
            // Void halves magic -- worthless if enemy has no magic
            if ("Void" in m.abilities && !ctx.enemyNoMagic) score += 6.0
            if ("Heal" in m.abilities && !ctx.healedOut) score += 10.0
            if ("Divine Shield" in m.abilities) score += 4.0
            if ("Forcefield" in m.abilities) score += 4.0
            // Dodge/Flying evasion only works vs melee/ranged
            if ("Dodge" in m.abilities && !ctx.aimTrue && !ctx.enemyOnlyMagic) score += 3.0
            if ("Flying" in m.abilities && !ctx.aimTrue && !ctx.enemyOnlyMagic) score += 2.0
            if ("Void Armor" in m.abilities && !ctx.enemyNoMagic) score += 3.0
            if ("Enrage" in m.abilities) score += 3.0
            // Retaliate counters melee attackers
            if ("Retaliate" in m.abilities && !ctx.enemyNoMelee) score += 2.0
            if ("Rebirth" in m.abilities && !ctx.deathHasNoPower && !ctx.bornAgain) score += 4.0
            if ("Last Stand" in m.abilities) score += 2.0
            // Taunt: great for tank unless Ferocity (Fury doubles damage vs Taunt)
            if ("Taunt" in m.abilities) {
                score += if (ctx.ferocity) {
                    -5.0
                } else if (ctx.aimless) {
                    1.0
                } else {
                    5.0
                }
            }
            // Thorns: good on tank vs melee, boosted by Amplify, useless with Deflection Shield or no enemy melee
            if ("Thorns" in m.abilities && !ctx.enemyNoMelee) {
                if (!ctx.deflectionShield) score += if (ctx.amplify) 5.0 else 3.0
            }
            // Magic Reflect: useless if enemy has no magic
            if ("Magic Reflect" in m.abilities && !ctx.enemyNoMagic) {
                if (!ctx.deflectionShield) score += if (ctx.amplify) 4.0 else 2.0
            }
        }

        if (pos == Position.BACKLINE) {
            // Healing/support
            if ("Tank Heal" in m.abilities && !ctx.healedOut) score += 10.0
            if ("Triage" in m.abilities && !ctx.healedOut) score += 6.0
            if ("Heal" in m.abilities && !ctx.healedOut) score += 4.0
            if ("Repair" in m.abilities && !ctx.unprotected) score += 5.0
            if ("Protect" in m.abilities && !ctx.unprotected) score += 5.0
            if ("Strengthen" in m.abilities) score += 3.0
            if ("Cleanse" in m.abilities) score += 3.0
            if ("Resurrect" in m.abilities && !ctx.deathHasNoPower) score += 5.0
            if ("Rebirth" in m.abilities && !ctx.deathHasNoPower && !ctx.bornAgain) score += 3.0
            // Offensive
            if ("Snipe" in m.abilities && !ctx.fogOfWar && !ctx.nowYouSeeMe) score += 2.0
            if ("Sneak" in m.abilities && !ctx.fogOfWar && !ctx.nowYouSeeMe) score += 2.0
            if ("Opportunity" in m.abilities && !ctx.fogOfWar && !ctx.nowYouSeeMe) score += 2.0
            if ("Blast" in m.abilities && !ctx.deflectionShield) score += 3.0
            if ("Double Strike" in m.abilities) score += 4.0
            if ("Poison" in m.abilities) score += 2.0
            if ("Stun" in m.abilities) score += if (ctx.heavyHitters) 8.0 else 2.0
            if ("Piercing" in m.abilities) score += 1.5
            if ("Trample" in m.abilities) score += if (ctx.stampede) 4.0 else 1.0
            if ("Deathblow" in m.abilities) score += 1.5
            if ("Recharge" in m.abilities) score += 1.0
            // Team buffs
            if ("Inspire" in m.abilities) score += 4.0
            if ("Swiftness" in m.abilities) score += if (ctx.reverseSpeed) -2.0 else 2.0
            // Enemy debuffs: only valuable if enemy uses that attack type
            if ("Demoralize" in m.abilities && !ctx.enemyNoMelee) score += 3.0
            if ("Headwinds" in m.abilities && !ctx.enemyNoRanged) score += 3.0
            if ("Silence" in m.abilities && !ctx.enemyNoMagic) score += 3.0
            if ("Slow" in m.abilities) score += if (ctx.reverseSpeed) -2.0 else 2.0
            if ("Blind" in m.abilities && !ctx.aimTrue && !ctx.enemyOnlyMagic) score += 2.0
            if ("Rust" in m.abilities && !ctx.unprotected) score += 2.0
            if ("Weaken" in m.abilities) score += 2.0
            if ("Weapons Training" in m.abilities) score += 4.0
            // Defensive for backline
            if ("Taunt" in m.abilities) {
                score += if (ctx.ferocity) {
                    -5.0
                } else if (ctx.aimless) {
                    0.0
                } else {
                    -2.0
                }
            }
            if ("Camouflage" in m.abilities) score += 2.0
            // Return Fire / Magic Reflect on backline: only if enemy uses that type
            if ("Return Fire" in m.abilities && !ctx.deflectionShield && !ctx.enemyNoRanged) score += if (ctx.amplify) 3.0 else 1.5
            if ("Magic Reflect" in m.abilities && !ctx.deflectionShield && !ctx.enemyNoMagic) score += if (ctx.amplify) 3.0 else 1.5
            // Martyr
            if ("Martyr" in m.abilities && !ctx.deathHasNoPower) score += 3.0
            if ("Redemption" in m.abilities && !ctx.deathHasNoPower && !ctx.deflectionShield) score += 1.5
        }
    } else {
        // Back to Basics: only raw stat bonuses for tank
        if (pos == Position.TANK) {
            score += m.health * 1.0
            if (!ctx.unprotected) score += m.armor * 1.5
        }
    }

    // ── Ruleset modifiers (applied to both positions) ──

    // Earthquake
    if (ctx.earthquake) {
        if (!ctx.backToBasics && "Flying" in m.abilities) score += 8.0 else score -= 6.0
    }

    // Reverse Speed
    if (ctx.reverseSpeed) {
        score -= m.speed * 1.0
        score += (10 - m.speed).coerceAtLeast(0) * 0.5
    }

    // Noxious Fumes
    if (ctx.noxiousFumes) {
        score += m.health * 1.0
        if (!ctx.backToBasics) {
            if ("Immunity" in m.abilities) score += 10.0
            if ("Cleanse" in m.abilities) score += 5.0
            if ("Heal" in m.abilities && !ctx.healedOut) score += 5.0
        }
    }

    // Global Warming
    if (ctx.globalWarming) {
        score += m.health * 0.5
        if (!ctx.backToBasics) {
            if ("Immunity" in m.abilities) score += 6.0
            if ("Cleanse" in m.abilities) score += 3.0
        }
    }

    // Equalizer
    if (ctx.equalizer) {
        score -= m.health * 1.5
        score += m.attackPower * 2.0
        if (m.mana <= 4 && m.attackPower >= 2) score += 4.0
    }

    // Damage type avoidance (reflect rulesets)
    if (ctx.avoidMelee && m.attackType == "melee" && !ctx.deflectionShield) score -= 5.0
    if (ctx.avoidMagic && m.attackType == "magic" && !ctx.deflectionShield) score -= 5.0
    if (ctx.avoidRanged && m.attackType == "ranged" && !ctx.deflectionShield) score -= 5.0

    // Thick Skinned (Shield on all): halves melee/ranged, prefer magic
    if (ctx.preferMagic) {
        if (m.attackType == "melee" || m.attackType == "ranged") score -= 4.0
        if (m.attackType == "magic") score += 3.0
    }

    // Arcane Dampening (Void on all): magic halved
    if (ctx.arcaneDampening && m.attackType == "magic") score -= 4.0

    // Armored Up: magic bypasses the +2 armor
    if (ctx.armoredUp && m.attackType == "magic" && !ctx.weakMagic) score += 3.0

    // Weak Magic: magic hits armor first
    if (ctx.weakMagic && m.attackType == "magic") score -= 3.0

    // What Doesn't Kill You: Enrage on all melee
    if (ctx.whatDoesntKillYou && m.attackType == "melee") score += 4.0

    // Stampede: melee with Trample
    if (ctx.stampede && !ctx.backToBasics && "Trample" in m.abilities && m.attackType == "melee") score += 4.0

    // Equal Opportunity: low HP is dangerous
    if (ctx.equalOpportunity) {
        if (m.health <= 3 && pos == Position.BACKLINE) score -= 4.0
        score += m.health * 0.5
    }

    // Target Practice: backline ranged/magic get targeted
    if (ctx.targetPractice && pos == Position.BACKLINE) {
        if (m.attackType == "ranged" || m.attackType == "magic") score += m.health * 0.5
    }

    // Hey Jealousy: high HP attracts fire (non-tanks)
    if (ctx.heyJealousy && pos == Position.BACKLINE) {
        score -= m.health * 0.3
    }

    // Aim True: evasion worthless
    if (ctx.aimTrue) {
        score -= m.speed * 0.5
    }

    // Backlash: True Strike valuable, speed helps avoid self-damage
    if (ctx.backlash) {
        if (!ctx.backToBasics && "True Strike" in m.abilities) score += 4.0
        score += m.speed * 0.5
    }

    // Holy Protection: multi-hit to pop Divine Shield
    if (ctx.holyProtection && !ctx.backToBasics) {
        if ("Double Strike" in m.abilities) score += 4.0
        if ("Blast" in m.abilities && !ctx.deflectionShield) score += 2.0
    }

    // Explosive Weaponry: spread damage, Reflection Shield valuable
    if (ctx.explosiveWeaponry) {
        score += m.health * 0.5
        if (!ctx.backToBasics && "Reflection Shield" in m.abilities) score += 5.0
    }

    // Blood Moon: kills buff the killer
    if (ctx.bloodMoon) {
        score += m.attackPower * 1.0
    }

    // Brute Force: highest attack goes first
    if (ctx.bruteForce) {
        score += m.attackPower * 0.5
    }

    // Tis But Scratches: Cripple on all, high attack = faster HP reduction
    if (ctx.tisButScratches) {
        score += m.attackPower * 0.5
    }

    // Collateral Damage: self-damage, HP matters
    if (ctx.collateralDamage) {
        score += m.health * 0.5
    }

    // Born Again: low HP less punished (everyone resurrects)
    if (ctx.bornAgain && m.health <= 3) {
        score += 2.0
    }

    // Dragon Breath: ranged/magic inflict Burning
    if (ctx.dragonBreath && (m.attackType == "ranged" || m.attackType == "magic")) {
        score += 2.0
    }

    // ── Counter-scoring: anticipate enemy strategies ──
    // Both players face the same rulesets, so we can predict what the enemy is likely to play
    // and boost abilities that specifically counter those strategies.
    if (!ctx.backToBasics) {
        // Predict enemy attack type emphasis
        val enemyHeavyMelee = ctx.enemyOnlyMelee || (
            !ctx.enemyNoMelee &&
                (ctx.meleeFromAnyPosition || ctx.whatDoesntKillYou || ctx.stampede)
            )
        val enemyHeavyMagic = ctx.enemyOnlyMagic || (
            !ctx.enemyNoMagic &&
                ((ctx.armoredUp && !ctx.weakMagic) || ctx.unprotected)
            )
        val enemyHeavyRanged = ctx.enemyOnlyRanged || (!ctx.enemyNoRanged && ctx.rangedFromPos1)

        // Counter enemy melee emphasis
        if (enemyHeavyMelee) {
            if ("Thorns" in m.abilities && !ctx.deflectionShield) score += 4.0
            if ("Demoralize" in m.abilities) score += 3.0
            if ("Shield" in m.abilities) score += 3.0
            if ("Dodge" in m.abilities && !ctx.aimTrue) score += 2.0
            if ("Blind" in m.abilities && !ctx.aimTrue) score += 2.0
            if ("Enfeeble" in m.abilities) score += 2.0
        }

        // Counter enemy magic emphasis
        if (enemyHeavyMagic) {
            if ("Void" in m.abilities) score += 4.0
            if ("Silence" in m.abilities) score += 3.0
            if ("Magic Reflect" in m.abilities && !ctx.deflectionShield) score += 3.0
            if ("Phase" in m.abilities) score += 2.0
            if ("Void Armor" in m.abilities) score += 2.0
        }

        // Counter enemy ranged emphasis
        if (enemyHeavyRanged) {
            if ("Headwinds" in m.abilities) score += 3.0
            if ("Return Fire" in m.abilities && !ctx.deflectionShield) score += 3.0
            if ("Shield" in m.abilities) score += 2.0
        }

        // Disruptive abilities (counter any enemy strategy)
        if ("Affliction" in m.abilities && !ctx.healedOut) score += 3.0
        if ("Dispel" in m.abilities) score += if (ctx.bloodMoon) 5.0 else 2.0
        if ("Halving" in m.abilities) score += 3.0
        if ("Shatter" in m.abilities && !ctx.unprotected) score += if (ctx.armoredUp) 4.0 else 2.0
        if ("Cripple" in m.abilities) score += 1.5
        if ("Enfeeble" in m.abilities && !ctx.enemyNoMelee && !enemyHeavyMelee) score += 1.0
        if ("Impede" in m.abilities && !ctx.reverseSpeed) score += 1.5
        if ("Giant Killer" in m.abilities) score += 2.0
        if ("Oppress" in m.abilities) score += 1.0
        if ("Life Leech" in m.abilities && !ctx.healedOut) score += 2.0
        if ("Scavenger" in m.abilities) score += 1.5
    }

    return score
}

private fun scoreSummoner(
    s: SummonerEntry,
    availableMonsters: List<CardEntry>,
    ctx: RulesetContext,
): Double {
    if (ctx.silencedSummoners) return -s.mana.toDouble()

    var score = 0.0
    val total = availableMonsters.size.coerceAtLeast(1).toDouble()
    val meleeRatio = availableMonsters.count { it.attackType == "melee" } / total
    val rangedRatio = availableMonsters.count { it.attackType == "ranged" } / total
    val magicRatio = availableMonsters.count { it.attackType == "magic" } / total

    // Summoner buffs have dual meaning:
    //   Positive values (e.g., attack=+1) buff YOUR team's attack type
    //   Negative values (e.g., attack=-1) debuff ENEMY team's attack type
    // Buff value depends on your pool; debuff value depends on whether enemy uses that type

    // Positive buffs: weighted by OUR pool composition
    if (s.buffs.attack > 0) score += s.buffs.attack * 3.0 * meleeRatio
    if (s.buffs.ranged > 0) score += s.buffs.ranged * 3.0 * rangedRatio
    if (s.buffs.magic > 0) score += s.buffs.magic * 3.0 * magicRatio

    // Negative buffs (enemy debuffs): weighted by whether enemy can use that type
    if (s.buffs.attack < 0 && !ctx.enemyNoMelee) score += s.buffs.attack * -3.0
    if (s.buffs.ranged < 0 && !ctx.enemyNoRanged) score += s.buffs.ranged * -3.0
    if (s.buffs.magic < 0 && !ctx.enemyNoMagic) score += s.buffs.magic * -3.0

    // Defensive buffs (positive = helps us, negative = hurts enemy)
    if (!ctx.unprotected) {
        if (s.buffs.armor > 0) {
            score += s.buffs.armor * 2.0
        } else if (s.buffs.armor < 0) {
            score += s.buffs.armor * -2.0 // enemy loses armor
        }
    }
    if (s.buffs.health > 0) {
        score += s.buffs.health * 2.0
    } else if (s.buffs.health < 0) {
        score += s.buffs.health * -2.0
    }
    if (s.buffs.speed > 0) {
        score += s.buffs.speed * if (ctx.reverseSpeed) -1.0 else 1.0
    } else if (s.buffs.speed < 0) {
        score += s.buffs.speed * if (ctx.reverseSpeed) 1.0 else -1.0
    }

    // Ruleset interactions
    if (ctx.avoidMelee && !ctx.deflectionShield && s.buffs.attack > 0) score -= s.buffs.attack * 2.0 * meleeRatio
    if (ctx.avoidRanged && !ctx.deflectionShield && s.buffs.ranged > 0) score -= s.buffs.ranged * 2.0 * rangedRatio
    if (ctx.avoidMagic && !ctx.deflectionShield && s.buffs.magic > 0) score -= s.buffs.magic * 2.0 * magicRatio
    if (ctx.preferMagic) {
        if (s.buffs.attack > 0) score -= s.buffs.attack * 1.5 * meleeRatio
        if (s.buffs.ranged > 0) score -= s.buffs.ranged * 1.5 * rangedRatio
        if (s.buffs.magic > 0) score += s.buffs.magic * 1.5 * magicRatio
    }
    if (ctx.arcaneDampening && s.buffs.magic > 0) score -= s.buffs.magic * 1.5 * magicRatio
    if (ctx.armoredUp && !ctx.weakMagic && s.buffs.magic > 0) score += s.buffs.magic * 1.5 * magicRatio
    if (ctx.whatDoesntKillYou && s.buffs.attack > 0) score += s.buffs.attack * 1.5 * meleeRatio

    // Enemy counter bonuses for summoner debuffs based on predicted enemy meta
    val enemyHeavyMelee = ctx.enemyOnlyMelee || (
        !ctx.enemyNoMelee &&
            (ctx.meleeFromAnyPosition || ctx.whatDoesntKillYou || ctx.stampede)
        )
    val enemyHeavyMagic = ctx.enemyOnlyMagic || (
        !ctx.enemyNoMagic &&
            ((ctx.armoredUp && !ctx.weakMagic) || ctx.unprotected)
        )
    val enemyHeavyRanged = ctx.enemyOnlyRanged || (!ctx.enemyNoRanged && ctx.rangedFromPos1)
    if (enemyHeavyMelee && s.buffs.attack < 0) score += s.buffs.attack * -2.0
    if (enemyHeavyMagic && s.buffs.magic < 0) score += s.buffs.magic * -2.0
    if (enemyHeavyRanged && s.buffs.ranged < 0) score += s.buffs.ranged * -2.0

    // Summoner abilities
    if (!ctx.backToBasics) {
        for (ability in s.buffs.abilities) {
            score += when (ability) {
                "Conscript" -> 5.0
                "Resurrect" -> if (ctx.deathHasNoPower) 0.0 else 4.0
                "Blast" -> if (ctx.deflectionShield) 0.0 else 3.0
                else -> 1.0
            }
        }
    }

    // Prefer cheaper summoners
    score -= s.mana * 0.3

    return score
}

// ── Scored team building ──

private fun buildScoredTeam(
    monsters: List<CardEntry>,
    remainingMana: Int,
    maxMonsters: Int,
    gladLimit: Int,
    ctx: RulesetContext,
): Pair<List<CardEntry>, Double> {
    if (monsters.isEmpty()) return emptyList<CardEntry>() to 0.0

    val tankCandidates = monsters
        .filter { it.mana <= remainingMana }
        .map { it to scoreMonster(it, Position.TANK, ctx) }
        .sortedByDescending { it.second }

    var bestTeam = emptyList<CardEntry>()
    var bestScore = Double.NEGATIVE_INFINITY
    val tankTrials = minOf(3, tankCandidates.size)

    for (i in 0 until tankTrials) {
        val (tank, tankScore) = tankCandidates[i]
        val manaAfterTank = remainingMana - tank.mana
        val gladUsedByTank = if (tank.isGladiator) 1 else 0

        // Score backline candidates by score-per-mana for efficient filling
        val backlineCandidates = monsters
            .filter { it.detailId != tank.detailId && it.mana <= manaAfterTank }
            .filter { !(it.isGladiator && gladUsedByTank >= gladLimit) }
            .map { it to scoreMonster(it, Position.BACKLINE, ctx) }
            .sortedByDescending { it.second / it.first.mana.coerceAtLeast(1) }

        val team = mutableListOf(tank)
        var manaLeft = manaAfterTank
        var gladCount = gladUsedByTank
        val usedIds = mutableSetOf(tank.detailId)
        var teamScore = tankScore

        for ((monster, monsterScore) in backlineCandidates) {
            if (team.size >= maxMonsters) break
            if (monster.detailId in usedIds) continue
            if (monster.mana > manaLeft) continue
            if (monster.isGladiator && gladCount >= gladLimit) continue
            team.add(monster)
            teamScore += monsterScore
            manaLeft -= monster.mana
            usedIds.add(monster.detailId)
            if (monster.isGladiator) gladCount++
        }

        // Second pass: fill remaining slots by absolute score (not per-mana)
        if (team.size < maxMonsters && manaLeft > 0) {
            val remaining = monsters
                .filter { it.detailId !in usedIds && it.mana <= manaLeft }
                .filter { !(it.isGladiator && gladCount >= gladLimit) }
                .map { it to scoreMonster(it, Position.BACKLINE, ctx) }
                .sortedByDescending { it.second }
            for ((m, mScore) in remaining) {
                if (team.size >= maxMonsters) break
                if (m.mana > manaLeft) continue
                team.add(m)
                teamScore += mScore
                manaLeft -= m.mana
                usedIds.add(m.detailId)
                if (m.isGladiator) gladCount++
            }
        }

        // Super Sneak: move second-tankiest monster to last position
        if (ctx.superSneak && team.size >= 3) {
            val backline = team.subList(1, team.size)
            val tankiest = backline.maxByOrNull { scoreMonster(it, Position.TANK, ctx) }
            if (tankiest != null) {
                team.remove(tankiest)
                team.add(tankiest)
            }
        }

        if (team.isNotEmpty() && teamScore > bestScore) {
            bestTeam = team.toList()
            bestScore = teamScore
        }
    }

    return bestTeam to bestScore
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

    // Gladiator handling — keep gladiators only if some summoner has Conscript or Are You Not Entertained
    val hasConscript = filteredSummoners.any { "Conscript" in it.buffs.abilities }
    val areYouNotEntertained = "Are You Not Entertained" in rulesets
    if (!hasConscript && !areYouNotEntertained) {
        filteredMonsters = filteredMonsters.filter { !it.isGladiator }
    }

    // Deduplicate by detailId
    val dedupSummoners = filteredSummoners.distinctBy { it.detailId }
    val dedupMonsters = filteredMonsters.distinctBy { it.detailId }

    val ctx = buildRulesetContext(rulesets)

    // Score each summoner + team combination, pick the best
    var bestResult: TeamSelection? = null
    var bestTotalScore = Double.NEGATIVE_INFINITY

    for (summoner in dedupSummoners) {
        val remaining = manaCap - summoner.mana
        if (remaining <= 0) continue
        var gladLimit = if ("Conscript" in summoner.buffs.abilities) 1 else 0
        if (areYouNotEntertained) gladLimit++

        if (summoner.color == "Gold") {
            // Dragon: try each ally color, pick the best scoring team
            val allyCandidates = COLOR_TO_SPLINTER.keys.filter {
                it !in inactiveColors && COLOR_TO_SPLINTER[it] !in inactiveColors && it != "Gold" && it != "Gray"
            }
            for (ally in allyCandidates) {
                val validColors = mutableSetOf(ally, "Gray")
                if ("Taking Sides" in rulesets) validColors.remove("Gray")
                if ("Shades of Gray" in rulesets) {
                    validColors.clear()
                    validColors.add("Gray")
                }
                val colorPool = dedupMonsters.filter { it.color in validColors }
                val (team, teamScore) = buildScoredTeam(colorPool, remaining, maxMonsters, gladLimit, ctx)
                if (team.isEmpty()) continue
                val sumScore = scoreSummoner(summoner, colorPool, ctx)
                val totalScore = sumScore + teamScore
                if (totalScore > bestTotalScore) {
                    bestTotalScore = totalScore
                    val monsterLookup = team.associateBy { it.uid }
                    val allyColor = determineDragonAllyColor(summoner.color, team.map { it.uid }, monsterLookup)
                    bestResult = TeamSelection(summoner.uid, team.map { it.uid }, allyColor)
                }
            }
        } else {
            val validColors = mutableSetOf(summoner.color, "Gray")
            if ("Taking Sides" in rulesets) validColors.remove("Gray")
            if ("Shades of Gray" in rulesets) {
                validColors.clear()
                validColors.add("Gray")
            }
            val colorPool = dedupMonsters.filter { it.color in validColors }
            val (team, teamScore) = buildScoredTeam(colorPool, remaining, maxMonsters, gladLimit, ctx)
            if (team.isEmpty()) continue
            val sumScore = scoreSummoner(summoner, colorPool, ctx)
            val totalScore = sumScore + teamScore
            if (totalScore > bestTotalScore) {
                bestTotalScore = totalScore
                bestResult = TeamSelection(summoner.uid, team.map { it.uid }, null)
            }
        }
    }

    return bestResult
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
