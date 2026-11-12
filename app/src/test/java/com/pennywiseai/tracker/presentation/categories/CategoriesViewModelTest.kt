package com.pennywiseai.tracker.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.ui.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {
    private val scheduler = TestCoroutineScheduler()

    /** Stands in for the app-wide scope: outlives the ViewModel, owned (and cancelled) by the test. */
    private val applicationScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))

    private val store = ViewModelStore()
    private val repository = mockk<CategoryRepository> {
        every { getAllCategories() } returns flowOf(emptyList())
    }
    private lateinit var viewModel: CategoriesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        // Through a store, so the test can clear it the way leaving the screen does.
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CategoriesViewModel(repository, applicationScope) as T
        }
        viewModel = ViewModelProvider(store, factory)[CategoriesViewModel::class.java]
    }

    @After
    fun tearDown() {
        store.clear()
        applicationScope.cancel()
        Dispatchers.resetMain()
    }

    private fun category(
        id: Long,
        name: String,
        isSystem: Boolean = false,
        isIncome: Boolean = false,
        parentId: Long? = null,
    ) = CategoryEntity(id = id, name = name, color = "#336699", isSystem = isSystem, isIncome = isIncome, parentId = parentId)

    /** A database whose hide/unhide takes a moment, recording how many flips overlap. */
    private inner class SlowToggles(initial: CategoryEntity) {
        var row = initial
        var calls = 0
        var running = 0
        var maxRunning = 0

        init {
            coEvery { repository.toggleCategoryHidden(initial.id) } coAnswers {
                calls++
                running++
                maxRunning = maxOf(maxRunning, running)
                delay(100)
                row = row.copy(isHidden = !row.isHidden)
                running--
                row
            }
        }
    }

    // --- Hide / unhide

    @Test
    fun `two quick taps flip the row twice, one after the other`() = runTest(scheduler) {
        val db = SlowToggles(category(7, "Hobbies"))

        viewModel.toggleCategoryHidden(7)
        viewModel.toggleCategoryHidden(7)
        advanceUntilIdle()

        assertEquals(2, db.calls)
        assertEquals(1, db.maxRunning)
        assertFalse(db.row.isHidden)
        assertEquals(UiText.Res(R.string.categories_msg_shown, listOf("Hobbies")), viewModel.snackbarMessage.value)
    }

    @Test
    fun `taps queued before leaving the screen still land`() = runTest(scheduler) {
        val db = SlowToggles(category(7, "Hobbies"))

        viewModel.toggleCategoryHidden(7)
        viewModel.toggleCategoryHidden(7)
        viewModel.toggleCategoryHidden(7)
        store.clear()
        advanceUntilIdle()

        assertEquals(3, db.calls)
        assertTrue("the last tap asked for hidden", db.row.isHidden)
    }

    // --- Editing

    @Test
    fun `renaming onto another category's name is refused`() = runTest(scheduler) {
        val groceries = category(1, "Groceries")
        coEvery { repository.getCategoryByName("Travel") } returns category(2, "Travel")
        viewModel.showEditDialog(groceries)

        viewModel.saveCategory(name = "Travel", color = "#336699", isIncome = false)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateCategory(any(), any()) }
        assertEquals(UiText.Res(R.string.categories_msg_already_exists, listOf("Travel")), viewModel.snackbarMessage.value)
        assertTrue("the dialog stays open to pick another name", viewModel.showAddEditDialog.value)
    }

    @Test
    fun `keeping a category's own name is not a clash`() = runTest(scheduler) {
        val groceries = category(1, "Groceries")
        coEvery { repository.getCategoryByName("Groceries") } returns groceries
        coEvery { repository.updateCategory(any(), any()) } returns Unit
        viewModel.showEditDialog(groceries)

        viewModel.saveCategory(name = "Groceries", color = "#993366", isIncome = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.updateCategory(groceries.copy(color = "#993366"), "Groceries") }
        assertFalse(viewModel.showAddEditDialog.value)
    }

    @Test
    fun `editing a built-in keeps its identity, type and parent`() = runTest(scheduler) {
        val food = category(3, "Food & Dining", isSystem = true)
        val saved = slot<CategoryEntity>()
        coEvery { repository.getCategoryByName("Eating Out") } returns null
        coEvery { repository.updateCategory(capture(saved), any()) } returns Unit
        viewModel.showEditDialog(food)

        viewModel.saveCategory(name = "Eating Out", color = "#993366", isIncome = true, icon = "X", parentId = 9)
        advanceUntilIdle()

        coVerify { repository.updateCategory(any(), previousName = "Food & Dining") }
        assertEquals("Eating Out", saved.captured.name)
        assertEquals("Food & Dining", saved.captured.systemName)
        assertFalse(saved.captured.isIncome)
        assertNull(saved.captured.parentId)
        assertTrue(saved.captured.isSystem)
    }

    @Test
    fun `renaming a renamed built-in keeps its original identity`() = runTest(scheduler) {
        val eatingOut = category(3, "Eating Out", isSystem = true).copy(systemName = "Food & Dining")
        val saved = slot<CategoryEntity>()
        coEvery { repository.getCategoryByName("Dining") } returns null
        coEvery { repository.updateCategory(capture(saved), any()) } returns Unit
        viewModel.showEditDialog(eatingOut)

        viewModel.saveCategory(name = "Dining", color = "#993366", isIncome = false)
        advanceUntilIdle()

        coVerify { repository.updateCategory(any(), previousName = "Eating Out") }
        assertEquals("Dining", saved.captured.name)
        assertEquals("Food & Dining", saved.captured.systemName)
    }

    @Test
    fun `editing a user category applies the new type and parent`() = runTest(scheduler) {
        val side = category(4, "Side gigs")
        val saved = slot<CategoryEntity>()
        coEvery { repository.getCategoryByName("Freelance") } returns null
        coEvery { repository.updateCategory(capture(saved), any()) } returns Unit
        viewModel.showEditDialog(side)

        viewModel.saveCategory(name = "Freelance", color = "#993366", isIncome = true, parentId = 9)
        advanceUntilIdle()

        assertTrue(saved.captured.isIncome)
        assertEquals(9L, saved.captured.parentId)
        assertNull("a user category has no built-in identity", saved.captured.systemName)
    }
}
