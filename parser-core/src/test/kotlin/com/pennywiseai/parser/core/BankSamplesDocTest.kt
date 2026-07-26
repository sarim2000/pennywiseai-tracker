package com.pennywiseai.parser.core

import com.pennywiseai.parser.core.bank.BankParserFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Generates `bank-samples.json` — one real, already-anonymised sample SMS per bank,
 * together with what the live parser actually extracts from it.
 *
 * pennywise-web's per-bank landing pages render this as the centrepiece: the raw message
 * on top, the parsed fields below, with the substrings that produced each field
 * highlighted. Because both halves come from the real [BankParserFactory] rather than
 * hand-written marketing copy, the demo can never claim an extraction the app wouldn't
 * actually make — if a parser regresses, this file changes and CI notices.
 *
 * Samples are harvested from the parser test suite (`ParserTestCase(message=…, sender=…)`),
 * which is already the canonical, PII-free corpus of real message formats.
 *
 * Update mode: `UPDATE_SUPPORTED_BANKS=true` (same switch as [SupportedBanksDocTest], so
 * `scripts/update-supported-banks.sh` refreshes both).
 */
class BankSamplesDocTest {

    private data class Sample(
        val bank: String,
        val sender: String,
        val message: String,
        val currency: String,
        val amount: String,
        val type: String,
        val merchant: String?,
        val accountLast4: String?,
        val reference: String?,
        val balance: String?,
    ) {
        /** More populated fields = a more convincing demo. */
        val richness: Int
            get() = listOf(merchant, accountLast4, reference, balance).count { it != null }
    }

    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        error("Could not locate the repository root")
    }

    /** Unescapes a Kotlin double-quoted literal's contents. */
    private fun unescape(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val n = raw[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '$' -> sb.append('$')
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    // --- Quality gates -------------------------------------------------------------
    // Every sample here is genuine parser output, but not every genuine output is worth
    // putting on a landing page: the test corpus deliberately includes edge cases, and a
    // few parsers still mis-extract on them (a merchant that comes out as "A/c XX1234",
    // an accountLast4 that grabbed a word). Showcasing those would advertise a bug to
    // someone deciding whether to install. So a bank's sample must parse *cleanly* to be
    // used — otherwise the next-best sample wins, and if none qualifies the bank simply
    // gets no demo panel. This picks which real example to show; it never edits one.

    /** Merchant that is really an account reference, or otherwise not a name. */
    private fun merchantLooksWrong(m: String?): Boolean {
        if (m == null) return false
        val t = m.trim()
        return t.isEmpty() ||
            t.length < 2 ||
            Regex("""^(a/c|acct?|account|ac)\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) ||
            Regex("""^[\dxX*•\s.-]+$""").matches(t)
    }

    /** Last-4 has to actually be trailing digits (masking chars allowed). */
    private fun last4LooksWrong(v: String?): Boolean {
        if (v == null) return false
        return !Regex("""^[\dxX*•]{2,6}$""").matches(v.trim())
    }

    /** Test fixtures use sentinels like 999999999; they read as broken in a demo. */
    private fun balanceLooksLikePlaceholder(v: String?): Boolean {
        if (v == null) return false
        val digits = v.substringBefore('.').trimStart('-')
        return digits.length > 8 || Regex("""^(9{5,}|0+)$""").matches(digits)
    }

    // --- Anonymization gate --------------------------------------------------------
    // These messages are published verbatim on public bank pages, so the "already
    // PII-free" assumption of the source corpus must be *enforced*, not trusted: a
    // real SMS that slipped into a parser test would leak personal data. We reject a
    // sample carrying a personal email address — one marker that has no legitimate
    // place in a bank SMS (real transaction SMS use bank UPI handles like @okhdfcbank
    // or @ybl, never a personal inbox). Phone numbers are deliberately NOT gated:
    // bank SMS quote fraud-reporting helplines ("Not you? SMS BLOCK to …"), which are
    // public bank numbers, not the user's — gating them would drop clean samples for
    // no privacy gain. (#643 review)
    private val personalEmailPattern = Regex(
        """[\w.+-]+@(?:gmail|googlemail|yahoo|ymail|outlook|hotmail|live|icloud|proton(?:mail)?|rediffmail|aol|zoho)\.[a-z.]+""",
        RegexOption.IGNORE_CASE,
    )

    private fun containsPersonalData(message: String): Boolean =
        personalEmailPattern.containsMatchIn(message)

    // `message = "..."` / `sender = "..."`, tolerating escaped quotes inside.
    private val messageRe = Regex("""message\s*=\s*"((?:[^"\\]|\\.)*)"""")
    private val senderRe = Regex("""sender\s*=\s*"((?:[^"\\]|\\.)*)"""")

    /** Test-case constructors in the suite. `SimpleTestCase` orders its arguments
     *  sender-then-message; `ParserTestCase` does the opposite. */
    private val caseHeads = listOf("ParserTestCase(", "SimpleTestCase(")

    /**
     * Returns the span of one balanced `(...)` argument list starting at [open], skipping
     * over string literals so a bracket inside a message body can't end the block early.
     */
    private fun blockEnd(source: String, open: Int): Int {
        var depth = 0
        var i = open
        var inString = false
        while (i < source.length) {
            val c = source[i]
            when {
                inString && c == '\\' -> i++
                c == '"' -> inString = !inString
                inString -> {}
                c == '(' -> depth++
                c == ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    /**
     * Pairs each message with the sender declared in the SAME test-case block.
     *
     * This used to key off proximity — "the first `sender =` after this `message =`" — which
     * is wrong whenever a constructor lists sender first (SimpleTestCase does). In a file
     * covering several banks, such as ThailandBankParsersTest, that walked past the end of
     * the block and picked up the *next* bank's sender, filing a UOB message under CIMB
     * Thai. Scanning balanced parens makes the pairing correct by construction instead of
     * by luck, so a multi-bank test file can no longer cross-contaminate.
     */
    private fun harvest(source: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (head in caseHeads) {
            var from = 0
            while (true) {
                val start = source.indexOf(head, from)
                if (start < 0) break
                val open = start + head.length - 1
                val end = blockEnd(source, open)
                if (end < 0) break
                from = end + 1

                val block = source.substring(open, end)
                val msg = messageRe.find(block)?.groupValues?.get(1)?.let(::unescape) ?: continue
                val sender = senderRe.find(block)?.groupValues?.get(1)?.let(::unescape) ?: continue
                if (sender.isNotBlank()) out.add(sender to msg)
            }
        }
        return out
    }

    private fun buildSamples(): Map<String, Sample> {
        val testRoot = File(repoRoot(), "parser-core/src/test/kotlin")
        val best = mutableMapOf<String, Sample>()

        testRoot.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            harvest(file.readText()).forEach { (sender, message) ->
                // Keep the demo readable on a phone.
                if (message.length !in 40..260 || message.contains('\n')) return@forEach
                // Never publish a real phone/email that slipped into a fixture.
                if (containsPersonalData(message)) return@forEach
                val parser = BankParserFactory.getParser(sender) ?: return@forEach
                val parsed = runCatching { parser.parse(message, sender, FIXED_TIMESTAMP) }
                    .getOrNull() ?: return@forEach

                if (parsed.amount.signum() <= 0) return@forEach
                if (merchantLooksWrong(parsed.merchant)) return@forEach
                if (last4LooksWrong(parsed.accountLast4)) return@forEach

                val balance = parsed.balance?.toPlainString()
                    ?.takeUnless { balanceLooksLikePlaceholder(it) }

                val sample = Sample(
                    bank = parser.getBankName(),
                    sender = sender,
                    message = message,
                    currency = parsed.currency ?: parser.getCurrency(),
                    amount = parsed.amount.toPlainString(),
                    type = parsed.type.name,
                    merchant = parsed.merchant,
                    accountLast4 = parsed.accountLast4,
                    reference = parsed.reference,
                    balance = balance,
                )

                val current = best[sample.bank]
                val better = current == null ||
                    sample.richness > current.richness ||
                    // Tie-break deterministically so the generated file is stable.
                    (sample.richness == current.richness && sample.message < current.message)
                if (better) best[sample.bank] = sample
            }
        }
        return best.toSortedMap()
    }

    private fun render(samples: Map<String, Sample>): String {
        fun esc(s: String) = s
            .replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
        fun field(name: String, value: String?) =
            if (value == null) "      \"$name\": null" else "      \"$name\": \"${esc(value)}\""

        val sb = StringBuilder("{\n")
        sb.append("  \"samples\": {\n")
        samples.entries.forEachIndexed { i, (bank, s) ->
            sb.append("    \"${esc(bank)}\": {\n")
            sb.append(field("sender", s.sender)).append(",\n")
            sb.append(field("message", s.message)).append(",\n")
            sb.append(field("currency", s.currency)).append(",\n")
            sb.append(field("amount", s.amount)).append(",\n")
            sb.append(field("type", s.type)).append(",\n")
            sb.append(field("merchant", s.merchant)).append(",\n")
            sb.append(field("accountLast4", s.accountLast4)).append(",\n")
            sb.append(field("reference", s.reference)).append(",\n")
            sb.append(field("balance", s.balance)).append("\n")
            sb.append("    }${if (i == samples.size - 1) "" else ","}\n")
        }
        sb.append("  }\n}\n")
        return sb.toString()
    }

    @Test
    fun `harvest never pairs a message with another block's sender`() {
        // Mirrors the shape that broke: one file covering two banks, where the first
        // constructor lists sender AFTER message and the second lists it BEFORE. A
        // proximity-based pairing walks out of block one and steals block two's sender.
        val source = """
            val a = listOf(
                ParserTestCase(
                    name = "uob",
                    message = "UOB: Card transaction 3,200.00 THB at AMAZON Bal 22,400.00 THB",
                    sender = "UOB",
                    expected = ExpectedTransaction(amount = BigDecimal("3200.00")),
                ),
                SimpleTestCase(
                    bankName = "CIMB Thai",
                    sender = "CIMB",
                    message = "CIMB: Transfer received 6,000.00 THB A/C x5566",
                ),
            )
        """.trimIndent()

        val senderOf = harvest(source).associate { (sender, message) -> message to sender }
        assertEquals(2, senderOf.size, "both test-case shapes should be harvested")
        assertEquals(
            "UOB",
            senderOf.entries.first { it.key.startsWith("UOB:") }.value,
            "a UOB message must stay paired with the UOB sender"
        )
        assertEquals(
            "CIMB",
            senderOf.entries.first { it.key.startsWith("CIMB:") }.value,
            "a CIMB message must stay paired with the CIMB sender"
        )
    }

    @Test
    fun `bank samples are in sync`() {
        val samples = buildSamples()
        val json = render(samples)
        val target = File(
            repoRoot(),
            "pennywise-web/server/src/main/resources/bank-samples.json"
        )

        if (System.getenv("UPDATE_SUPPORTED_BANKS") == "true") {
            target.parentFile.mkdirs()
            target.writeText(json)
            println("bank-samples.json: ${samples.size} banks with a live parse demo")
            return
        }

        assertEquals(
            json,
            target.takeIf { it.exists() }?.readText(),
            "bank-samples.json is stale — run scripts/update-supported-banks.sh"
        )
    }

    private companion object {
        /** Fixed so regenerating the file twice produces identical bytes. */
        const val FIXED_TIMESTAMP = 1_750_000_000_000L
    }
}
