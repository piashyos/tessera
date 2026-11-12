package app.tessera.ui.editor.components

import app.tessera.ui.editor.engine.FormatState

/** Toolbar-facing actions applied to the editor's current selection. */
interface RichBodyActions {
    fun toggleBold()
    fun toggleItalic()
    fun toggleStrikethrough()
    fun toggleParagraph()
    fun toggleHeading1()
    fun toggleHeading2()
    fun toggleHeading3()
    fun toggleHeading4()
    fun toggleHeading5()
    fun toggleHeading6()
    fun toggleBulletList()
    fun toggleTaskList()

    // Default no-ops so previews / test mocks that implement RichBodyActions without a live editor keep compiling.
    /** Toggle underline on the current selection. */
    fun toggleUnderline() {}

    /** Toggle an ordered (numbered) list on the current block(s). */
    fun toggleOrderedList() {}

    /** Toggle a block quote on the current block(s). */
    fun toggleQuote() {}

    /** Toggle a monospace code block on the current block(s). */
    fun toggleCode() {}

    /** Insert a horizontal rule (divider) at the cursor. */
    fun insertHorizontalRule() {}

    /** Undo the last edit. No-op when the stack is empty. */
    fun undo() {}

    /** Redo the last undone edit. No-op when nothing was undone. */
    fun redo() {}

    /** Programmatically focus the editor and surface the IME, placing the cursor at end of text. */
    fun focusBody()

    /** Insert a new empty 2x2 table at the caret. */
    fun insertTable() {}

    /**
     * User intent to attach an image from the gallery. Overridden at the screen level to launch
     * the photo picker; the editor object itself leaves it a no-op.
     */
    fun onInsertImage() {}

    /**
     * User intent to capture an image with the camera. Overridden at the screen level to request
     * the CAMERA permission (when needed) and launch capture; a no-op on the editor object.
     */
    fun onCaptureImage() {}

    /**
     * Splice an image (`tessera-img://<imageId>`) into the editor AT THE CURRENT CARET. Called by the screen after
     * [InsertImageUseCase] has stored the image and produced its [imageId]. Default no-op for previews / mocks
     * without a live editor.
     */
    fun insertImage(imageId: String) {}

    /** Toggle the Subheading paragraph kind on the current block(s). */
    fun toggleSubheading() {}

    /** Toggle a dashed list on the current block(s). */
    fun toggleDashList() {}

    /** Move the selected list items one level right. */
    fun indent() {}

    /** Move the selected list items one level left. */
    fun outdent() {}

    /** Hand over an edit the editor still holds back (its debounce) to the host now. */
    fun flushPendingEdit() {}

    /** Take down the editor's own floating menu (Paste / Select all): it is a window above the screen's modal layers. */
    fun dismissTextMenu() {}

    /** What the format bar shows at the caret. */
    fun formatState(): FormatState = FormatState.Empty
}
