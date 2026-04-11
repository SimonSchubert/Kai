package com.inspiredandroid.kai.splinterlands

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks in the contract of [buildLlmPrompt]. The Splinterlands LLM picker builds a
 * system prompt + user message pair from a match description; these tests assert
 * that the structural pieces survive refactors.
 */
class SplinterlandsTeamPickerPromptTest {

    private fun summoner(
        uid: String = "s-placeholder",
        name: String = "Summoner",
        color: String = "Red",
        splinter: String = "Fire",
        mana: Int = 3,
        buffs: SummonerBuffs = SummonerBuffs(),
    ) = SummonerEntry(
        uid = uid,
        detailId = 0,
        color = color,
        splinter = splinter,
        mana = mana,
        rarity = "Common",
        attackType = "None",
        attackPower = 0,
        speed = 0,
        armor = 0,
        health = 0,
        buffs = buffs,
        name = name,
    )

    private fun card(
        uid: String = "c-placeholder",
        name: String = "Card",
        color: String = "Red",
        splinter: String = "Fire",
        mana: Int = 3,
        attackType: String = "Melee",
        attackPower: Int = 2,
        speed: Int = 2,
        armor: Int = 0,
        health: Int = 5,
        abilities: List<String> = emptyList(),
        isGladiator: Boolean = false,
    ) = CardEntry(
        uid = uid,
        detailId = 0,
        color = color,
        splinter = splinter,
        mana = mana,
        rarity = "Common",
        attackType = attackType,
        attackPower = attackPower,
        speed = speed,
        armor = armor,
        health = health,
        abilities = abilities,
        isGladiator = isGladiator,
        name = name,
    )

    private fun matchInfo(manaCap: Int = 20, ruleset: String = "Standard"): JsonObject = buildJsonObject {
        put("mana_cap", manaCap)
        put("ruleset", ruleset)
    }

    @Test
    fun `system prompt interpolates mana_cap and max_monsters`() {
        val result = buildLlmPrompt(
            summoners = listOf(summoner(uid = "s1", name = "Pyre")),
            monsters = listOf(card(uid = "c1", name = "Fire Demon")),
            matchInfo = matchInfo(manaCap = 27),
            maxMonsters = 6,
        )
        assertTrue("27" in result.systemPrompt, "mana_cap should be interpolated")
        assertTrue("6" in result.systemPrompt, "max_monsters should be interpolated")
        assertFalse("{mana_cap}" in result.systemPrompt, "placeholder should be replaced")
        assertFalse("{max_monsters}" in result.systemPrompt, "placeholder should be replaced")
    }

    @Test
    fun `user message numbers summoners S1-Sn and monsters M1-Mn`() {
        val result = buildLlmPrompt(
            summoners = listOf(
                summoner(uid = "s1", name = "Pyre"),
                summoner(uid = "s2", name = "Tarsa"),
            ),
            monsters = listOf(
                card(uid = "c1", name = "Fire Demon"),
                card(uid = "c2", name = "Goblin Shaman"),
                card(uid = "c3", name = "Serpentine Spy"),
            ),
            matchInfo = matchInfo(),
            maxMonsters = 6,
        )
        assertTrue("S1: Pyre" in result.userMessage)
        assertTrue("S2: Tarsa" in result.userMessage)
        assertTrue("M1: Fire Demon" in result.userMessage)
        assertTrue("M2: Goblin Shaman" in result.userMessage)
        assertTrue("M3: Serpentine Spy" in result.userMessage)
    }

    @Test
    fun `idMap maps numbered ids back to uids`() {
        val result = buildLlmPrompt(
            summoners = listOf(summoner(uid = "summoner-uid-1", name = "Pyre")),
            monsters = listOf(card(uid = "card-uid-1", name = "Fire Demon")),
            matchInfo = matchInfo(),
            maxMonsters = 6,
        )
        assertEquals("summoner-uid-1", result.idMap[1])
        // Monsters use 1000+index in the id map (see SplinterlandsTeamPicker.kt).
        assertEquals("card-uid-1", result.idMap[1001])
    }

    @Test
    fun `gladiator monsters are tagged GLAD in the user message`() {
        val result = buildLlmPrompt(
            summoners = listOf(summoner(uid = "s1", name = "Pyre")),
            monsters = listOf(
                card(uid = "c1", name = "Normal Card"),
                card(uid = "c2", name = "Glad Card", isGladiator = true),
            ),
            matchInfo = matchInfo(),
            maxMonsters = 6,
        )
        assertFalse("M1: Normal Card" in result.userMessage && "[GLAD]" in result.userMessage.substringAfter("Normal Card").substringBefore("M2:"))
        assertTrue("Glad Card" in result.userMessage)
        assertTrue("[GLAD]" in result.userMessage)
    }

    @Test
    fun `conscript summoners are tagged CONSCRIPT in the user message`() {
        val result = buildLlmPrompt(
            summoners = listOf(
                summoner(uid = "s1", name = "Regular"),
                summoner(uid = "s2", name = "Conscripted", buffs = SummonerBuffs(abilities = listOf("Conscript"))),
            ),
            monsters = listOf(card(uid = "c1", name = "Card")),
            matchInfo = matchInfo(),
            maxMonsters = 6,
        )
        assertTrue("S2: Conscripted" in result.userMessage)
        assertTrue("[CONSCRIPT]" in result.userMessage)
    }

    @Test
    fun `unplayable monsters are filtered out before numbering`() {
        // Red summoner can only play Red and Gray (Neutral). Blue card should be dropped.
        val result = buildLlmPrompt(
            summoners = listOf(summoner(uid = "s1", name = "Pyre", color = "Red")),
            monsters = listOf(
                card(uid = "c1", name = "Fire Card", color = "Red"),
                card(uid = "c2", name = "Water Card", color = "Blue"),
                card(uid = "c3", name = "Neutral Card", color = "Gray"),
            ),
            matchInfo = matchInfo(),
            maxMonsters = 6,
        )
        assertTrue("Fire Card" in result.userMessage)
        assertFalse("Water Card" in result.userMessage, "unplayable Blue card should be filtered")
        assertTrue("Neutral Card" in result.userMessage, "Gray/Neutral cards are always playable")
    }

    @Test
    fun `user message ends with the JSON response schema instruction`() {
        val result = buildLlmPrompt(
            summoners = listOf(summoner(uid = "s1", name = "Pyre")),
            monsters = listOf(card(uid = "c1", name = "Card")),
            matchInfo = matchInfo(),
            maxMonsters = 6,
        )
        assertTrue(result.userMessage.trimEnd().endsWith("\"mana_total\": <number>}"))
        assertTrue("Pick a strong team NOW" in result.userMessage)
    }

    @Test
    fun `custom rulesets appear in the system prompt`() {
        val result = buildLlmPrompt(
            summoners = listOf(summoner(uid = "s1", name = "Pyre")),
            monsters = listOf(card(uid = "c1", name = "Card")),
            matchInfo = matchInfo(ruleset = "Little League|Melee Mayhem"),
            maxMonsters = 6,
        )
        assertTrue("Little League" in result.systemPrompt)
        assertTrue("Melee Mayhem" in result.systemPrompt)
    }
}
