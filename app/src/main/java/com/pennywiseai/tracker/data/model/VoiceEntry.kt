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
 * saves. A question or a plan goes to Chat instead, and two amounts too once the model is there.
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
            if (text.isBlank() || asksOrPlans(text)) return null
            val daysAgo = ChatMessageFacts.spokenDaysAgo(text) ?: return null
            // "taxi 30 monday": a weekday this can't count must not be dated today.
            if (daysAgo == 0 && WEEKDAY.containsMatchIn(text)) return null
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

        /** A question or a plan: for Chat, which keeps it until the model is there. */
        fun asksOrPlans(text: String) = asks(text) || TransactionDraft.saysNotYetPaid(text) ||
            ARABIC_PLAN.containsMatchIn(text) || LATER_DAY.containsMatchIn(text)

        /**
         * More than one amount, or a day this can't count: only the model can read it. A spend
         * whose amount wasn't heard ("lunch cash", or an amount said in words) is not one of
         * these: the user says it again or types it.
         */
        fun needsModel(text: String): Boolean = ChatMessageFacts.spokenDaysAgo(text).let { it == null || it == 0 && WEEKDAY.containsMatchIn(text) } ||
            ChatMessageFacts.statedAmounts(ChatMessageFacts.withoutDays(text)).size > 1

        private val WEEKDAY = Regex(
            "(?<![\\p{L}\\p{M}])(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
                "الاثنين|الإثنين|الثلاثاء|الأربعاء|الاربعاء|الخميس|الجمعة|السبت|الأحد|الاحد)(?![\\p{L}\\p{M}])",
            RegexOption.IGNORE_CASE
        )

        private fun asks(text: String) = '?' in text || '؟' in text || QUESTION_START.containsMatchIn(text)

        // Said before a question: "okay how much …", "طيب كم …". The recognizer adds no "?".
        private const val LEAD_IN = "(?:(?:okay|ok|hey|so|um|umm|uh|well|and|but|right|alright|" +
            "طيب|طب|يعني|اه|آه|اوكي|أوكي|هلا)(?![\\p{L}\\p{M}'])[\\s,،.!]*)*"

        private val QUESTION_START = Regex(
            "^\\s*$LEAD_IN(?:(?:what|what's|whats|how|when|where|why|which|who|show|list|tell|did|do|does|is|are|was|were|can|could|" +
                "should|would|will|am|have|has|compare|give)(?![\\p{L}\\p{M}'])" +
                "|(?:كم|قديش|كيف|متى|وين|أين|اين|ماذا|شو|ايش|إيش|هل|ليش|لماذا|ما|اعرض|ورجيني|وريني|قارن)(?![\\p{L}\\p{M}]))",
            RegexOption.IGNORE_CASE
        )

        // TransactionDraft's plan words are English, "tomorrow" and "next week" among them.
        private val ARABIC_PLAN = Regex("(?<![\\p{L}\\p{M}])(?:سوف|سأ\\p{L}+|رح|ناوي|بدي)(?![\\p{L}\\p{M}])")

        private const val COUNT = "\\p{Nd}+|a|an|one|two|three|four|five|six|seven|eight|nine|ten|a few|a couple of|couple of"
        private const val AR_UNIT = "يوم|ايام|أيام|اسبوع|أسبوع|اسابيع|أسابيع|شهر|اشهر|أشهر|شهور|ساعة|ساعه|ساعات"

        // A day still to come: "rent 500 in two days", "later today", "قهوة ٢٠٠ بعد يومين". Not bare "غدا": it's also lunch, spoken;
        // "بعد" alone isn't either: "بعد الغداء" is after lunch.
        private val LATER_DAY = Regex(
            "(?<![\\p{L}\\p{M}])(?:in\\s+(?:$COUNT)\\s+(?:days?|weeks?|months?|hours?)|next\\s+(?:mon|tues|wednes|thurs|fri|satur|sun)day|" +
                "later|" +
                "بكرة|بكره|بكرا|غداً|غدًا|بعد\\s*(?:يومين|اسبوعين|أسبوعين|شهرين|ساعتين|$AR_UNIT|\\p{Nd}+\\s*(?:$AR_UNIT))|" +
                "(?:الاسبوع|الأسبوع|الشهر|السنة|السنه|العام)\\s+(?:الجاي|الجايه|الجاية|القادم|القادمة|القادمه))(?![\\p{L}\\p{M}])",
            RegexOption.IGNORE_CASE
        )

        /** Said before or after what it was for: "spent … on", "… in cash", "دفعت … على". */
        private val FILLER = setOf(
            "i", "i've", "ive", "just", "spent", "spend", "paid", "pay", "bought", "buy", "purchased", "gave", "sent",
            "received", "got", "earned", "for", "on", "at", "in", "of", "to", "from", "with", "by", "a", "an", "the",
            "cash", "and", "my", "some", "worth", "total", "was", "it", "is",
            "دفعت", "صرفت", "اشتريت", "شريت", "سددت", "استلمت", "قبضت", "تقاضيت", "على", "ع", "عـ", "في", "ب", "بـ", "ل", "لـ",
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
            "Transportation" to setOf("تاكسي", "بنزين", "مواصلات", "اوبر", "كريم"),
            "Salary" to setOf("راتب", "معاش"),
        )

        // Arabic writes "the" and a preposition onto the word: "القهوة", "بالتاكسي", "للبقالة" say قهوة, تاكسي, بقالة.
        private val ARABIC_ARTICLE = Regex("(?<![\\p{L}\\p{M}])[وف]?(?:بال|كال|لل|ال)(?=\\p{L}{2})")

        private fun norm(x: String) = ChatMessageFacts.foldSpelling(x).replace("&", " and ").replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ")
            .replace(ARABIC_ARTICLE, "").trim()

        private fun categoryOf(merchant: String, type: TransactionType, categories: List<CategoryEntity>, label: (String) -> String): String? {
            if (merchant.isEmpty()) return null
            val ofType = categories.filter { it.isIncome == (type == TransactionType.INCOME) && !it.isHidden }
            val words = " ${norm(merchant)} "
            // A category named in the words: "200 groceries", "٥٠ مواصلات".
            ofType.firstOrNull { c -> listOf(c.name, label(c.name)).map(::norm).any { it.isNotEmpty() && " $it " in words } }
                ?.let { return it.name }
            // The mapping's names are the built-ins'; the user may have renamed them.
            val ofThisType = { g: String -> BuiltinCategoryNames.current(g).takeIf { n -> ofType.any { it.name == n } } }
            val guess = if (type == TransactionType.INCOME) {
                SharedCategoryMapping.determineCategory(merchant, "INCOME").takeIf { it != "Income" }
            } else {
                SharedCategoryMapping.getCategory(merchant).takeIf { it != "Others" }
            }
            // Only this type's spoken words: "lunch" in a salary sentence isn't the income's category.
            return guess?.let(ofThisType)
                ?: SPOKEN.entries.firstNotNullOfOrNull { (k, said) -> ofThisType(k)?.takeIf { said.any { " ${norm(it)} " in words } } }
        }
    }
}
