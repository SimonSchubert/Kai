package com.inspiredandroid.kai.ui.dynamicui

import kotlin.time.Clock

/**
 * Temporary debug collector for kai-ui parse errors.
 * Appends to a file via platform callback. Remove before release.
 */
object ParseErrorCollector {

    private var onLog: ((String) -> Unit)? = null

    fun init(onLog: (String) -> Unit) {
        this.onLog = onLog
    }

    fun log(error: String, rawJson: String) {
        val timestamp = Clock.System.now().toString()
        val entry = "$timestamp | $error | ${rawJson.take(500)}"
        onLog?.invoke(entry)
    }
}
