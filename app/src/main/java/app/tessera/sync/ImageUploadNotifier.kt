package app.tessera.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** One-shot, user-facing messages surfaced by the image workers (e.g. a snackbar). */
enum class ImageUploadMessage {
    /** The user's Drive storage is full — the affected image could not be uploaded. */
    DRIVE_QUOTA_EXCEEDED,
}

/**
 * One-shot message bus for image-upload events the UI shows the user. Mirrors the fire-and-forget
 * [SharedFlow] pattern used by [app.tessera.data.drive.DriveAuthManager.consentRequests]: a worker
 * reports a message, the UI collects [messages] and renders it once.
 */
interface ImageUploadNotifier {
    val messages: SharedFlow<ImageUploadMessage>

    fun report(message: ImageUploadMessage)
}

@Singleton
class DefaultImageUploadNotifier @Inject constructor() : ImageUploadNotifier {
    private val _messages = MutableSharedFlow<ImageUploadMessage>(extraBufferCapacity = 8)
    override val messages: SharedFlow<ImageUploadMessage> = _messages.asSharedFlow()

    override fun report(message: ImageUploadMessage) {
        _messages.tryEmit(message)
    }
}
