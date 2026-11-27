package com.pennywiseai.tracker.data.model

import java.time.LocalDateTime

/**
 * A past merchant offered while typing one in (#1392): [merchantName] is what the field
 * gets, so the row keeps the merchant's alias and learned category; [alias] is what the
 * chip shows when the user named it.
 */
data class MerchantSuggestion(
    val merchantName: String,
    val alias: String?,
    val uses: Int,
    val lastUsed: LocalDateTime?,
)

/** What a merchant field shows of the past merchants found for it (see `TransactionDao.suggestMerchants`). */
object MerchantSuggestions {
    /** How many chips the row shows. */
    const val LIMIT = 5

    /** Typing pauses this long before a lookup, so a fast typist's every letter needn't hit the database. */
    const val DEBOUNCE_MS = 150L

    /** [typed] for a `LIKE … ESCAPE '\'`: its `%` and `_` match only themselves. */
    fun likeTerm(typed: String): String =
        typed.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /**
     * The chips for [typed]: [found] less the name already typed as is (a different case
     * stays, so a tap settles the spelling), at most [LIMIT].
     */
    fun shown(found: List<MerchantSuggestion>, typed: String): List<MerchantSuggestion> {
        val name = typed.trim()
        return found.filter { it.merchantName != name }.take(LIMIT)
    }
}
