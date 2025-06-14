package androidx.compose.ui.platform // Must match the expect class's package

import androidx.compose.ui.text.AnnotatedString

/**
 * Actual implementation of [ClipEntry] for Android.
 *
 * The constructor is called from common code. This class holds the data.
 * The actual interaction with the Android clipboard service happens within the
 * `actual` implementation of [ClipboardManager.setClipEntry].
 */
actual class ClipEntry actual constructor(
    actual val annotatedString: AnnotatedString,
    actual val clipMetadata: ClipMetadata?
) {
    // No further Android-specific implementation is needed within ClipEntry itself
    // for the purpose of construction and holding the annotatedString.
    // The `annotatedString` and `clipMetadata` are already declared as `actual val`
    // in the constructor, fulfilling the `expect class` requirements.
}

// Additionally, ensure there's an actual implementation for ClipMetadata if it's also an expect class
// For now, assume ClipMetadata from androidx.compose.ui.platform is a concrete class or has its own actuals.
// Let's check the definition of ClipMetadata.
// From Compose: `class ClipMetadata(val timestamp: Long = System.currentTimeMillis())`
// It's a concrete class, not an expect class. So no actual needed for ClipMetadata itself.
