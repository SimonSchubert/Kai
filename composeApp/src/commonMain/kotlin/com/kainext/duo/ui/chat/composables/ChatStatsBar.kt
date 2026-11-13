package com.kainext.duo.ui.chat.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kainext.duo.data.TokenUsage
import com.kainext.duo.formatContextWindow
import com.kainext.duo.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.chat_stats_cache_hit
import kai.composeapp.generated.resources.chat_stats_context
import kai.composeapp.generated.resources.chat_stats_no_cache
import kai.composeapp.generated.resources.chat_stats_rounds
import kai.composeapp.generated.resources.chat_stats_show_overlay
import kai.composeapp.generated.resources.chat_stats_steps
import kai.composeapp.generated.resources.chat_stats_tokens
import kai.composeapp.generated.resources.chat_stats_tokens_per_second
import org.jetbrains.compose.resources.stringResource

/**
 * The one-line usage readout under the composer: cumulative tokens, assistant rounds, tool steps,
 * output speed, prompt-cache hit rate and how much of the model's context window is in use.
 *
 * Every value is optional. A provider that reports no usage simply hides its cells instead of
 * showing a zero, so the strip never claims data the provider didn't send.
 */
@Composable
internal fun ChatStatsBar(
    totalTokens: Int,
    rounds: Int,
    steps: Int,
    tokenUsage: TokenUsage?,
    contextWindow: Long?,
    modifier: Modifier = Modifier,
    /** When non-null, a button opens the floating control window. Null hides it entirely. */
    onShowOverlay: (() -> Unit)? = null,
) {
    // Keep the strip alive while the conversation is empty when a context window is known:
    // "0% of 128k" is still useful, and the row would otherwise pop in and out of existence.
    if (totalTokens <= 0 && rounds <= 0 && onShowOverlay == null &&
        (contextWindow == null || contextWindow <= 0)
    ) {
        return
    }

    val separator = " · "
    // Strings are resolved first and the list built from them: `stringResource` is @Composable and
    // must not be called from inside a non-composable lambda.
    val roundsText = if (rounds > 0) stringResource(Res.string.chat_stats_rounds, rounds) else null
    val stepsText = if (steps > 0) stringResource(Res.string.chat_stats_steps, steps) else null
    val tokensText = if (totalTokens > 0) {
        stringResource(Res.string.chat_stats_tokens, formatTokenCount(totalTokens))
    } else {
        null
    }
    val speed = tokenUsage?.outputTokensPerSecond
    val speedText = if (speed != null) {
        stringResource(Res.string.chat_stats_tokens_per_second, speed.toInt().toString())
    } else {
        null
    }
    val parts = listOfNotNull(roundsText, stepsText, tokensText, speedText)

    val cacheText = when {
        tokenUsage == null -> null
        tokenUsage.cacheHitFraction == null -> stringResource(Res.string.chat_stats_no_cache)
        else -> stringResource(
            Res.string.chat_stats_cache_hit,
            ((tokenUsage.cacheHitFraction ?: 0f) * 100).toInt().toString(),
        )
    }

    val contextText = if (contextWindow != null && contextWindow > 0) {
        val used = tokenUsage?.totalTokens ?: 0
        val percent = (used * 100.0 / contextWindow).coerceIn(0.0, 100.0)
        val shown = if (percent > 0 && percent < 1) "<1" else percent.toInt().toString()
        stringResource(Res.string.chat_stats_context, shown, formatContextWindow(contextWindow))
    } else {
        null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(
            text = parts.joinToString(separator),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        cacheText?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        contextText?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        // Sits at the end of the strip so it never pushes the numbers around as it appears.
        onShowOverlay?.let { onClick ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.chat_stats_show_overlay),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .handCursor()
                    .clickable { onClick() },
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}

/** 1_234 -> "1.2k", 1_234_567 -> "1.2M". Keeps the strip to one line on a phone. */
internal fun formatTokenCount(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${roundToTenths(tokens, 100_000)}M"
    tokens >= 1_000 -> "${roundToTenths(tokens, 100)}k"
    else -> tokens.toString()
}

/** Formats [tokens] / [unit] rounded to one decimal, so 1999 -> "2.0" rather than "1.9". */
private fun roundToTenths(tokens: Int, unit: Int): String {
    val tenths = (tokens.toLong() * 10 + unit / 2) / unit
    return "${tenths / 10}.${tenths % 10}"
}
