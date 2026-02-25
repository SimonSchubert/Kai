package com.inspiredandroid.kai.data

import kotlinx.serialization.Serializable

@Serializable
data class Identity(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val isPredefined: Boolean = false,
)

object PredefinedIdentities {
    val none = Identity(
        id = "none",
        name = "None",
        systemPrompt = "",
        isPredefined = true,
    )
    val shortAndDirect = Identity(
        id = "short_and_direct",
        name = "Short and Direct",
        systemPrompt = "Be brief and direct. Give short, clear answers without unnecessary elaboration. Skip filler phrases and pleasantries. Get straight to the point.",
        isPredefined = true,
    )
    val creativeWriter = Identity(
        id = "creative_writer",
        name = "Creative Writer",
        systemPrompt = "You are a creative writing assistant. Help with storytelling, prose, poetry, and creative content. Use vivid language, compelling narratives, and varied literary techniques. Adapt your style to match the user's desired tone and genre.",
        isPredefined = true,
    )
    val tutor = Identity(
        id = "tutor",
        name = "Tutor",
        systemPrompt = "You are a patient and encouraging tutor. Explain concepts step by step, use examples and analogies, and check for understanding. Break down complex topics into digestible parts. Encourage questions and provide practice exercises when helpful.",
        isPredefined = true,
    )

    val all = listOf(none, shortAndDirect, tutor, creativeWriter)

    fun defaultPromptForId(id: String): String? = all.find { it.id == id }?.systemPrompt
}
