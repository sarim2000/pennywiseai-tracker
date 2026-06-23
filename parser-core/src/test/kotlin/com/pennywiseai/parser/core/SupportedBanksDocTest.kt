package com.pennywiseai.parser.core

import com.pennywiseai.parser.core.bank.BankParserFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Generates the supported-banks catalogue from the live [BankParserFactory] registry,
 * so the docs/website listing can never drift from the actual parsers.
 *
 * - Normal run (CI): asserts the committed `docs/supported-banks.json` and the README
 *   marker block are up to date. If a parser was added/removed without regenerating,
 *   this fails with a clear "run scripts/update-supported-banks.sh" message.
 * - Update mode (env `UPDATE_SUPPORTED_BANKS=true`, what the script passes): (re)writes
 *   `docs/supported-banks.json` and the README block between the markers.
 *
 * Country/flag/symbol are derived from each parser's base currency (a parser's
 * `getCurrency()`), which maps 1:1 to a country for every bank we support. Multi-currency
 * parsers (UAE, Arab Bank, CRDB, …) are grouped under their base currency.
 */
class SupportedBanksDocTest {

    private data class CountryMeta(val country: String, val flag: String, val symbol: String)

    // Currency -> country presentation. Keep in sync when a parser introduces a new currency
    // (the generator throws below if a currency is missing, so it can't be forgotten).
    private val currencyMeta = mapOf(
        "INR" to CountryMeta("India", "🇮🇳", "₹"),
        "USD" to CountryMeta("United States", "🇺🇸", "$"),
        "AED" to CountryMeta("UAE", "🇦🇪", "د.إ"),
        "THB" to CountryMeta("Thailand", "🇹🇭", "฿"),
        "NPR" to CountryMeta("Nepal", "🇳🇵", "₨"),
        "ETB" to CountryMeta("Ethiopia", "🇪🇹", "ብር"),
        "TZS" to CountryMeta("Tanzania", "🇹🇿", "TSh"),
        "PKR" to CountryMeta("Pakistan", "🇵🇰", "₨"),
        "IRR" to CountryMeta("Iran", "🇮🇷", "﷼"),
        "SAR" to CountryMeta("Saudi Arabia", "🇸🇦", "﷼"),
        "RUB" to CountryMeta("Russia", "🇷🇺", "₽"),
        "BYN" to CountryMeta("Belarus", "🇧🇾", "Br"),
        "COP" to CountryMeta("Colombia", "🇨🇴", "$"),
        "EGP" to CountryMeta("Egypt", "🇪🇬", "E£"),
        "CZK" to CountryMeta("Czech Republic", "🇨🇿", "Kč"),
        "KES" to CountryMeta("Kenya", "🇰🇪", "Ksh"),
        "NGN" to CountryMeta("Nigeria", "🇳🇬", "₦"),
        "MZN" to CountryMeta("Mozambique", "🇲🇿", "MT"),
        "LKR" to CountryMeta("Sri Lanka", "🇱🇰", "Rs"),
        "TRY" to CountryMeta("Turkey", "🇹🇷", "₺"),
        "OMR" to CountryMeta("Oman", "🇴🇲", "ر.ع."),
        "EUR" to CountryMeta("Eurozone", "🇪🇺", "€"),
    )

    private data class CountryGroup(
        val currency: String,
        val meta: CountryMeta,
        val banks: List<String>,
    )

    private fun buildGroups(): List<CountryGroup> {
        val byCurrency = BankParserFactory.getAllParsers()
            .map { it.getBankName() to it.getCurrency() }
            .distinct()
            .groupBy({ it.second }, { it.first })

        val missing = byCurrency.keys.filterNot { currencyMeta.containsKey(it) }
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "No country mapping for currency code(s) $missing — add them to " +
                    "SupportedBanksDocTest.currencyMeta."
            )
        }

        return byCurrency.entries
            .map { (currency, banks) ->
                CountryGroup(currency, currencyMeta.getValue(currency), banks.distinct().sorted())
            }
            // Most banks first, then alphabetical by country.
            .sortedWith(compareByDescending<CountryGroup> { it.banks.size }.thenBy { it.meta.country })
    }

    /** Headline count used both in the JSON and the README marketing bullet. */
    private fun summaryText(groups: List<CountryGroup>): String {
        val totalBanks = groups.sumOf { it.banks.size }
        return "$totalBanks banks across ${groups.size} countries"
    }

    private fun renderJson(groups: List<CountryGroup>): String {
        fun esc(s: String) = s
            .replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
        val totalBanks = groups.sumOf { it.banks.size }
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"totalBanks\": $totalBanks,\n")
        sb.append("  \"totalCountries\": ${groups.size},\n")
        sb.append("  \"countries\": [\n")
        groups.forEachIndexed { i, g ->
            sb.append("    {\n")
            sb.append("      \"country\": \"${esc(g.meta.country)}\",\n")
            sb.append("      \"flag\": \"${esc(g.meta.flag)}\",\n")
            sb.append("      \"currency\": \"${g.currency}\",\n")
            sb.append("      \"symbol\": \"${esc(g.meta.symbol)}\",\n")
            sb.append("      \"bankCount\": ${g.banks.size},\n")
            sb.append("      \"banks\": [${g.banks.joinToString(", ") { "\"${esc(it)}\"" }}]\n")
            sb.append("    }${if (i == groups.lastIndex) "" else ","}\n")
        }
        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun renderReadmeBlock(groups: List<CountryGroup>): String {
        val totalBanks = groups.sumOf { it.banks.size }
        val sb = StringBuilder()
        sb.append("<!-- SUPPORTED_BANKS:START (generated by scripts/update-supported-banks.sh — do not edit by hand) -->\n")
        sb.append("Supporting **$totalBanks banks & services** across **${groups.size} countries** with **multi-currency** capabilities:\n")
        groups.forEach { g ->
            sb.append("\n### ${g.meta.flag} ${g.meta.country} — ${g.currency} ${g.meta.symbol} (${g.banks.size})\n")
            sb.append(g.banks.joinToString(", ") { "**$it**" })
            sb.append("\n")
        }
        sb.append("<!-- SUPPORTED_BANKS:END -->")
        return sb.toString()
    }

    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists() || File(dir, "settings.gradle").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        error("Could not locate the repository root (settings.gradle[.kts] not found)")
    }

    private fun replaceMarkers(readme: String, block: String): String {
        val start = "<!-- SUPPORTED_BANKS:START"
        val endMarker = "<!-- SUPPORTED_BANKS:END -->"
        val startIdx = readme.indexOf(start)
        val endIdx = readme.indexOf(endMarker)
        require(startIdx >= 0 && endIdx > startIdx) {
            "README.md is missing the SUPPORTED_BANKS markers."
        }
        return readme.substring(0, startIdx) + block + readme.substring(endIdx + endMarker.length)
    }

    private val summaryStart = "<!-- BANKS_SUMMARY -->"
    private val summaryEnd = "<!-- /BANKS_SUMMARY -->"

    /** Rewrites the inline headline count in the marketing bullet (keeps the markers). */
    private fun replaceSummary(readme: String, summary: String): String {
        val s = readme.indexOf(summaryStart)
        val e = readme.indexOf(summaryEnd)
        require(s >= 0 && e > s) { "README.md is missing the BANKS_SUMMARY markers." }
        return readme.substring(0, s + summaryStart.length) + summary + readme.substring(e)
    }

    private fun currentSummary(readme: String): String? {
        val s = readme.indexOf(summaryStart)
        val e = readme.indexOf(summaryEnd)
        if (s < 0 || e <= s) return null
        return readme.substring(s + summaryStart.length, e)
    }

    @Test
    fun `supported-banks catalogue is in sync`() {
        val groups = buildGroups()
        val json = renderJson(groups)
        val block = renderReadmeBlock(groups)
        val summary = summaryText(groups)

        val root = repoRoot()
        val jsonFile = File(root, "docs/supported-banks.json")
        val readmeFile = File(root, "README.md")

        if (System.getenv("UPDATE_SUPPORTED_BANKS") == "true") {
            jsonFile.parentFile.mkdirs()
            jsonFile.writeText(json)
            val updated = replaceSummary(replaceMarkers(readmeFile.readText(), block), summary)
            readmeFile.writeText(updated)
            return
        }

        assertEquals(
            json,
            jsonFile.takeIf { it.exists() }?.readText(),
            "docs/supported-banks.json is stale — run scripts/update-supported-banks.sh"
        )
        val readme = readmeFile.readText()
        val current = readme.substringAfter("<!-- SUPPORTED_BANKS:START", "")
            .let { "<!-- SUPPORTED_BANKS:START$it" }
            .substringBefore("<!-- SUPPORTED_BANKS:END -->", "") + "<!-- SUPPORTED_BANKS:END -->"
        assertEquals(
            block,
            current,
            "README supported-banks block is stale — run scripts/update-supported-banks.sh"
        )
        assertEquals(
            summary,
            currentSummary(readme),
            "README banks-summary bullet is stale — run scripts/update-supported-banks.sh"
        )
    }
}
