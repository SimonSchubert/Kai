package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.logo
import kai.composeapp.generated.resources.welcome_message
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

private data class ExploreTopic(
    val title: String,
    val emoji: String,
    val color: Color,
)

private val topics = listOf(
    ExploreTopic("People", "\uD83D\uDC64", Color(0xFFE57373)),
    ExploreTopic("Sport", "\u26BD", Color(0xFF81C784)),
    ExploreTopic("Countries", "\uD83C\uDF0D", Color(0xFF64B5F6)),
    ExploreTopic("Technology", "\uD83D\uDCBB", Color(0xFFFFB74D)),
)

@Composable
internal fun EmptyState(modifier: Modifier, isUsingSharedKey: Boolean, onNavigateToExplore: (String) -> Unit = {}) {
    val isInspectionMode = LocalInspectionMode.current

    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isInspectionMode) {
            // Use static logo for previews/screenshots since Lottie loads asynchronously
            Image(
                modifier = Modifier.size(64.dp),
                imageVector = vectorResource(Res.drawable.logo),
                contentDescription = null,
            )
            Spacer(Modifier.height(12.dp))
        } else {
            val composition by rememberLottieComposition {
                LottieCompositionSpec.JsonString(
                    Res.readBytes("files/lottie_loading.json").decodeToString(),
                )
            }
            Image(
                modifier = Modifier.size(128.dp),
                painter = rememberLottiePainter(
                    composition = composition,
                    iterations = Compottie.IterateForever,
                    speed = 0.6f,
                ),
                contentDescription = null,
            )
        }
        Text(
            text = stringResource(Res.string.welcome_message),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (isUsingSharedKey) {
            val linkColor = MaterialTheme.colorScheme.primary
            val annotatedString = remember {
                buildAnnotatedString {
                    append("By using the service, you agree to the ")
                    withLink(LinkAnnotation.Url(url = "https://schubert-simon.de/privacy/kai.txt")) {
                        withStyle(style = SpanStyle(color = linkColor)) {
                            append("Privacy Policy")
                        }
                    }
                }
            }
            Text(
                annotatedString,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(24.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(topics) { topic ->
                TopicCard(
                    topic = topic,
                    onClick = { onNavigateToExplore(topic.title) },
                )
            }
        }
    }
}

@Composable
private fun TopicCard(topic: ExploreTopic, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(topic.color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(topic.color.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = topic.emoji,
                fontSize = 24.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = topic.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}
