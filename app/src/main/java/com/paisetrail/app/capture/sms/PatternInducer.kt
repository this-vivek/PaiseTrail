package com.paisetrail.app.capture.sms

/**
 * Turns a handful of real SMS bodies into a regex, so adding a bank doesn't require hand-writing
 * one (spec 3.2 "data, not code" extended one step further). Each rule below looks for a keyword
 * that reliably precedes one of {amount, acctLast4, ref, payee, vpa} in Indian bank UPI-debit
 * alerts (see [BankSmsPatternSeed] for the confirmed HDFC shape this was modeled on), captures the
 * value that follows, and leaves everything else as literal (escaped) text — so the induced regex
 * is really just "the example message, with the parts that clearly vary replaced by capture
 * groups." Multiple examples are used only to raise (or lower) confidence: every example is
 * scanned independently, the richest candidate becomes the suggested regex, and it's then
 * re-tested against every example to report how many it actually matches.
 */
object PatternInducer {
    data class InducedPattern(
        val regex: String,
        val fields: List<String>,
        val matchedCount: Int,
        val totalCount: Int,
    )

    private val FIELD_RULES: List<Pair<String, Regex>> = listOf(
        // Checked before "amount" so the "Avl Bal"/"Available balance" figure that follows most
        // debit alerts gets claimed here instead — otherwise it's a second number matching the
        // amount rule (duplicate named groups aren't legal in one regex, so the whole induction
        // would fail), or, if the balance rule didn't exist at all, it would freeze into literal
        // text and break matching the next time the balance is a different number.
        "balance" to Regex(
            """(?:avl\.?\s*bal(?:ance)?|available\s*bal(?:ance)?)[^0-9]{0,15}?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE,
        ),
        // Lazy non-digit skip (not just \s*) so "Rs:130.00" (colon, no space — Union Bank) and
        // "Rs 130.00" and "Rs.130.00" all work without needing a separate rule per punctuation style.
        "amount" to Regex("""(?:rs\.?|inr)[^0-9]{0,5}?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        // "Last 4" is exactly that — 4 digits — so the lazy skip plus an exact {4} (not a range)
        // forces the match past any shorter, earlier digit run, which is what actually finds the
        // trailing 4 regardless of where the bank put its masking (HDFC masks the prefix, "*6996";
        // some banks mask the middle, "158***279903", where the account number's true last 4 are
        // NOT the first digits a lazier or ranged pattern would stop on).
        "acctLast4" to Regex("""(?:a/c|acct|account)[^\n]{0,20}?([0-9]{4})(?=\D|$)""", RegexOption.IGNORE_CASE),
        "vpa" to Regex("""([\w.\-]+@[\w.\-]+)"""),
        "ref" to Regex("""(?:ref\s*no\.?|refno\.?|ref\.?|rrn|upi|txn\s*id)[:\-\s]*([0-9a-z]{4,})""", RegexOption.IGNORE_CASE),
        "payee" to Regex("""(?m)^(?:to|trf to|paid to|towards)\s+(.+?)\s*$""", RegexOption.IGNORE_CASE),
        // Not one of BankSmsPatternRegistry's five known groups (so it's never read back), but
        // dates are the most common reason two real examples of the same template otherwise fail
        // to cross-validate — without this, a date difference between examples freezes into
        // literal (and unmatchable) text, since nothing else here recognizes it as variable.
        "date" to Regex("""(?:on)\s+([0-9]{1,2}[/-][0-9]{1,2}[/-][0-9]{2,4})""", RegexOption.IGNORE_CASE),
        // Same reasoning as "date" — a timestamp down to the second (e.g. "18:52:24") is certain
        // to differ on the next real message of the same template, so it needs its own field
        // rather than being left to freeze into literal text. No keyword prefix needed — the
        // HH:MM(:SS) shape itself is distinctive enough not to collide with anything else here.
        "time" to Regex("""([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)"""),
    )

    /** Best-effort regex from 1+ raw SMS bodies believed to be the same bank's same template. */
    fun induce(examples: List<String>): InducedPattern? {
        val cleaned = examples.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return null

        val candidates = cleaned.map { buildFromExample(it) }
        val best = candidates.maxByOrNull { it.second.size } ?: return null
        val (regexString, fields) = best
        if ("amount" !in fields) return null

        val compiled = try {
            Regex(regexString, RegexOption.DOT_MATCHES_ALL)
        } catch (e: Exception) {
            return null
        }
        val matchedCount = cleaned.count { example -> matchesWithAmount(compiled, example) }

        return InducedPattern(regexString, fields, matchedCount, cleaned.size)
    }

    private fun matchesWithAmount(compiled: Regex, example: String): Boolean {
        val result = compiled.find(example) ?: return false
        val groups = result.groups as? MatchNamedGroupCollection ?: return false
        return try {
            groups["amount"] != null
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun buildFromExample(text: String): Pair<String, List<String>> {
        val spans = scan(text)
        val sb = StringBuilder()
        val fields = mutableListOf<String>()
        var cursor = 0
        for (span in spans) {
            appendLiteral(sb, text.substring(cursor, span.start))
            sb.append("(?<${span.field}>${patternFor(span.field)})")
            fields += span.field
            cursor = span.end
        }
        appendLiteral(sb, text.substring(cursor))
        return sb.toString() to fields
    }

    private data class FieldSpan(val start: Int, val end: Int, val field: String)

    /** Non-overlapping value spans found in [text], in the priority order of [FIELD_RULES] — a
     * span already claimed by an earlier (higher-priority) field can't be claimed again, and only
     * the FIRST match of each rule is ever taken. A regex can't declare the same named group
     * twice, so if a field's keyword appears more than once in one message (most commonly the
     * debited amount followed by an "Avl Bal" figure — both digit-shaped and both preceded by
     * "Rs"/"INR"), taking every occurrence would produce an invalid, uncompilable regex. */
    private fun scan(text: String): List<FieldSpan> {
        val claimed = mutableListOf<IntRange>()
        val spans = mutableListOf<FieldSpan>()
        for ((field, rule) in FIELD_RULES) {
            val match = rule.find(text) ?: continue
            val group = match.groups[1] ?: continue
            val range = group.range
            if (claimed.any { it.first <= range.last && range.first <= it.last }) continue
            claimed += range
            spans += FieldSpan(range.first, range.last + 1, field)
        }
        return spans.sortedBy { it.start }
    }

    private fun patternFor(field: String): String = when (field) {
        "amount" -> "[0-9,]+(?:\\.[0-9]{1,2})?"
        "acctLast4" -> "[0-9]{2,6}"
        "ref" -> "[0-9A-Za-z]+"
        "payee" -> ".+?"
        "vpa" -> "[\\w.\\-]+@[\\w.\\-]+"
        "date" -> "[0-9]{1,2}[/-][0-9]{1,2}[/-][0-9]{2,4}"
        "balance" -> "[0-9,]+(?:\\.[0-9]{1,2})?"
        "time" -> "[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?"
        else -> ".*?"
    }

    /** Whitespace runs become `\s+` (tolerant of minor formatting drift between messages of the
     * same template — carrier line-ending differences, an extra space); everything else is
     * escaped exactly since it's presumed to be the bank's fixed wording. */
    private fun appendLiteral(sb: StringBuilder, literal: String) {
        var i = 0
        while (i < literal.length) {
            if (literal[i].isWhitespace()) {
                var j = i
                while (j < literal.length && literal[j].isWhitespace()) j++
                sb.append("""\s+""")
                i = j
            } else {
                var j = i
                while (j < literal.length && !literal[j].isWhitespace()) j++
                sb.append(Regex.escape(literal.substring(i, j)))
                i = j
            }
        }
    }
}
