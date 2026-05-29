package com.inspiredandroid.kai.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.getAvailableTools
import com.inspiredandroid.kai.tools.BrowserTools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserUiState(
    val url: String = "https://www.google.com",
    val isLoading: Boolean = false,
    val aiPrompt: String = "",
    val aiResponse: String? = null,
    val isAiThinking: Boolean = false
)

class BrowserViewModel(
    private val dataRepository: DataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    fun onUrlChange(newUrl: String) {
        _uiState.update { it.copy(url = newUrl) }
    }

    fun onPromptChange(newPrompt: String) {
        _uiState.update { it.copy(aiPrompt = newPrompt) }
    }

    fun sendPrompt() {
        val prompt = _uiState.value.aiPrompt
        val currentUrl = _uiState.value.url
        if (prompt.isBlank()) return

        _uiState.update { it.copy(isAiThinking = true, aiResponse = null) }

        viewModelScope.launch {
            try {
                val tools = getAvailableTools()
                val getPageTextTool = tools.find { it.schema.name == BrowserTools.getPageTextToolInfo.id }

                val contextInfo = if (getPageTextTool != null) {
                    try {
                        val result = getPageTextTool.execute(emptyMap()) as? Map<*, *>
                        val text = result?.get("text")?.toString() ?: ""
                        "\n\nCurrent Page Content (Text):\n$text"
                    } catch (e: Exception) {
                        ""
                    }
                } else {
                    ""
                }

                val fullPrompt = "The user is currently viewing this URL: $currentUrl$contextInfo\n\nUser Command: $prompt\n\nYou can use your browser tools to interact with the page if needed to fulfill the request."

                // Use askWithTools so AI can actually call click_element, fill_form, etc.
                val response = dataRepository.askWithTools(fullPrompt)
                _uiState.update { it.copy(aiResponse = response, isAiThinking = false, aiPrompt = "") }
            } catch (e: Exception) {
                _uiState.update { it.copy(aiResponse = "Error: ${e.message}", isAiThinking = false) }
            }
        }
    }
}
