package app.tessera.ui.notes

/**
 * Time window used by the search "За период" filter. [windowMillis] is the look-back
 * span from now; [All] disables filtering.
 */
enum class DatePeriod(val windowMillis: Long?) {
    All(null),
    Today(24L * 60 * 60 * 1000),
    Week(7L * 24 * 60 * 60 * 1000),
    Month(30L * 24 * 60 * 60 * 1000),
}
