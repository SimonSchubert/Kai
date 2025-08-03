package com.inspiredandroid.kai.data

import io.github.vinceglb.filekit.PlatformFile

interface DataRepository {

    suspend fun fetchGroqModels()
    fun updateSelectedService(id: String)
    fun updateGroqModel(id: String)
    fun changeGroqApiKey(apiKey: String)
    suspend fun ask(question: String?, file: PlatformFile?)
    fun changeGeminiApiKey(apiKey: String)
    fun clearHistory()
    fun isUsingSharedKey(): Boolean
    fun updateGeminiModel(id: String)
    fun currentService(): String
}
