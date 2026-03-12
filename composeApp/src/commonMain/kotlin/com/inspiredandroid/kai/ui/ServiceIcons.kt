package com.inspiredandroid.kai.ui

import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_service_anthropic
import kai.composeapp.generated.resources.ic_service_cerebras
import kai.composeapp.generated.resources.ic_service_deepseek
import kai.composeapp.generated.resources.ic_service_gemini
import kai.composeapp.generated.resources.ic_service_groqcloud
import kai.composeapp.generated.resources.ic_service_mistral
import kai.composeapp.generated.resources.ic_service_nvidia
import kai.composeapp.generated.resources.ic_service_ollamacloud
import kai.composeapp.generated.resources.ic_service_openai
import kai.composeapp.generated.resources.ic_service_openai_compatible
import kai.composeapp.generated.resources.ic_service_openrouter
import kai.composeapp.generated.resources.ic_service_xai
import org.jetbrains.compose.resources.DrawableResource

fun serviceIcon(serviceId: String): DrawableResource = when (serviceId) {
    "gemini" -> Res.drawable.ic_service_gemini
    "anthropic" -> Res.drawable.ic_service_anthropic
    "openai" -> Res.drawable.ic_service_openai
    "deepseek" -> Res.drawable.ic_service_deepseek
    "mistral" -> Res.drawable.ic_service_mistral
    "xai" -> Res.drawable.ic_service_xai
    "openrouter" -> Res.drawable.ic_service_openrouter
    "groqcloud" -> Res.drawable.ic_service_groqcloud
    "nvidia" -> Res.drawable.ic_service_nvidia
    "cerebras" -> Res.drawable.ic_service_cerebras
    "ollamacloud" -> Res.drawable.ic_service_ollamacloud
    "openai-compatible" -> Res.drawable.ic_service_openai_compatible
    else -> Res.drawable.ic_service_openai_compatible
}
