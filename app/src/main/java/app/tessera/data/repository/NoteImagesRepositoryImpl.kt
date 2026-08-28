package app.tessera.data.repository

import android.content.Context
import android.net.Uri
import app.tessera.data.images.ImageCompressor
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.repository.NoteImagesRepository
import app.tessera.sync.ImageWorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteImagesRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: NoteImageDao,
        private val compressor: ImageCompressor,
        private val scheduler: ImageWorkScheduler,
        private val authRepository: AuthRepository,
    ) : NoteImagesRepository {
        override suspend fun insertLocalImage(noteId: NoteId, source: Uri): Result<String> =
            runCatching {
                val imagesDir = File(context.filesDir, IMAGES_DIR).apply { mkdirs() }
                val imageId = UUID.randomUUID().toString()
                val target = File(imagesDir, "$imageId.webp")

                val compressed = compressor.compress(source, target)

                val entity =
                    NoteImageEntity(
                        imageId = imageId,
                        noteId = noteId.value,
                        ownerUid = authRepository.currentUser.value?.value,
                        localPath = target.absolutePath,
                        status = ImageStatus.LOCAL_ONLY,
                        width = compressed.width,
                        height = compressed.height,
                    )
                dao.upsert(entity)
                scheduler.requestUpload()

                imageId
            }

        private companion object {
            const val IMAGES_DIR = "images"
        }
    }
