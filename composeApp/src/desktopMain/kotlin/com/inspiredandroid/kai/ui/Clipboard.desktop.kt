package com.inspiredandroid.kai.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun clipEntryOfPlainText(text: String): ClipEntry = ClipEntry(StringSelection(text))
