package app.tessera.core.text

import java.text.Normalizer
import kotlin.math.min

/**
 * Lenient, typo-tolerant note search. Matching is:
 *  - case-insensitive across scripts (Unicode lowercasing, so `Свадьба` == `свадьба`, not just ASCII);
 *  - diacritic-insensitive (`café` == `cafe`, `ё` == `е`);
 *  - substring / prefix aware (`свад` finds `свадьба`);
 *  - typo-tolerant (`свдьба`, `свадба` still find `свадьба`) via bounded edit distance on word tokens.
 *
 * Multi-word queries use AND semantics: every query term must match somewhere in the note. Results
 * are ranked by [matchScore] (title hits and exact/prefix hits outrank fuzzy body hits).
 */

private val NonWord = Regex("[^\\p{L}\\p{N}]+")
private val CombiningMarks = Regex("\\p{Mn}+")

/** Lowercases (Unicode-aware) and strips combining diacritical marks for accent-insensitive matching. */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(CombiningMarks, "")

/** Splits already-normalized text into word tokens (letters/digits runs). */
fun searchTokens(text: String): List<String> =
    text.split(NonWord).filter { it.isNotBlank() }

/** Distinct, normalized query terms. Empty when the query has no searchable characters. */
fun searchTerms(query: String): List<String> =
    searchTokens(normalizeForSearch(query)).distinct()

/**
 * Edit-distance budget allowed for a typo on a term of [length]. Short terms get no slack (fuzzy
 * matching them would match almost anything); longer terms tolerate more.
 */
internal fun fuzzyBudget(length: Int): Int = when {
    length <= 3 -> 0
    length <= 6 -> 1
    else -> 2
}

/**
 * True when [a] and [b] are within [max] single-character edits (Levenshtein). Uses a two-row
 * DP with an early-out when the whole row exceeds [max], so it stays cheap for the common
 * "clearly different" case.
 */
internal fun withinEditDistance(a: String, b: String, max: Int): Boolean {
    if (max == 0) return a == b
    val lenA = a.length
    val lenB = b.length
    if (kotlin.math.abs(lenA - lenB) > max) return false

    var prev = IntArray(lenB + 1) { it }
    var curr = IntArray(lenB + 1)
    for (i in 1..lenA) {
        curr[0] = i
        var rowMin = curr[0]
        for (j in 1..lenB) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = min(min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost)
            if (curr[j] < rowMin) rowMin = curr[j]
        }
        if (rowMin > max) return false
        val tmp = prev
        prev = curr
        curr = tmp
    }
    return prev[lenB] <= max
}

private const val SCORE_TITLE_EXACT = 100
private const val SCORE_TITLE_PREFIX = 60
private const val SCORE_TITLE_SUBSTRING = 40
private const val SCORE_TAG = 30
private const val SCORE_BODY_EXACT = 28
private const val SCORE_BODY_PREFIX = 20
private const val SCORE_BODY_SUBSTRING = 14
private const val SCORE_TITLE_FUZZY = 12
private const val SCORE_BODY_FUZZY = 6

/**
 * Relevance score of a note's [title]/[body]/[tags] against the normalized query [terms], or `null`
 * when any term fails to match (AND semantics). Higher is more relevant. Inputs are raw text;
 * normalization happens here.
 */
fun matchScore(title: String, body: String, tags: List<String>, terms: List<String>): Int? {
    if (terms.isEmpty()) return 0

    val titleNorm = normalizeForSearch(title)
    val bodyNorm = normalizeForSearch(body)
    val tagsNorm = tags.map(::normalizeForSearch)
    val titleTokens = searchTokens(titleNorm)
    val bodyTokens = searchTokens(bodyNorm)

    var total = 0
    for (term in terms) {
        val termScore = bestTermScore(term, titleNorm, bodyNorm, tagsNorm, titleTokens, bodyTokens)
            ?: return null // this term matched nowhere → the note doesn't match
        total += termScore
    }
    return total
}

private fun bestTermScore(
    term: String,
    titleNorm: String,
    bodyNorm: String,
    tagsNorm: List<String>,
    titleTokens: List<String>,
    bodyTokens: List<String>,
): Int? {
    // Strongest signals first.
    when {
        titleTokens.any { it == term } -> return SCORE_TITLE_EXACT
        titleTokens.any { it.startsWith(term) } -> return SCORE_TITLE_PREFIX
    }
    var best = 0
    if (titleNorm.contains(term)) best = maxOf(best, SCORE_TITLE_SUBSTRING)
    if (tagsNorm.any { it.contains(term) }) best = maxOf(best, SCORE_TAG)
    when {
        bodyTokens.any { it == term } -> best = maxOf(best, SCORE_BODY_EXACT)
        bodyTokens.any { it.startsWith(term) } -> best = maxOf(best, SCORE_BODY_PREFIX)
        bodyNorm.contains(term) -> best = maxOf(best, SCORE_BODY_SUBSTRING)
    }
    if (best > 0) return best

    // Nothing exact/substring matched — allow a typo on word tokens.
    val budget = fuzzyBudget(term.length)
    if (budget > 0) {
        if (titleTokens.any { withinEditDistance(it, term, budget) }) return SCORE_TITLE_FUZZY
        if (bodyTokens.any { withinEditDistance(it, term, budget) }) return SCORE_BODY_FUZZY
    }
    return null
}
