package com.pennywiseai.tracker.data.model

import com.pennywiseai.shared.domain.mapping.SharedCategoryMapping
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import java.math.BigDecimal
import java.time.LocalDate

/**
 * One spend or income said out loud for the quick add sheet: "200 lunch cash",
 * "spent 45 dollars on coffee yesterday", "دفعت ٥٠ ريال قهوة". Read by rules, not the
 * model, so it works with no model downloaded; the sheet shows every field and the user
 * saves. Anything else (a question, a plan, two amounts) goes to Chat instead.
 */
data class VoiceEntry(
    val amount: BigDecimal,
    val currency: String,
    val type: TransactionType,
    /** The words left once amount, currency and day are taken out; may be empty. */
    val merchant: String,
    /** Null when the words don't settle it: the user picks. */
    val category: String?,
    val date: LocalDate,
) {
    companion object {
        /**
         * [text] as one entry, or null when it isn't one: a question, a plan, no amount or
         * more than one, or a day this can't count. [label] gives a category's name as the
         * user sees it, so "بقالة" finds Groceries in Arabic.
         */
        fun parse(
            text: String,
            categories: List<CategoryEntity>,
            baseCurrency: String,
            today: LocalDate = LocalDate.now(),
            label: (String) -> String = { it },
        ): VoiceEntry? {
            if (text.isBlank() || asks(text) || TransactionDraft.saysNotYetPaid(text) || ARABIC_PLAN.containsMatchIn(text)) return null
            val daysAgo = ChatMessageFacts.spokenDaysAgo(text) ?: return null
            val amount = ChatMessageFacts.statedAmounts(ChatMessageFacts.withoutDays(text)).singleOrNull()
                ?.abs()?.takeIf { it.signum() > 0 } ?: return null
            val type = TransactionDraft.statedType(text, amount) ?: TransactionType.EXPENSE
            val merchant = merchantOf(text)
            return VoiceEntry(
                amount = amount,
                currency = ChatMessageFacts.currency(text, null, baseCurrency),
                type = type,
                merchant = merchant,
                category = categoryOf(merchant, type, categories, label),
                date = today.minusDays(daysAgo.toLong()),
            )
        }

        private fun asks(text: String) = '?' in text || '؟' in text || QUESTION_START.containsMatchIn(text)

        private val QUESTION_START = Regex(
            "^\\s*(?:(?:what|what's|whats|how|when|where|why|which|who|show|list|tell|did|do|does|is|are|was|were|can|could|" +
                "should|would|will|am|have|has|compare|give)(?![\\p{L}\\p{M}'])" +
                "|(?:كم|قديش|كيف|متى|وين|أين|اين|ماذا|شو|ايش|إيش|هل|ليش|لماذا|ما|اعرض|ورجيني|وريني|قارن)(?![\\p{L}\\p{M}]))",
            RegexOption.IGNORE_CASE
        )

        // TransactionDraft's plan words are English; "بكرة" is tomorrow. Not "غدا": it's also lunch, spoken.
        private val ARABIC_PLAN = Regex("(?<![\\p{L}\\p{M}])(?:سوف|سأ\\p{L}+|رح|ناوي|بدي|بكرة|بكره|بكرا|الشهر الجاي|الاسبوع الجاي|الأسبوع الجاي)(?![\\p{L}\\p{M}])")

        /** Said before or after what it was for: "spent … on", "… in cash", "دفعت … على". */
        private val FILLER = setOf(
            "i", "i've", "ive", "just", "spent", "spend", "paid", "pay", "bought", "buy", "purchased", "gave", "sent",
            "received", "got", "earned", "for", "on", "at", "in", "of", "to", "from", "with", "by", "a", "an", "the",
            "cash", "and", "my", "some", "worth", "total", "was", "it", "is",
            "دفعت", "صرفت", "اشتريت", "شريت", "سددت", "استلمت", "قبضت", "على", "ع", "عـ", "في", "ب", "بـ", "ل", "لـ",
            "عشان", "مشان", "كاش", "نقدا", "نقداً", "نقدي", "بالكاش", "من", "حق", "ثمن", "و"
        )

        private const val PUNCTUATION = ".,!;:،؛-_\"'()"

        private fun merchantOf(text: String): String {
            val words = ChatMessageFacts.withoutFacts(text).split(Regex("\\s+"))
                .map { it.trim { c -> c in PUNCTUATION } }
                .filter { it.isNotEmpty() }
            val isFiller = { w: String -> w.lowercase() in FILLER }
            return words.dropWhile(isFiller).dropLastWhile(isFiller).joinToString(" ")
        }

        /** Everyday spoken words the merchant rules (built for SMS) don't know. */
        private val SPOKEN = mapOf(
            "Food & Dining" to setOf("breakfast", "lunch", "dinner", "meal", "snack", "فطور", "فطار", "غداء", "غدا", "عشاء", "عشا", "قهوة", "مطعم"),
            "Groceries" to setOf("groceries", "بقالة", "خضار", "سوبرماركت"),
            "Transportation" to setOf("تاكسي", "بنزين", "مواصلات", "اوبر", "أوبر", "كريم"),
        )

        private fun norm(x: String) = x.lowercase().replace("&", " and ").replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim()

        private fun categoryOf(merchant: String, type: TransactionType, categories: List<CategoryEntity>, label: (String) -> String): String? {
            if (merchant.isEmpty()) return null
            val ofType = categories.filter { it.isIncome == (type == TransactionType.INCOME) && !it.isHidden }
            val words = " ${norm(merchant)} "
            // A category named in the words: "200 groceries", "٥٠ مواصلات".
            ofType.firstOrNull { c -> listOf(c.name, label(c.name)).map(::norm).any { it.isNotEmpty() && " $it " in words } }
                ?.let { return it.name }
            val guess = if (type == TransactionType.INCOME) {
                SharedCategoryMapping.determineCategory(merchant, "INCOME").takeIf { it != "Income" }
            } else {
                SharedCategoryMapping.getCategory(merchant).takeIf { it != "Others" }
                    ?: SPOKEN.entries.firstOrNull { (_, said) -> said.any { " $it " in words } }?.key
            }
            // The mapping's names are the built-ins'; the user may have renamed them.
            return guess?.let(BuiltinCategoryNames::current)?.takeIf { g -> ofType.any { it.name == g } }
        }
    }
}
