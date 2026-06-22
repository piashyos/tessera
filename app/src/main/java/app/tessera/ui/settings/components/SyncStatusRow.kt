package app.tessera.ui.settings.components

import android.text.format.DateUtils
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.tessera.R
import app.tessera.sync.SyncIndicator
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.TriangleAlert

/**
 * Renders the current [SyncIndicator] as a [SettingsRow]. On [SyncIndicator.Error] the whole row
 * becomes a retry affordance when [onRetry] is supplied.
 */
@Composable
fun SyncStatusRow(
    indicator: SyncIndicator,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val icon: ImageVector
    val title: String
    val subtitle: String?
    var onClick: (() -> Unit)? = null

    when (indicator) {
        is SyncIndicator.Ok -> {
            icon = Lucide.CircleCheck
            title = stringResource(R.string.sync_status_synced)
            subtitle = indicator.lastSyncAtMs?.let { ts ->
                stringResource(R.string.sync_last_synced, relativeTime(ts))
            } ?: stringResource(R.string.sync_status_up_to_date)
        }

        SyncIndicator.Syncing -> {
            icon = Lucide.RefreshCw
            title = stringResource(R.string.sync_status_syncing)
            subtitle = null
        }

        SyncIndicator.Offline -> {
            icon = Lucide.CloudOff
            title = stringResource(R.string.sync_status_offline)
            subtitle = stringResource(R.string.sync_status_offline_subtitle)
        }

        is SyncIndicator.Error -> {
            icon = Lucide.TriangleAlert
            title = stringResource(R.string.sync_status_error)
            subtitle = indicator.message ?: stringResource(R.string.sync_status_error_subtitle)
            onClick = onRetry
        }
    }

    val isError = indicator is SyncIndicator.Error
    SettingsRow(
        modifier = modifier,
        leadingIcon = icon,
        title = title,
        subtitle = subtitle,
        destructive = isError,
        onClick = onClick,
        trailing = if (isError && onClick != null) {
            { SettingsValue(stringResource(R.string.sync_retry)) }
        } else {
            null
        },
    )
}

private fun relativeTime(timestampMs: Long): String =
    DateUtils.getRelativeTimeSpanString(
        timestampMs,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
