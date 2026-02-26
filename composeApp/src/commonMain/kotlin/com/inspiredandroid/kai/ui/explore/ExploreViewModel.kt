package com.inspiredandroid.kai.ui.explore

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.SharedJson
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.getDeviceLanguage
import com.inspiredandroid.kai.network.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.CoroutineContext

@Immutable
data class ExploreItem(
    val title: String,
    val description: String,
    val imageUrl: String? = null,
)

@Immutable
data class ExploreUiState(
    val items: List<ExploreItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val topicTitle: String = "",
)

class ExploreViewModel(
    private val dataRepository: DataRepository,
    private val backgroundDispatcher: CoroutineContext = getBackgroundDispatcher(),
) : ViewModel() {

    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state

    fun loadTopic(topic: String) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null, topicTitle = topic) }

        viewModelScope.launch(backgroundDispatcher) {
            try {
                val prompt = """List 12 interesting $topic to explore.
Return ONLY a JSON array with objects having "title", "description" (one short sentence), and optionally "imageUrl" (a working public image URL, e.g. from Wikipedia/Wikimedia Commons).
Example: [{"title":"Example","description":"A short description","imageUrl":"https://upload.wikimedia.org/..."}]
Return ONLY the JSON array, no markdown, no explanation."""

                val response = dataRepository.askExplore(
                    prompt = prompt,
                    topic = topic,
                    language = getDeviceLanguage(),
                )
                val items = parseItemsFromJson(response)
                _state.update { it.copy(items = items, isLoading = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
            }
        }
    }

    fun retry() {
        val topic = _state.value.topicTitle
        if (topic.isNotEmpty()) {
            _state.update { it.copy(isLoading = false) }
            loadTopic(topic)
        }
    }

    private fun parseItemsFromJson(response: String): List<ExploreItem> {
        val jsonText = extractJson(response)
        val array = SharedJson.parseToJsonElement(jsonText).jsonArray
        return array.map { element ->
            val obj = element.jsonObject
            ExploreItem(
                title = obj["title"]?.jsonPrimitive?.content ?: "",
                description = obj["description"]?.jsonPrimitive?.content ?: "",
                imageUrl = obj["imageUrl"]?.jsonPrimitive?.content?.takeIf { it != "null" },
            )
        }
    }
}

/**
 * Extracts JSON content from an LLM response that may be wrapped in markdown code fences.
 */
internal fun extractJson(response: String): String {
    val fenceRegex = Regex("""```(?:json)?\s*\n?([\s\S]*?)\n?\s*```""")
    fenceRegex.find(response)?.let { return sanitizeJsonQuotes(it.groupValues[1].trim()) }

    val start = response.indexOfFirst { it == '[' || it == '{' }
    val end = response.indexOfLast { it == ']' || it == '}' }
    if (start != -1 && end > start) return sanitizeJsonQuotes(response.substring(start, end + 1))

    return response.trim()
}

/**
 * Fixes unescaped double quotes inside JSON string values that LLMs sometimes produce.
 */
private fun String.nextNonWhitespace(from: Int): Char? {
    var idx = from
    while (idx < length && this[idx].isWhitespace()) idx++
    return if (idx < length) this[idx] else null
}

private fun sanitizeJsonQuotes(json: String): String = buildString(json.length) {
    var i = 0
    while (i < json.length) {
        if (json[i] != '"') {
            append(json[i++])
            continue
        }
        append(json[i++]) // opening "
        while (i < json.length) {
            val c = json[i]
            if (c == '\\') {
                append(json[i++])
                if (i < json.length) append(json[i++])
                continue
            }
            if (c != '"') {
                append(json[i++])
                continue
            }
            val next = json.nextNonWhitespace(i + 1)
            if (next == null || next in ",]}:") {
                append(json[i++])
                break
            }
            append("\\\"")
            i++
        }
    }
}
