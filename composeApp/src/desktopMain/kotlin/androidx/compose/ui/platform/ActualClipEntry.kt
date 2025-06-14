package androidx.compose.ui.platform // Must match the expect class's package

import androidx.compose.ui.text.AnnotatedString

/**
 * Actual implementation of [ClipEntry] for Desktop (JVM).
 *
 * The constructor is called from common code. This class holds the data.
 * The actual interaction with the system clipboard happens within the
 * `actual` implementation of [ClipboardManager.setClipEntry].
 */
actual class ClipEntry actual constructor(
    actual val annotatedString: AnnotatedString,
    actual val clipMetadata: ClipMetadata?
) {
    // No further JVM-specific implementation is needed within ClipEntry itself
    // for the purpose of construction and holding the annotatedString.
    // The `annotatedString` and `clipMetadata` are already declared as `actual val`
    // in the constructor, fulfilling the `expect class` requirements.
}
