package com.inspiredandroid.kai.data

interface DataRepository {

    suspend fun fetchGroqModels()
    fun updateSelectedService(id: String)
    fun updateGroqModel(id: String)
    fun changeGroqApiKey(apiKey: String)
    suspend fun ask(question: String?): Result<Any>
    fun changeGeminiApiKey(apiKey: String)
    fun clearHistory()
    fun isUsingSharedKey(): Boolean
    fun updateGeminiModel(id: String)
}
