package app.tessera.data.drive

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await

/** Scope Tessera requests: create/read/modify only the files it creates in the user's Drive. */
private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/** Access tokens are cached for slightly less than their real ~1h lifetime. */
private const val TOKEN_TTL_MILLIS = 55L * 60L * 1000L

/** Pure interface the rest of the app depends on for Drive OAuth. */
interface DriveAuthManager {
    /** Cached-or-refreshed access token, or null when consent is missing/declined. */
    suspend fun accessToken(): String?

    /** Emits Consent-needed PendingIntents; the editor screen launches them. */
    val consentRequests: SharedFlow<PendingIntent>

    /** Drop cached token after a DriveAuthException so next call re-authorizes. */
    fun invalidateToken()
}

@Singleton
class PlayServicesDriveAuthManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DriveAuthManager {
        private val tokenCache = TokenCache(clock = System::currentTimeMillis)
        private val _consentRequests =
            MutableSharedFlow<PendingIntent>(extraBufferCapacity = 1)

        override val consentRequests: SharedFlow<PendingIntent> = _consentRequests.asSharedFlow()

        override suspend fun accessToken(): String? {
            tokenCache.get()?.let { return it }

            val request =
                AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                    .build()
            val result = Identity.getAuthorizationClient(context).authorize(request).await()

            if (result.hasResolution()) {
                result.pendingIntent?.let { _consentRequests.emit(it) }
                return null
            }

            val token = result.accessToken ?: return null
            tokenCache.put(token, TOKEN_TTL_MILLIS)
            return token
        }

        override fun invalidateToken() {
            tokenCache.invalidate()
        }
    }
