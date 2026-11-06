package app.tessera.ui.editor.engine

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper

/**
 * Spec §6.3 (1): the ways an IME says "Enter" or "Backspace" all end in [TesseraEditText.performEnter] / [TesseraEditText.performBackspace],
 * the same `EditRules` as the hardware keyboard. `commitText("\n")`, `sendKeyEvent(ENTER)`, `sendKeyEvent(DEL)`, and
 * `deleteSurroundingText(1, 0)` (what Gboard sends for Backspace; Samsung sends `DEL`). Anything else passes through unchanged.
 */
internal class TesseraInputConnection(target: InputConnection, private val host: TesseraEditText) : InputConnectionWrapper(target, true) {

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (text != null && text.length == 1 && text[0] == '\n') {
            finishComposingText() // Enter ends the word being composed, it does not replace it
            if (host.performEnter()) return true
        }
        return super.commitText(text, newCursorPosition)
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode.isEnter() && event.isPlain()) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                finishComposingText()
                if (!host.performEnter()) return super.sendKeyEvent(event)
            }
            return host.editable
        }
        if (event.keyCode == KeyEvent.KEYCODE_DEL && event.isPlain()) {
            if (event.action == KeyEvent.ACTION_DOWN && !host.performBackspace()) return super.sendKeyEvent(event)
            return host.editable
        }
        return super.sendKeyEvent(event)
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        if (beforeLength == 1 && afterLength == 0 && host.hasCollapsedSelection() && host.performBackspace()) return true
        return super.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun getCursorCapsMode(reqModes: Int): Int = host.capsModeAfterSentinel(reqModes) ?: super.getCursorCapsMode(reqModes)

    private fun Int.isEnter() = this == KeyEvent.KEYCODE_ENTER || this == KeyEvent.KEYCODE_NUMPAD_ENTER
    private fun KeyEvent.isPlain() = !isCtrlPressed && !isAltPressed && !isMetaPressed
}
