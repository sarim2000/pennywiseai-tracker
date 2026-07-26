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

    // `message = "..."` / `sender = "..."`, tolerating escaped quotes inside.
    private val messageRe = Regex("""message\s*=\s*"((?:[^"\\]|\\.)*)"""")
    private val senderRe = Regex("""sender\s*=\s*"((?:[^"\\]|\\.)*)"""")

    /**
     * Pairs each `message =` with the `sender =` that follows it inside the same
     * ParserTestCase block. Both orderings appear in the suite, so fall back to the
     * nearest preceding sender when none follows.
     */
    private fun harvest(source: String): List<Pair<String, String>> {
        val messages = messageRe.findAll(source).map { it.range.first to unescape(it.groupValues[1]) }.toList()
        val senders = senderRe.findAll(source).map { it.range.first to unescape(it.groupValues[1]) }.toList()
        if (senders.isEmpty()) return emptyList()
        return messages.mapNotNull { (pos, msg) ->
            val sender = senders.firstOrNull { it.first > pos } ?: senders.last { it.first < pos }
            sender.second.takeIf { it.isNotBlank() }?.let { it to msg }
        }
    }

    private fun buildSamples(): Map<String, Sample> {
        val testRoot = File(repoRoot(), "parser-core/src/test/kotlin")
        val best = mutableMapOf<String, Sample>()

        testRoot.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            harvest(file.readText()).forEach { (sender, message) ->
                // Keep the demo readable on a phone.
                if (message.length !in 40..260 || message.contains('\n')) return@forEach
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
