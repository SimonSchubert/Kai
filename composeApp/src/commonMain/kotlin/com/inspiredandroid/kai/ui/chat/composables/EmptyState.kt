package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.info_using_shared_key
import kai.composeapp.generated.resources.logo
import kai.composeapp.generated.resources.settings_sign_in_copy_api_key_from
import kai.composeapp.generated.resources.welcome_message
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun EmptyState(modifier: Modifier, isUsingSharedKey: Boolean) {
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
    }
}
