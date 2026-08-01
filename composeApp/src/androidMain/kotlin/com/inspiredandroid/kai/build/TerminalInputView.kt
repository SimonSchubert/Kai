package com.inspiredandroid.kai.build

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.inspiredandroid.kai.build.terminal.TerminalKey
import com.inspiredandroid.kai.build.terminal.TerminalModifiers

/**
 * Invisible, focusable view over the terminal grid whose only job is IME
 * plumbing — it draws nothing, since the grid underneath is Compose.
 *
 * It asks the keyboard for a null input type, which is what makes a keyboard
 * stop composing text and deliver raw key events instead. That is the only way
 * Enter, Backspace and the arrows reach a terminal at all. Not every keyboard
 * honors it, so committed text and surrounding-text deletions are handled as
 * well; between the two paths every keyboard lands somewhere sensible.
 */
@SuppressLint("ViewConstructor")
internal class TerminalInputView(context: Context) : View(context) {

    var onKey: (TerminalKey, TerminalModifiers) -> Unit = { _, _ -> }
    var onText: (String, TerminalModifiers) -> Unit = { _, _ -> }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setWillNotDraw(true)
        // The view carries no content of its own, and leaving it in the
        // accessibility tree would hide the grid it sits on top of.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Take focus so a hardware keyboard works straight away; the soft
        // keyboard waits to be asked, rather than eating half the screen.
        requestFocus()
    }

    fun showKeyboard() {
        requestFocus()
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.showSoftInput(this, 0)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        showKeyboard()
        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        // DONE rather than NONE: NONE costs the newline key on some devices.
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN
        return TerminalInputConnection()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        handleKeyEvent(event) || super.onKeyDown(keyCode, event)

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean =
        handleKeyEvent(event) || super.onKeyMultiple(keyCode, repeatCount, event)

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        // Leave system keys alone: back still dismisses the keyboard and exits.
        if (event.keyCode in SystemKeyCodes) return false

        val modifiers = TerminalModifiers(
            ctrl = event.isCtrlPressed,
            alt = event.isAltPressed,
            shift = event.isShiftPressed,
        )

        val key = terminalKeyFor(event.keyCode)
        if (key != null) {
            onKey(key, modifiers)
            return true
        }

        // Resolve the character with ctrl/alt masked out so Shift still capitalizes.
        val unicode = event.getUnicodeChar(event.metaState and CharacterMetaMask)
        // Dead keys carry a flag instead of a character; wait for the combined one.
        if (unicode == 0 || unicode and KeyCharacterMap.COMBINING_ACCENT != 0) return false
        onText(unicode.toChar().toString(), modifiers)
        return true
    }

    private inner class TerminalInputConnection :
        BaseInputConnection(this@TerminalInputView, true) {

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (!text.isNullOrEmpty()) onText(text.toString(), TerminalModifiers.None)
            return true
        }

        /** A terminal has no composing region — wait for the commit rather than echo twice. */
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true

        override fun finishComposingText(): Boolean = true

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            repeat(beforeLength) { onKey(TerminalKey.Backspace, TerminalModifiers.None) }
            repeat(afterLength) { onKey(TerminalKey.Delete, TerminalModifiers.None) }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean = handleKeyEvent(event)

        /** The action button is the closest thing the IME has to an Enter key. */
        override fun performEditorAction(editorAction: Int): Boolean {
            onKey(TerminalKey.Enter, TerminalModifiers.None)
            return true
        }
    }

    private companion object {
        val CharacterMetaMask = (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv()

        val SystemKeyCodes = setOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
        )

        fun terminalKeyFor(keyCode: Int): TerminalKey? = when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> TerminalKey.Enter
            KeyEvent.KEYCODE_DEL -> TerminalKey.Backspace
            KeyEvent.KEYCODE_FORWARD_DEL -> TerminalKey.Delete
            KeyEvent.KEYCODE_ESCAPE -> TerminalKey.Escape
            KeyEvent.KEYCODE_TAB -> TerminalKey.Tab
            KeyEvent.KEYCODE_DPAD_UP -> TerminalKey.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> TerminalKey.Down
            KeyEvent.KEYCODE_DPAD_LEFT -> TerminalKey.Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> TerminalKey.Right
            KeyEvent.KEYCODE_MOVE_HOME -> TerminalKey.Home
            KeyEvent.KEYCODE_MOVE_END -> TerminalKey.End
            KeyEvent.KEYCODE_PAGE_UP -> TerminalKey.PageUp
            KeyEvent.KEYCODE_PAGE_DOWN -> TerminalKey.PageDown
            else -> null
        }
    }
}
