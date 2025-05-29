package com.inspiredandroid.kai.network

internal object NetworkConstants {
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    const val GEMINI_GENERATE_CONTENT_PATH = ":generateContent" // Path appended to model ID

    const val GROQ_BASE_URL = "https://api.groq.com/openai/v1/"
    const val GROQ_CHAT_COMPLETIONS_PATH = "chat/completions"
    const val GROQ_MODELS_PATH = "models"

    const val PROXY_BASE_URL = "https://proxy-api-amber.vercel.app/"
    const val PROXY_CHAT_PATH = "chat"
}
