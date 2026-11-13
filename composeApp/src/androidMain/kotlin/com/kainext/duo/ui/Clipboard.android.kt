package com.kainext.duo.ui

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

internal actual fun clipEntryOfPlainText(text: String): ClipEntry = ClipEntry(ClipData.newPlainText("plain text", text))
