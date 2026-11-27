package com.pennywiseai.tracker.presentation.common

import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.core.entitlement.FreeTierLimits
import com.pennywiseai.tracker.core.entitlement.TrialFeature
import com.pennywiseai.tracker.core.entitlement.TrialStatus
import com.pennywiseai.tracker.core.entitlement.testFeatureTrials
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.export.CsvExporter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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

    private val rows = List(250) { mockk<TransactionEntity>(name = "row$it") }
    private val stored = mutableMapOf<String, Any>()
    private var clock = TimeUnit.DAYS.toMillis(20_000)
    private val trials = testFeatureTrials(stored) { clock }

    /** The rows each export handed the CSV writer. */
    private val exported = mutableListOf<List<TransactionEntity>>()
    private val csvExporter = mockk<CsvExporter> {
        every { exportTransactions(any(), any()) } answers { exported += firstArg<List<TransactionEntity>>(); emptyFlow() }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(pro: Boolean?) = ExportViewModel(
        csvExporter = csvExporter,
        userPreferencesRepository = mockk(),
        entitlementGate = mockk<EntitlementGate> {
            every { resolvedProEntitlement } returns MutableStateFlow(pro)
            every { isProEntitled } returns MutableStateFlow(pro == true)
        },
        featureTrials = trials,
    )

    private suspend fun ExportViewModel.export(): List<TransactionEntity> {
        exportTransactions(rows).collect {}
        return exported.last()
    }

    @Test
    fun `a free export without a trial writes the first rows up to the cap`() = runTest {
        val vm = viewModel(pro = false)
        assertEquals(rows.take(FreeTierLimits.MAX_CSV_EXPORT_ROWS_PER_MONTH), vm.export())
        assertTrue(vm.csvAccess.value.canStartTrial)
    }

    @Test
    fun `the CSV trial exports every row until it ends`() = runTest {
        val vm = viewModel(pro = false)
        vm.startCsvTrial()
        assertTrue(vm.csvAccess.value.unlocked)
        assertEquals(7, vm.csvAccess.value.trialDaysLeft)
        assertEquals(rows, vm.export())

        clock += TimeUnit.DAYS.toMillis(7)
        assertEquals(FreeTierLimits.MAX_CSV_EXPORT_ROWS_PER_MONTH, vm.export().size)
        assertFalse(viewModel(pro = false).csvAccess.value.canStartTrial)
    }

    @Test
    fun `an export made during the trial but run after it ends is capped`() = runTest {
        val vm = viewModel(pro = false)
        vm.startCsvTrial()
        val export = vm.exportTransactions(rows)

        clock += TimeUnit.DAYS.toMillis(7)
        export.collect {}
        assertEquals(FreeTierLimits.MAX_CSV_EXPORT_ROWS_PER_MONTH, exported.single().size)
    }

    @Test
    fun `the analytics trial does not open CSV export`() = runTest {
        trials.start(TrialFeature.ANALYTICS_GROUPING, isPro = false)
        val vm = viewModel(pro = false)
        assertEquals(FreeTierLimits.MAX_CSV_EXPORT_ROWS_PER_MONTH, vm.export().size)
        assertTrue(vm.csvAccess.value.canStartTrial)
    }

    @Test
    fun `while Pro is still loading no CSV trial is offered or started`() {
        val vm = viewModel(pro = null)
        assertFalse(vm.csvAccess.value.canStartTrial)
        vm.startCsvTrial()
        assertEquals(TrialStatus.NotStarted, trials.status(TrialFeature.CSV_EXPORT))
    }

    @Test
    fun `Pro exports every row and sees no trial`() = runTest {
        val vm = viewModel(pro = true)
        assertEquals(rows, vm.export())
        assertFalse(vm.csvAccess.value.canStartTrial)
        assertEquals(null, vm.csvAccess.value.trialDaysLeft)
    }
}
