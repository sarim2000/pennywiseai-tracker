package com.pennywiseai.tracker.widget

import android.content.Context
import com.pennywiseai.tracker.core.TransactionWidgets
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** [TransactionWidgets] backed by [WidgetRefresher]'s one-shot update workers. */
class WorkManagerTransactionWidgets @Inject constructor(
    @ApplicationContext private val context: Context
) : TransactionWidgets {

    override fun refresh() = WidgetRefresher.refreshTransactionWidgets(context)
}
