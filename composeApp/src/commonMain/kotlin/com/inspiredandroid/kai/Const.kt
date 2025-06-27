package com.inspiredandroid.kai

class Value {
    companion object {
        const val DEFAULT_SERVICE = "gemini"
        const val DEFAULT_GROQ_MODEL = "llama-3.3-70b-versatile"
        const val SERVICE_GROQ = "groqcloud"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        const val SERVICE_GEMINI = "gemini"
    }
}

class Key {
    companion object {
        const val GROQ_API_KEY = "service_groq_api_key"
        const val GROQ_MODEL_ID = "service_groq_model_id"
        const val GEMINI_API_KEY = "service_gemini_api_key"
        const val GEMINI_MODEL_ID = "service_gemini_model_id"
        const val CURRENT_SERVICE_ID = "current_service_id"
    }
}
