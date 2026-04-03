@file:Suppress("DEPRECATION")

package com.inspiredandroid.kai.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

val darkPurple = Color(0xFF6200EE)
val lightPurple = Color(0xff8063C5)
val gradientBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(darkPurple, lightPurple))

// Animated border gradient colors
val gradientPurple = Color(0xFF9C27B0)
val gradientViolet = Color(0xFF7C4DFF)
val gradientMagenta = Color(0xFFE040FB)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    onPrimary = Color(0xFF000000),
    surface = Color(0xFF1E1E1E),
    background = Color(0xFF121212),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
)

val LightColorScheme = lightColorScheme(
    primary = darkPurple,
    onPrimary = Color(0xFFFFFFFF),
    surface = Color(0xFFF2F2F2),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
)

@Composable
fun outlineTextFieldColors() = OutlinedTextFieldDefaults.colors()

@Composable
fun KaiOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        label = label,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        colors = outlineTextFieldColors(),
    )
}

@Composable
@Preview
fun Theme(
    colorScheme: ColorScheme,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorScheme,
    ) {
        content()
    }
}
