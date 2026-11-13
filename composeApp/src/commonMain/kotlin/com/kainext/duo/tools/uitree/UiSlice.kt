package com.kainext.duo.tools.uitree

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The pruned screen: the nodes worth showing a model, plus how much was dropped to get there.
 *
 * [truncated] and [totalVisited] are not bookkeeping trivia — they are part of the answer. When
 * [truncated] is true the model is looking at a partial view (a long list was cut at the node
 * cap), and it must know that before concluding "there is no Settings entry on this screen".
 * Without that flag a truncated slice reads exactly like a complete one, and the model silently
 * reports missing UI as absent.
 */
@Serializable
data class UiSlice(
    val nodes: List<UiNode>,
    val truncated: Boolean = false,
    val totalVisited: Int = 0,
) {
    val isEmpty: Boolean get() = nodes.isEmpty()

    /**
     * JSON form, for tool results that get fed back into the conversation and for anything that
     * needs to round-trip (tests, recordings, an HTTP bridge).
     *
     * Uses `encodeDefaults = false` and `explicitNulls = false` so that flags which are `false`
     * and fields which are `null` are simply absent. For a node like a plain label that turns
     * `{"i":3,"text":"Settings","bounds":"…","cx":1,"cy":2,"clickable":false,"scrollable":false,
     * "editable":false,"checked":null,"enabled":true}` into `{"i":3,"text":"Settings",…}`, which
     * is most of the node's size. The model reads absent as false/null without being told, and
     * the tool schema documents it once instead of on every node.
     *
     * Prefer this over [toPromptText] when the consumer is a JSON parser (structured tool output,
     * a test asserting on fields). Prefer [toPromptText] when the consumer is the model's eyes.
     */
    fun toCompactJson(): String = CompactUiJson.encodeToString(this)

    /**
     * Line-per-node text form, cheaper than [toCompactJson] and easier for a model to scan.
     *
     * Why it exists: JSON spends 4-6 tokens per node on punctuation, braces and repeated keys
     * (`"i":`, `"cx":`, `"clickable":`), and the pruner already fixed the field order — so
     * position carries the meaning and the keys are pure overhead. One line per node also lets
     * the model count and index rows visually, which it does measurably better than inside a
     * brace soup.
     *
     * Trade-off: this form is *lossy* for a machine (no parser, no round trip) and it drops
     * bounds in favour of the centre point, because an actor needs the centre and only needs the
     * size when it is about to swipe. Use [toCompactJson] when something has to parse the result;
     * use this when the only consumer is the model.
     */
    fun toPromptText(): String {
        if (nodes.isEmpty()) return "(no actionable nodes on screen)"
        return buildString {
            append("#screen ").append(nodes.size).append(" nodes")
            if (truncated) append(" (truncated, ").append(totalVisited).append(" visited)")
            append('\n')
            for (node in nodes) {
                append('#').append(node.i)
                append(" [").append(node.cx).append(',').append(node.cy).append(']')
                node.cls?.let { append(' ').append(it) }
                val label = node.text ?: node.desc
                if (label != null) {
                    append(" \"").append(escape(label)).append('"')
                } else if (node.editable) {
                    // An empty text field still has to be visible as a field, otherwise the
                    // model cannot tell where typing will land.
                    append(" \"\"")
                }
                if (node.text != null && node.desc != null) append(" desc=\"").append(escape(node.desc)).append('"')
                node.id?.let { append(" id=").append(it) }
                if (node.clickable) append(" clickable")
                if (node.scrollable) append(" scrollable")
                if (node.editable) append(" editable")
                when (node.checked) {
                    true -> append(" checked")
                    false -> append(" unchecked")
                    null -> Unit
                }
                if (!node.enabled) append(" disabled")
                append('\n')
            }
        }.trimEnd('\n')
    }

    /**
     * Rough token cost of [toCompactJson], at the usual ~4 characters per token for English and
     * JSON punctuation. This is an estimate on purpose: exact tokenisation is model-specific and
     * a tokenizer dependency in commonMain is not worth it. It exists so the pruner's caps
     * (node count, depth) can be justified and tuned with a number instead of a guess.
     */
    fun estimatedTokenCost(): Int = toCompactJson().length / 4

    /** Rough token cost of [toPromptText]; always <= [estimatedTokenCost] for the same slice. */
    fun estimatedPromptTokenCost(): Int = toPromptText().length / 4

    /**
     * Cheap content fingerprint used by the executor's `awaitUiStable` polling loop: two slices
     * with the same signature are considered "the same screen". It hashes node count plus every
     * label, which is what changes when a tap lands (a spinner disappears, a label flips to
     * "Saving…", a row is added). Positions are deliberately excluded — a ripple animation or a
     * list settling can move a node by a pixel without anything meaningful having changed, and
     * including bounds would make the loop wait out its full timeout on every tap.
     */
    fun signature(): Long {
        var hash = FNV_OFFSET_BASIS
        hash = mix(hash, nodes.size.toLong())
        for (node in nodes) {
            node.text?.let { hash = mixString(hash, it) }
            node.desc?.let { hash = mixString(hash, it) }
            hash = mix(hash, if (node.checked == true) 1L else 0L)
            hash = mix(hash, if (node.enabled) 1L else 0L)
        }
        return hash
    }

    /** The node with this index, or null when the slice has since been re-pruned and lost it. */
    fun nodeAt(index: Int): UiNode? = nodes.firstOrNull { it.i == index }

    /**
     * Resolves a human/LLM-supplied label to a node: exact match on text/desc/id first, then a
     * case-insensitive contains. Order matters — "OK" must not resolve to "OK, cancel all" just
     * because that node happened to come first in the tree.
     */
    fun matchByText(needle: String): UiNode? {
        val trimmed = needle.trim()
        if (trimmed.isEmpty()) return null
        return nodes.firstOrNull { it.matchesExactly(trimmed) }
            ?: nodes.firstOrNull { it.containsLabel(trimmed) }
    }

    /** Nodes that can be scrolled, used by [UiAction.Scroll] when the model omits an index. */
    fun scrollableNodes(): List<UiNode> = nodes.filter { it.scrollable }

    private fun escape(value: String): String {
        val flattened = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
        return if (flattened.length <= MAX_LABEL_CHARS) {
            flattened.replace("\"", "'")
        } else {
            flattened.take(MAX_LABEL_CHARS).replace("\"", "'") + "…"
        }
    }

    private fun mixString(hash: Long, value: String): Long {
        var h = hash
        for (ch in value) {
            h = (h xor ch.code.toLong()) * FNV_PRIME
        }
        return h
    }

    private fun mix(hash: Long, value: Long): Long = (hash xor value) * FNV_PRIME

    companion object {
        /**
         * `encodeDefaults = false` drops `false`/default values, `explicitNulls = false` drops
         * nulls. Both are the whole point of the compact form; see [toCompactJson].
         */
        val CompactUiJson: Json = Json {
            encodeDefaults = false
            explicitNulls = false
        }

        private const val MAX_LABEL_CHARS = 80
        private const val FNV_OFFSET_BASIS = -3750763034362895579L
        private const val FNV_PRIME = 1099511628211L

        fun empty(): UiSlice = UiSlice(nodes = emptyList(), truncated = false, totalVisited = 0)
    }
}
