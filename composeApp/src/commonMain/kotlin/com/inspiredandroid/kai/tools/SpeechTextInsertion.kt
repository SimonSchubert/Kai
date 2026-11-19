package com.inspiredandroid.kai.tools

/**
 * Appends recognized speech to the current value without overwriting text already
 * entered in a generated field. The destination is selected by the caller.
 */
internal fun appendSpeechTranscript(current: String, transcript: String): String {
    val recognizedText = transcript.trim()
    if (recognizedText.isEmpty()) return current
    if (current.isEmpty()) return recognizedText

    val separator = if (current.last().isWhitespace()) "" else " "
    return current + separator + recognizedText
}

/**
 * Automatic submission is permitted only when explicitly enabled and there is no
 * existing draft or attachment that could be sent unintentionally.
 */
internal fun shouldAutoSendSpeechTranscript(
    enabled: Boolean,
    currentDraft: String,
    hasAttachments: Boolean,
    isLoading: Boolean,
): Boolean = enabled && currentDraft.isBlank() && !hasAttachments && !isLoading
