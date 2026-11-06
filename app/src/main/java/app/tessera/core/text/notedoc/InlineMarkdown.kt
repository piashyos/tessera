package app.tessera.core.text.notedoc

// Inline half of the dialect (spec §5.3): CommonMark delimiter runs for `*` / `**` / `~~`, code spans,
// the tags we write ourselves (<u> <b> <i> <s>) and `\` escapes. Everything else is literal.

private const val ASCII_PUNCT = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

private fun isAsciiPunct(c: Char) = ASCII_PUNCT.indexOf(c) >= 0

private fun isPunct(c: Char): Boolean {
    if (c.code < 128) return isAsciiPunct(c)
    return when (Character.getType(c).toByte()) {
        Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
        Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION, Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL,
        Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL,
        -> true
        else -> false
    }
}

// ---- reading ----

private abstract class Node { var start = 0 }
private class TextNode(val s: String) : Node()
private class CodeNode(val s: String) : Node()
private class Tag(val name: Char, val closing: Boolean) : Node() {
    var matched = false
    val raw get() = if (closing) "</$name>" else "<$name>"
}
private class Delim(val ch: Char, val len: Int, val canOpen: Boolean, val canClose: Boolean) : Node() {
    var consumedAsCloser = 0
    var consumedAsOpener = 0
    var dead = false
    val remaining get() = len - consumedAsCloser - consumedAsOpener
}
private class Match(val open: Node, val close: Node, val mark: Mark)

private fun tagAt(s: String, i: Int): Tag? {
    val closing = i + 1 < s.length && s[i + 1] == '/'
    val nameAt = if (closing) i + 2 else i + 1
    if (nameAt + 1 >= s.length || s[nameAt + 1] != '>') return null
    val name = s[nameAt]
    return if (name == 'u' || name == 'b' || name == 'i' || name == 's') Tag(name, closing) else null
}

private fun closingRun(s: String, from: Int, n: Int): Int {
    var k = from
    while (k < s.length) {
        if (s[k] != '`') { k++; continue }
        var e = k
        while (e < s.length && s[e] == '`') e++
        if (e - k == n) return k
        k = e
    }
    return -1
}

private fun stripCodePadding(c: String) =
    if (c.length >= 2 && c.first() == ' ' && c.last() == ' ' && c.any { it != ' ' }) c.substring(1, c.length - 1) else c

private fun tokenize(s: String): List<Node> {
    val nodes = ArrayList<Node>()
    val lit = StringBuilder()
    fun flush() {
        if (lit.isNotEmpty()) { nodes += TextNode(lit.toString()); lit.clear() }
    }
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '\\' && i + 1 < s.length && isAsciiPunct(s[i + 1]) -> { lit.append(s[i + 1]); i += 2 }
            c == '`' -> {
                var j = i
                while (j < s.length && s[j] == '`') j++
                val close = closingRun(s, j, j - i)
                if (close < 0) {
                    lit.append(s, i, j)
                    i = j
                } else {
                    flush()
                    nodes += CodeNode(stripCodePadding(s.substring(j, close)))
                    i = close + (j - i)
                }
            }
            c == '*' || c == '~' -> {
                var j = i
                while (j < s.length && s[j] == c) j++
                if (c == '~' && j - i != 2) {
                    lit.append(s, i, j)
                } else {
                    flush()
                    val prev = if (i == 0) ' ' else s[i - 1]
                    val next = if (j == s.length) ' ' else s[j]
                    val left = !isWs(next) && (!isPunct(next) || isWs(prev) || isPunct(prev))
                    val right = !isWs(prev) && (!isPunct(prev) || isWs(next) || isPunct(next))
                    nodes += Delim(c, j - i, left, right)
                }
                i = j
            }
            c == '<' -> {
                val tag = tagAt(s, i)
                if (tag == null) { lit.append(c); i++ } else { flush(); nodes += tag; i += tag.raw.length }
            }
            else -> { lit.append(c); i++ }
        }
    }
    flush()
    return nodes
}

/** CommonMark "process emphasis", restricted to `*` and `~~`. */
private fun pairDelimiters(delims: List<Delim>): List<Match> {
    val matches = ArrayList<Match>()
    var ci = 0
    while (ci < delims.size) {
        val c = delims[ci]
        if (c.dead || !c.canClose || c.remaining == 0) { ci++; continue }
        var oi = ci - 1
        while (oi >= 0) {
            val o = delims[oi]
            if (!o.dead && o.ch == c.ch && o.canOpen && o.remaining > 0 && !oddMatch(o, c)) break
            oi--
        }
        if (oi < 0) {
            if (!c.canOpen) c.dead = true
            ci++
            continue
        }
        val o = delims[oi]
        val use = if (c.ch == '~' || (o.remaining >= 2 && c.remaining >= 2)) 2 else 1
        o.consumedAsOpener += use
        c.consumedAsCloser += use
        matches += Match(o, c, if (c.ch == '~') Mark.Strike else if (use == 2) Mark.Bold else Mark.Italic)
        for (k in oi + 1 until ci) delims[k].dead = true
    }
    return matches
}

private fun oddMatch(o: Delim, c: Delim) =
    o.ch == '*' && (o.canClose || c.canOpen) && (o.len + c.len) % 3 == 0 && !(o.len % 3 == 0 && c.len % 3 == 0)

private fun pairTags(nodes: List<Node>): List<Match> {
    val matches = ArrayList<Match>()
    val open = ArrayList<Tag>()
    for (n in nodes) {
        if (n !is Tag) continue
        if (!n.closing) { open += n; continue }
        val idx = open.indexOfLast { it.name == n.name }
        if (idx < 0) continue
        val o = open[idx]
        o.matched = true
        n.matched = true
        matches += Match(o, n, tagMark(n.name))
        while (open.size > idx) open.removeAt(open.lastIndex)
    }
    return matches
}

private fun tagMark(name: Char) = when (name) {
    'u' -> Mark.Underline
    'b' -> Mark.Bold
    'i' -> Mark.Italic
    else -> Mark.Strike
}

private fun literalOf(n: Node): String = when (n) {
    is TextNode -> n.s
    is CodeNode -> n.s
    is Tag -> if (n.matched) "" else n.raw
    is Delim -> n.ch.toString().repeat(n.remaining)
    else -> ""
}

// ---- legacy emphasis (spec §5.3 "Терпимость к legacy-эмфазе") ----
// The Aztec-era converter stored its spans verbatim: `**bold **text`, `** bold**`, `**a****b**`, `~~a~~~~b~~`.

private class Run(val start: Int, val len: Int) { val end get() = start + len }

/** Runs of [ch] outside escapes. */
private fun runsOf(s: String, ch: Char): List<Run> {
    val out = ArrayList<Run>()
    var i = 0
    while (i < s.length) {
        if (s[i] == '\\' && i + 1 < s.length && isAsciiPunct(s[i + 1])) { i += 2; continue }
        if (s[i] != ch) { i++; continue }
        var j = i
        while (j < s.length && s[j] == ch) j++
        out += Run(i, j - i)
        i = j
    }
    return out
}

/** `**a****b**` -> `**ab**`: a closer glued to an opener of the same kind, between non-blank text, inside a pair. */
private fun mergeAdjacentPairs(s: String, d: String): String {
    val len = d.length
    val runs = runsOf(s, d[0])
    val glued = runs.filter { r ->
        r.len == 2 * len && r.start > 0 && r.end < s.length && !isWs(s[r.start - 1]) && !isWs(s[r.end]) &&
            runs.any { it.len == len && it.end <= r.start } && runs.any { it.len == len && it.start >= r.end }
    }
    if (glued.isEmpty()) return s
    val sb = StringBuilder()
    var pos = 0
    for (r in glued) { sb.append(s, pos, r.start); pos = r.end }
    return sb.append(s, pos, s.length).toString()
}

/** `**bold **text` -> `**bold** text`: blanks on the inner side of exactly one end of a pair move outside. */
private fun moveInnerBlanks(s: String, d: String): String {
    val len = d.length
    val runs = runsOf(s, d[0]).filter { it.len == len }
    val sb = StringBuilder()
    var pos = 0
    var k = 0
    while (k < runs.size - 1) {
        val o = runs[k]
        val c = runs[k + 1]
        val inner = s.substring(o.end, c.start)
        val lead = inner.takeWhile(::isWs)
        val trail = inner.takeLastWhile(::isWs)
        if (lead.length < inner.length && (lead.isEmpty() != trail.isEmpty())) {
            sb.append(s, pos, o.start).append(lead).append(d).append(inner.substring(lead.length, inner.length - trail.length)).append(d).append(trail)
            pos = c.end
            k += 2
        } else {
            k++
        }
    }
    return sb.append(s, pos, s.length).toString()
}

private fun legacyEmphasis(s: String): String {
    if ('*' !in s && '~' !in s) return s
    val sb = StringBuilder()
    var plain = StringBuilder()
    fun flushPlain() {
        var seg = plain.toString()
        for (d in listOf("**", "~~", "*")) seg = moveInnerBlanks(if (d.length == 2) mergeAdjacentPairs(seg, d) else seg, d)
        sb.append(seg)
        plain = StringBuilder()
    }
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length && isAsciiPunct(s[i + 1])) { plain.append(c).append(s[i + 1]); i += 2; continue }
        if (c == '`') {
            var j = i
            while (j < s.length && s[j] == '`') j++
            val close = closingRun(s, j, j - i)
            if (close >= 0) {
                flushPlain()
                sb.append(s, i, close + (j - i))
                i = close + (j - i)
            } else {
                plain.append(s, i, j)
                i = j
            }
            continue
        }
        plain.append(c)
        i++
    }
    flushPlain()
    return sb.toString()
}

/** Reads one line of inline Markdown. The result is canonical (see [normalizeInline]). */
internal fun parseInline(source: String): Inline {
    val nodes = tokenize(legacyEmphasis(source))
    val matches = pairDelimiters(nodes.filterIsInstance<Delim>()) + pairTags(nodes)
    val text = StringBuilder()
    for (n in nodes) {
        n.start = text.length
        text.append(literalOf(n))
    }
    val marks = ArrayList<MarkRange>()
    for (n in nodes) if (n is CodeNode) marks += MarkRange(n.start, n.start + n.s.length, Mark.Code)
    for (m in matches) {
        val from = m.open.start + literalOf(m.open).length
        marks += MarkRange(from, m.close.start, m.mark)
    }
    return normalizeInline(Inline(text.toString(), marks))
}

// ---- writing ----

private fun needsCheck(text: String) = text.any { it == '\\' || it == '*' || it == '~' || it == '`' || it == '<' }

/**
 * Writes [inline] with delimiters (`**` `*` `~~` `<u>` backticks) and re-reads the result. If it does not read back
 * as [inline] (or [accept] refuses it), the paragraph is written in the fallback form: `<b>` `<i>` `<s>` tags and
 * escaped literal text, which always reads back (spec §5.3).
 */
internal fun serializeInline(inline: Inline, accept: (String) -> Boolean = { true }): String {
    val n = normalizeInline(inline)
    if (n.marks.isEmpty() && !needsCheck(n.text) && accept(n.text)) return n.text
    val primary = emit(n, fallback = false)
    if (parseInline(primary) == n && accept(primary)) return primary
    return emit(n, fallback = true)
}

private fun openToken(mark: Mark, fallback: Boolean) = when (mark) {
    Mark.Underline -> "<u>"
    Mark.Strike -> if (fallback) "<s>" else "~~"
    Mark.Bold -> if (fallback) "<b>" else "**"
    Mark.Italic -> if (fallback) "<i>" else "*"
    Mark.Code -> ""
}

private fun closeToken(mark: Mark, fallback: Boolean) =
    if (mark == Mark.Underline) "</u>" else if (fallback && mark != Mark.Code) "</" + openToken(mark, true)[1] + ">" else openToken(mark, false)

private fun escapeLiteral(s: String): String {
    val sb = StringBuilder(s.length + 4)
    for (c in s) {
        if (c == '\\' || c == '*' || c == '~' || c == '`' || c == '<' || c == '[') sb.append('\\')
        sb.append(c)
    }
    return sb.toString()
}

private fun codeSpan(content: String): String {
    var longest = 0
    var run = 0
    for (c in content) {
        run = if (c == '`') run + 1 else 0
        if (run > longest) longest = run
    }
    val fence = "`".repeat(longest + 1)
    val pad = if (content.startsWith('`') || content.endsWith('`')) " " else ""
    return "$fence$pad$content$pad$fence"
}

private fun emit(n: Inline, fallback: Boolean): String {
    val t = n.text
    val bounds = sortedSetOf(0, t.length)
    for (m in n.marks) { bounds += m.start; bounds += m.end }
    val sb = StringBuilder()
    val stack = ArrayList<Mark>()
    val points = bounds.toList()
    for (k in 0 until points.size - 1) {
        val a = points[k]
        val b = points[k + 1]
        val covering = n.marks.filter { it.start <= a && b <= it.end }
        val code = covering.any { it.mark == Mark.Code }
        val want = covering.filter { it.mark != Mark.Code }
        val wantKinds = want.map { it.mark }
        // close what no longer applies, together with everything opened after it
        val firstBad = stack.indexOfFirst { it !in wantKinds }
        if (firstBad >= 0) {
            while (stack.size > firstBad) sb.append(closeToken(stack.removeAt(stack.lastIndex), fallback))
        }
        // open the rest: the one that lasts longest goes outermost, ties by the canonical order
        val toOpen = want.filter { it.mark !in stack }
            .sortedWith(compareBy({ -it.end }, { writeRank(it.mark) }))
        for (m in toOpen) { sb.append(openToken(m.mark, fallback)); stack += m.mark }
        val segment = t.substring(a, b)
        sb.append(if (code) codeSpan(segment) else if (fallback) escapeLiteral(segment) else segment)
    }
    while (stack.isNotEmpty()) sb.append(closeToken(stack.removeAt(stack.lastIndex), fallback))
    return sb.toString()
}

/** Outside to inside when several marks cover the same text: `<u>`, `~~`, `**`, `*`. */
private fun writeRank(mark: Mark) = when (mark) {
    Mark.Underline -> 0
    Mark.Strike -> 1
    Mark.Bold -> 2
    Mark.Italic -> 3
    Mark.Code -> 4
}
