package app.tessera.ui.editor.engine

import android.util.Log
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.ui.editor.engine.spans.ObjectSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line table of the layout against the text, on the device (the fuzzer of 01deaac found `DynamicLayout` keeping a stale table after raw
 * inserts in the first paragraph, under Robolectric even on a bare `EditText`; the editor heals it, `healLayout`). After every step: nothing
 * throws, the last line ends at the text's length, each line starts where the one before it ended, and the lines are as many as the text's
 * line breaks need at least. `adb logcat -s NativeLayoutSync` has every step.
 */
class NativeLayoutSyncTest {
    private fun log(msg: String) = Log.i(TAG, msg)

    /** `null` when the line table matches the text, else what is off. Main thread. */
    private fun mismatch(v: TextView): String? {
        val l = v.layout ?: return "no layout"
        val t = v.text
        val problems = ArrayList<String>()
        if (l.getLineEnd(l.lineCount - 1) != t.length) problems += "last line ends at ${l.getLineEnd(l.lineCount - 1)}, text length ${t.length}"
        for (i in 1 until l.lineCount) if (l.getLineStart(i) != l.getLineEnd(i - 1)) problems += "line $i starts at ${l.getLineStart(i)}, line ${i - 1} ends at ${l.getLineEnd(i - 1)}"
        val breaks = t.count { it == '\n' }
        if (l.lineCount < breaks + 1) problems += "${l.lineCount} lines for ${breaks + 1} paragraphs"
        return problems.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    /**
     * The platform itself, without the engine: on this device (API 36) a bare `EditText` keeps a stale line table after these raw inserts, as
     * under Robolectric — `healLayout` is needed, not only a safety net. If this starts to fail, the platform fixed it and the heal may go.
     */
    @Test
    fun aBareEditTextOnTheDeviceKeepsAStaleLineTableAfterRawInserts() {
        DeviceEditor("").use { e ->
            val et = e.ui {
                e.container.removeView(e.view)
                EditText(e.activity).also {
                    it.setText("q\nr\nb", TextView.BufferType.EDITABLE)
                    e.container.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            }
            e.settleFrames()
            val bad = ArrayList<String>()
            for ((name, step) in listOf<Pair<String, () -> Unit>>(
                "loaded \"q\\nr\\nb\"" to {},
                "insert(0, \"\\n\")" to { et.text.insert(0, "\n") },
                "insert(0, \"a\")" to { et.text.insert(0, "a") },
            )) {
                val now = e.ui { step(); mismatch(et) } // right after the edit, before any frame
                e.settleFrames()
                val later = e.ui { mismatch(et) }
                log("bare EditText, $name: text ${e.ui { et.text.toString().replace("\n", "\\n") }}; right after ${now ?: "ok"}; after a frame ${later ?: "ok"}")
                if (now != null || later != null) bad += "$name: right after ${now ?: "ok"}, after a frame ${later ?: "ok"}"
            }
            assertTrue("the platform's stale line table after insert(0, \"\\n\"), insert(0, \"a\"): ${bad.ifEmpty { listOf("not seen") }}", bad.any { it.startsWith("insert(0, \"a\")") })
        }
    }

    @Test
    fun theEditorKeepsItsLineTableThroughRawInsertsObjectsAndPastes() {
        val bad = ArrayList<String>()
        for (md in listOf("---\n- ", "text\n![](tessera-img://qa-sync)")) {
            DeviceEditor(md).use { e ->
                fun objectStart() = e.ui { e.view.text!!.let { t -> t.getSpans(0, t.length, ObjectSpan::class.java).map { t.getSpanStart(it) }.minOrNull() ?: -1 } }
                val steps = listOf<Pair<String, () -> Unit>>(
                    "loaded" to {},
                    "a character at the very start (keyboard)" to { e.caret(0); e.ime { commitText("x", 1) } },
                    "a raw insert at the very start" to { e.ui { e.view.text!!.insert(0, "w") } },
                    "a picture with the caret at the start of the object's paragraph" to { e.caret(objectStart()); e.ui { e.view.insertImage("qa-sync-2") } },
                    "typing on" to { e.ime { commitText("y", 1) }; e.ime { commitText("z", 1) } },
                    "the picture cut and pasted at the very start" to {
                        val ps = e.ui { e.view.text!!.let { t -> t.getSpans(0, t.length, ObjectSpan::class.java).first { it.block is NoteBlock.Image }.let(t::getSpanStart) } }
                        e.ui { e.view.requestFocus(); e.view.setSelection(ps, ps + 1); e.view.onTextContextMenuItem(android.R.id.cut) }
                        e.ui { e.view.setSelection(0); e.view.onTextContextMenuItem(android.R.id.paste) }
                    },
                    "a raw insert of a line break at the very start" to { e.ui { e.view.text!!.insert(0, "\n") } },
                    "typing at the very start" to { e.caret(0); e.ime { commitText("q", 1) } },
                )
                for ((name, step) in steps) {
                    val thrown = runCatching { step() }.exceptionOrNull()
                    val now = e.ui { mismatch(e.view) }
                    e.settleFrames()
                    val later = e.ui { mismatch(e.view) }
                    log("editor \"${md.replace("\n", "\\n")}\", $name: ${e.md().replace("\n", "\\n")}; thrown ${thrown ?: "nothing"}; right after ${now ?: "ok"}; after a frame ${later ?: "ok"}")
                    if (thrown != null || later != null) bad += "\"${md.replace("\n", "\\n")}\", $name: thrown ${thrown ?: "nothing"}, after a frame ${later ?: "ok"}"
                }
            }
        }
        assertEquals("the line table of the editor", emptyList<String>(), bad)
    }

    private companion object {
        const val TAG = "NativeLayoutSync"
    }
}
