package com.pennywiseai.tracker.presentation.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearnedCategoriesViewModelTest {

    /** Starts with no emission, like a Room query that hasn't returned yet. */
    private class FakeData : LearnedCategoriesData {
        val pairs = MutableStateFlow<List<LearnedCategory>?>(null)
        val removed = mutableListOf<String>()

        override fun observeLearnedCategories() = pairs.filterNotNull()
        override suspend fun removeLearnedCategory(merchantName: String) {
            removed += merchantName
            pairs.value = pairs.value?.filterNot { it.merchantName == merchantName }
        }
    }

    private val data = FakeData()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    /** Keeps the WhileSubscribed StateFlow collecting for the test's duration. */
    private fun kotlinx.coroutines.test.TestScope.subscribe(viewModel: LearnedCategoriesViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.mappings.collect {} }
    }

    @Test
    fun `mappings are null until the data source emits`() = runTest {
        val viewModel = LearnedCategoriesViewModel(data)
        subscribe(viewModel)

        assertNull(viewModel.mappings.value)
    }

    @Test
    fun `an empty data source gives an empty list, not loading`() = runTest {
        val viewModel = LearnedCategoriesViewModel(data)
        subscribe(viewModel)

        data.pairs.value = emptyList()

        assertEquals(emptyList<LearnedCategory>(), viewModel.mappings.value)
    }

    @Test
    fun `pairs come through in the data source's order`() = runTest {
        val pairs = listOf(
            LearnedCategory("Zeta Mart", "Groceries"),
            LearnedCategory("Alpha Cafe", "Food & Dining"),
            LearnedCategory("Metro Fuel", "Transportation"),
        )
        val viewModel = LearnedCategoriesViewModel(data)
        subscribe(viewModel)

        data.pairs.value = pairs

        assertEquals(pairs, viewModel.mappings.value)
    }

    @Test
    fun `remove passes the exact merchant name and the list updates`() = runTest {
        data.pairs.value = listOf(
            LearnedCategory("Test Store", "Shopping"),
            LearnedCategory("test store ", "Groceries"),
        )
        val viewModel = LearnedCategoriesViewModel(data)
        subscribe(viewModel)

        viewModel.remove("test store ")

        assertEquals(listOf("test store "), data.removed)
        assertEquals(
            listOf(LearnedCategory("Test Store", "Shopping")),
            viewModel.mappings.filterNotNull().first(),
        )
    }
}
