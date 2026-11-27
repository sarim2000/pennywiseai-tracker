package com.pennywiseai.tracker.presentation.common

import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.core.entitlement.FreeTierLimits
import com.pennywiseai.tracker.core.entitlement.TrialFeature
import com.pennywiseai.tracker.core.entitlement.testFeatureTrials
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    private val rows = (1..250).toList()
    private val stored = mutableMapOf<String, Any>()
    private var clock = TimeUnit.DAYS.toMillis(20_000)
    private val trials = testFeatureTrials(stored) { clock }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(pro: Boolean) = ExportViewModel(
        csvExporter = mockk(),
        userPreferencesRepository = mockk(),
        entitlementGate = mockk<EntitlementGate> { every { isProEntitled } returns MutableStateFlow(pro) },
        featureTrials = trials,
    )

    @Test
    fun `a free export without a trial writes the first rows up to the cap`() {
        val vm = viewModel(pro = false)
        assertEquals(rows.take(FreeTierLimits.MAX_CSV_EXPORT_ROWS_PER_MONTH), vm.rowsToExport(rows))
        assertTrue(vm.csvAccess.value.canStartTrial)
    }

    @Test
    fun `the CSV trial exports every row until it ends`() {
        val vm = viewModel(pro = false)
        vm.startCsvTrial()
        assertTrue(vm.csvAccess.value.unlocked)
        assertEquals(7, vm.csvAccess.value.trialDaysLeft)
        assertEquals(rows, vm.rowsToExport(rows))

        // Re-checked at export time, even with the dialog's state still showing the trial.
        clock += TimeUnit.DAYS.toMillis(7)
        assertEquals(FreeTierLimits.MAX_CSV_EXPORT_ROWS_PER_MONTH, vm.rowsToExport(rows).size)
        assertFalse(viewModel(pro = false).csvAccess.value.canStartTrial)
    }

    @Test
    fun `the analytics trial does not open CSV export`() {
        trials.start(TrialFeature.ANALYTICS_GROUPING)
        val vm = viewModel(pro = false)
        assertEquals(FreeTierLimits.MAX_CSV_EXPORT_ROWS_PER_MONTH, vm.rowsToExport(rows).size)
        assertTrue(vm.csvAccess.value.canStartTrial)
    }

    @Test
    fun `Pro exports every row and sees no trial`() {
        val vm = viewModel(pro = true)
        assertEquals(rows, vm.rowsToExport(rows))
        assertFalse(vm.csvAccess.value.canStartTrial)
        assertEquals(null, vm.csvAccess.value.trialDaysLeft)
    }
}
