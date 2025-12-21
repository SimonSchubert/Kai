package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.info_using_shared_key
import kai.composeapp.generated.resources.welcome_message
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EmptyState(modifier: Modifier, isUsingSharedKey: Boolean) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
        Text(
            text = stringResource(Res.string.welcome_message),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (isUsingSharedKey) {
            Text(
                text = stringResource(Res.string.info_using_shared_key),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
