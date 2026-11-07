package app.tessera.ui.editor.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.data.images.ImageDownloader
import app.tessera.data.images.NoteImageDecoder
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.objectRect
import app.tessera.ui.editor.engine.spans.ObjectSpan
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pictures of a note as the app keeps them on the device: WebP files written the way `ImageCompressor` writes them (lossy, quality 80),
 * in the app's own folders, with a Room row that knows the local path and nothing of Drive (a row with a Drive id makes a missing or broken
 * file start a download, and the consent screen of Drive would cover the app).
 */
internal object ImageFixtures {
    /** The picture: a color that is nowhere else on the screen, so its pixels can be found. */
    const val FILL = 0xFF1565C0.toInt()

    /** The top-left quarter of the picture as stored: where it went after a rotation can be seen. */
    const val MARK = 0xFFFFB300.toInt()

    fun webp(file: File, w: Int, h: Int, mark: Boolean = true): File {
        file.parentFile?.mkdirs()
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        b.eraseColor(FILL)
        if (mark) Canvas(b).drawRect(0f, 0f, w / 4f, h / 4f, Paint().apply { color = MARK })
        FileOutputStream(file).use { b.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }
        b.recycle()
        return file
    }

    /** Where the app stores a picture taken on this device (`NoteImagesRepositoryImpl`). */
    fun appFile(ctx: Context, id: String) = File(File(ctx.filesDir, "images"), "$id.webp")

    /** Where a picture downloaded from Drive is kept (`DriveImageDownloader`). */
    fun cacheFile(ctx: Context, id: String) = File(File(ctx.cacheDir, "images"), "$id.webp")

    fun row(id: String, file: File, w: Int, h: Int, noteId: String = "qa") =
        NoteImageEntity(imageId = id, noteId = noteId, ownerUid = null, localPath = file.absolutePath, status = ImageStatus.LOCAL_ONLY, width = w, height = h)

    /** The file cut after [keep] bytes, as a download that broke off. */
    fun truncate(file: File, keep: Int) {
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(keep.coerceAtMost(bytes.size)))
    }

    fun removeApp(ctx: Context, ids: Collection<String>) = ids.forEach { appFile(ctx, it).delete(); cacheFile(ctx, it).delete() }

    /**
     * Holds every decode of [decoder] until the returned gate is opened (the bounds of a prefetch are read without it). The decode runs on an
     * IO thread; never hold a decoder whose decode runs on the main thread (the Aztec path decodes local files synchronously).
     */
    fun hold(decoder: NoteImageDecoder): CountDownLatch {
        val real = decoder.fileDecoder
        val gate = CountDownLatch(1)
        decoder.fileDecoder = { f, w, row, ref -> gate.await(30, TimeUnit.SECONDS); real(f, w, row, ref) }
        return gate
    }
}

/** The Room table of pictures, in memory: what a test decoder reads its rows from. */
internal class MemoryImageDao : NoteImageDao {
    val rows = ConcurrentHashMap<String, NoteImageEntity>()
    override suspend fun upsert(image: NoteImageEntity) { rows[image.imageId] = image }
    override suspend fun byId(imageId: String) = rows[imageId]
    override suspend fun byNote(noteId: String) = rows.values.filter { it.noteId == noteId }
    override suspend fun byOwnerAndStatus(uid: String, status: ImageStatus) = rows.values.filter { it.ownerUid == uid && it.status == status }
    override suspend fun byOwner(uid: String) = rows.values.filter { it.ownerUid == uid }
    override suspend fun all() = rows.values.toList()
    override suspend fun backfillOwner(uid: String) = Unit
    override suspend fun transition(uid: String, from: ImageStatus, to: ImageStatus) = Unit
    override suspend fun delete(imageId: String) { rows.remove(imageId) }
}

/** Drive as the decoder sees it: [fetch] gives the file a download would write, `null` when there is nothing to get. */
internal class FakeDrive(private val fetch: (String) -> File? = { null }) : ImageDownloader {
    @Volatile var calls = 0
    override suspend fun download(imageId: String): File? { calls++; return fetch(imageId) }
}

/** What an image of the text shows now: its paragraph, its source, where it is in the view, and the picture (or the tile) in it. */
internal data class Shown(val ps: Int, val src: String, val rect: Rect, val loaded: Boolean, val failed: Boolean, val pictureW: Int, val pictureH: Int)

/** The images of [e] in document order. Main thread. */
internal fun shownImages(e: TesseraEditText): List<Shown> {
    val t = e.text ?: return emptyList()
    return t.getSpans(0, t.length, ObjectSpan::class.java)
        .filter { it.block is NoteBlock.Image }
        .sortedBy { t.getSpanStart(it) }
        .map { o ->
            val ps = t.getSpanStart(o)
            val st = o.image!!
            val d = st.drawable
            Shown(ps, (o.block as NoteBlock.Image).src, e.objectRect(ps) ?: Rect(), d != null && !st.failed, st.failed, d?.intrinsicWidth ?: -1, d?.intrinsicHeight ?: -1)
        }
}

/** The top of the line that holds [needle] (layout coordinates: what the text does, whatever the scroll). Main thread. */
internal fun lineTopOf(e: TesseraEditText, needle: String): Int {
    val at = e.text.toString().indexOf(needle)
    check(at >= 0) { "\"$needle\" not in the text" }
    return e.layout.getLineTop(e.layout.getLineForOffset(at))
}

internal fun <V : View> findView(v: View, type: Class<V>): V? {
    if (type.isInstance(v)) return type.cast(v)
    if (v is ViewGroup) for (i in 0 until v.childCount) findView(v.getChildAt(i), type)?.let { return it }
    return null
}
