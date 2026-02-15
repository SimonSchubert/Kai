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

                val response = dataRepository.askExplore(prompt)
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
        val json = Json { ignoreUnknownKeys = true }
        val array = json.parseToJsonElement(jsonText).jsonArray
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
 * Also sanitizes unescaped quotes within JSON string values that LLMs sometimes produce.
 */
internal fun extractJson(response: String): String {
    // Try to extract content between code fences first
    val fenceRegex = Regex("""```(?:json)?\s*\n?([\s\S]*?)\n?\s*```""")
    val match = fenceRegex.find(response)
    val raw = if (match != null) {
        match.groupValues[1].trim()
    } else {
        // Fallback: find the first [ or { and match to the last ] or }
        val start = response.indexOfFirst { it == '[' || it == '{' }
        val end = response.indexOfLast { it == ']' || it == '}' }
        if (start != -1 && end != -1 && end > start) {
            response.substring(start, end + 1)
        } else {
            response.trim()
        }
    }
    return sanitizeJsonQuotes(raw)
}

/**
 * Fixes unescaped double quotes inside JSON string values.
 * LLMs sometimes produce: "description": "a "raider" must tag"
 * This walks the string character-by-character to detect and escape inner quotes.
 */
private fun sanitizeJsonQuotes(json: String): String {
    val sb = StringBuilder(json.length)
    var i = 0
    while (i < json.length) {
        val c = json[i]
        if (c == '"') {
            // Found opening quote of a JSON string value – scan for the matching close
            sb.append('"')
            i++
            // Collect string contents, fixing unescaped quotes
            while (i < json.length) {
                val sc = json[i]
                if (sc == '\\') {
                    // Already escaped sequence – pass through both chars
                    sb.append(sc)
                    i++
                    if (i < json.length) {
                        sb.append(json[i])
                        i++
                    }
                } else if (sc == '"') {
                    // Is this the real closing quote or an unescaped inner quote?
                    // Look ahead: a real closing quote is followed by , ] } : or whitespace then one of those
                    val after = json.substring(i + 1).trimStart()
                    if (after.isEmpty() || after[0] in ",]}:") {
                        // Real closing quote
                        sb.append('"')
                        i++
                        break
                    } else {
                        // Unescaped inner quote – escape it
                        sb.append("\\\"")
                        i++
                    }
                } else {
                    sb.append(sc)
                    i++
                }
            }
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}
