package com.pennywiseai.tracker.presentation.home

import androidx.compose.runtime.Immutable
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import java.time.LocalDate

/**
 * The rows behind one of Home's figures: [type] ([TransactionTypeFilter.ALL] for every type) from [start] to [end],
 * whole days, in the profile Home counted ([profileId], null for all). With [currencyOnly]
 * only rows written in [currency]; without it (unified mode) every currency, converted to it.
 */
@Immutable
data class HomeFigureRows(
    val type: TransactionTypeFilter,
    val start: LocalDate,
    val end: LocalDate,
    val currency: String,
    val currencyOnly: Boolean,
    val profileId: Long?,
)
