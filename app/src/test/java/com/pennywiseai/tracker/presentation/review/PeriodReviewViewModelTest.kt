package com.pennywiseai.tracker.presentation.review

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.export.PdfReportExporter
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.ReviewRange
import com.pennywiseai.tracker.utils.inAppLanguage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodReviewViewModelTest {
    private val loader = mockk<PeriodReviewLoader>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { loader.observe(any(), any(), any()) } returns emptyFlow()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private val exporter = mockk<PdfReportExporter>()

    private fun gate(pro: Boolean) = mockk<EntitlementGate> { every { isProEntitled } returns MutableStateFlow(pro) }

    /** A view model showing a loaded year, with a working exporter, for [block]. */
    private fun withExportReady(pro: Boolean, block: (PeriodReviewViewModel) -> Unit) = runTest {
        val year = LocalDate.of(2026, 1, 1)
        val shown = PeriodReview(
            range = ReviewRange(ReviewKind.YEAR, year, year.plusYears(1).minusDays(1), year.minusYears(1), year.minusDays(1), isComplete = true),
            currencies = emptyList(),
            firstRecorded = null,
            lastRecorded = null,
            hasOlder = false,
        )
        every { loader.observe(any(), any(), any()) } returns flowOf(shown)
        coEvery { exporter.export(any(), any(), any(), any(), any()) } returns mockk<Uri>()
        val preferences = mockk<UserPreferencesRepository>()
        every { preferences.selectedProfileId } returns flowOf(null)
        mockkStatic("com.pennywiseai.tracker.utils.AppLanguageKt")
        try {
            every { any<Context>().inAppLanguage() } returns mockk(relaxed = true)
            val viewModel = PeriodReviewViewModel(SavedStateHandle(), loader, exporter, mockk(), preferences, mockk(), gate(pro))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.review.collect {} }
            block(viewModel)
        } finally {
            unmockkStatic("com.pennywiseai.tracker.utils.AppLanguageKt")
        }
    }

    @Test
    fun `a second tap while the share sheet is open writes no second file`() = withExportReady(pro = true) { viewModel ->
        viewModel.exportPdf("2026")
        viewModel.exportPdf("2026")
        coVerify(exactly = 1) { exporter.export(any(), any(), any(), any(), any()) }

        viewModel.pdfShareClosed()
        viewModel.exportPdf("2026")
        coVerify(exactly = 2) { exporter.export(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `without Pro no report is written`() = withExportReady(pro = false) { viewModel ->
        viewModel.exportPdf("2026")
        coVerify(exactly = 0) { exporter.export(any(), any(), any(), any(), any()) }
        assertEquals(false, viewModel.exportingPdf.value)
    }

    @Test
    fun `a restored period stays the one it was, whatever the date is now`() = runTest {
        val chosenOn = LocalDate.of(2026, 1, 15)
        val state = SavedStateHandle(
            mapOf("selectedKind" to "WEEK", "selectedOffset" to -3, "selectedToday" to chosenOn.toEpochDay())
        )
        val viewModel = PeriodReviewViewModel(state, loader, mockk(), mockk(), mockk(), mockk(), gate(pro = true))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.review.collect {} }

        assertEquals(ReviewSelection(ReviewKind.WEEK, -3, chosenOn), viewModel.selection.value)
        verify { loader.observe(ReviewKind.WEEK, -3, chosenOn) }
    }

    @Test
    fun `moving between periods keeps counting from the same day and saves it`() {
        val chosenOn = LocalDate.of(2026, 1, 15)
        val state = SavedStateHandle(
            mapOf("selectedKind" to "WEEK", "selectedOffset" to -3, "selectedToday" to chosenOn.toEpochDay())
        )
        val viewModel = PeriodReviewViewModel(state, loader, mockk(), mockk(), mockk(), mockk(), gate(pro = true))

        viewModel.older()

        assertEquals(ReviewSelection(ReviewKind.WEEK, -4, chosenOn), viewModel.selection.value)
        assertEquals(chosenOn.toEpochDay(), state.get<Long>("selectedToday"))
    }
}
