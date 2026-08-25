package app.tessera.ui.notes

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.tessera.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatNoteDate(
    epochMs: Long,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.ENGLISH,
    todayLabel: String = "Today",
    yesterdayLabel: String = "Yesterday",
): String {
    val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    return when {
        date == today -> todayLabel
        date == today.minusDays(1) -> yesterdayLabel
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMM", locale))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }
}

/** Localized note date label that follows the app's currently selected language. */
@Composable
fun noteDateLabel(epochMs: Long): String =
    formatNoteDate(
        epochMs = epochMs,
        locale = LocalConfiguration.current.locales[0],
        todayLabel = stringResource(R.string.note_date_today),
        yesterdayLabel = stringResource(R.string.note_date_yesterday),
    )
