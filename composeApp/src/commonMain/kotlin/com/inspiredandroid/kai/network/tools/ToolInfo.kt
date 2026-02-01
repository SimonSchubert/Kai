package com.inspiredandroid.kai.network.tools

import androidx.compose.runtime.Immutable

/**
 * Represents tool information for display in settings.
 * This is decoupled from the Tool interface to allow showing tools
 * even on platforms that don't implement them.
 */
@Immutable
data class ToolInfo(
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean = true,
)
