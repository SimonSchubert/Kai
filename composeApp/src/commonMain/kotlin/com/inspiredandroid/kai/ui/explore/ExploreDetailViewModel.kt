package com.inspiredandroid.kai.ui.explore

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.network.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Immutable
data class ExploreDetailUiState(
    val itemName: String = "",
    val markdownContent: String = "",
    val references: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class ExploreDetailViewModel(
    private val dataRepository: DataRepository,
    private val backgroundDispatcher: CoroutineContext = getBackgroundDispatcher(),
) : ViewModel() {

    private val _state = MutableStateFlow(ExploreDetailUiState())
    val state: StateFlow<ExploreDetailUiState> = _state

    fun loadItem(itemName: String) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null, itemName = itemName) }

        viewModelScope.launch(backgroundDispatcher) {
            try {
                val prompt = """Write an informative article about "$itemName" in markdown format.
Include relevant sections with headers. Be detailed and informative.
At the very end, add a line "---REFERENCES---" followed by a JSON array of 5 related topic names that the reader might want to explore next.
Example end: ---REFERENCES---["Topic A","Topic B","Topic C","Topic D","Topic E"]"""

                val response = dataRepository.askExplore(prompt)
                val (content, references) = parseDetailResponse(response)
                _state.update {
                    it.copy(
                        markdownContent = content,
                        references = references,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
            }
        }
    }

    fun retry() {
        val name = _state.value.itemName
        if (name.isNotEmpty()) {
            _state.update { it.copy(isLoading = false) }
            loadItem(name)
        }
    }

    private fun parseDetailResponse(response: String): Pair<String, List<String>> {
        val separator = "---REFERENCES---"
        val index = response.indexOf(separator)
        if (index == -1) return stripMarkdownFences(response) to emptyList()

        val content = stripMarkdownFences(response.substring(0, index).trim())
        val refsJson = extractJson(response.substring(index + separator.length))
        val references = try {
            val json = Json { ignoreUnknownKeys = true }
            json.parseToJsonElement(refsJson).jsonArray.map { it.jsonPrimitive.content }
        } catch (_: Exception) {
            emptyList()
        }
        return content to references
    }
}

/**
 * Strips outer markdown code fences that LLMs sometimes wrap around the entire response.
 * Handles ```markdown, ```md, or plain ``` wrapping.
 * Works even when only the opening or closing fence is present (e.g. when the response was split).
 */
private fun stripMarkdownFences(text: String): String {
    var result = text.trim()
    // Strip opening fence
    val openRegex = Regex("""^```(?:markdown|md)?\s*\n""")
    result = openRegex.replaceFirst(result, "")
    // Strip closing fence
    val closeRegex = Regex("""\n\s*```\s*$""")
    result = closeRegex.replaceFirst(result, "")
    return result
}
