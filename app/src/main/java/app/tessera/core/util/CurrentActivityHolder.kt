package app.tessera.core.util

import android.app.Activity
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds a weak reference to the currently resumed [Activity]. Needed by Credential Manager,
 * which requires an Activity context to present its sign-in UI, without leaking the activity
 * into the ViewModel/repository layer.
 */
@Singleton
class CurrentActivityHolder @Inject constructor() {
    private var activityRef: WeakReference<Activity>? = null

    var activity: Activity?
        get() = activityRef?.get()
        set(value) {
            activityRef = value?.let(::WeakReference)
        }
}
