package com.pennywiseai.tracker.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()
    
    // Categories list
    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Dialog states
    private val _showAddEditDialog = MutableStateFlow(false)
    val showAddEditDialog: StateFlow<Boolean> = _showAddEditDialog.asStateFlow()
    
    private val _editingCategory = MutableStateFlow<CategoryEntity?>(null)
    val editingCategory: StateFlow<CategoryEntity?> = _editingCategory.asStateFlow()
    
    // Snackbar message
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()
    
    fun showAddDialog() {
        _editingCategory.value = null
        _showAddEditDialog.value = true
    }
    
    fun showEditDialog(category: CategoryEntity) {
        if (!category.isSystem) {
            _editingCategory.value = category
            _showAddEditDialog.value = true
        } else {
            _snackbarMessage.value = "System categories cannot be edited"
        }
    }
    
    fun hideDialog() {
        _showAddEditDialog.value = false
        _editingCategory.value = null
    }
    
    fun saveCategory(
        name: String,
        color: String,
        isIncome: Boolean
    ) {
        viewModelScope.launch {
            try {
                val editingCat = _editingCategory.value
                
                if (editingCat != null) {
                    // Update existing category
                    categoryRepository.updateCategory(
                        editingCat.copy(
                            name = name,
                            color = color,
                            isIncome = isIncome
                        )
                    )
                    _snackbarMessage.value = "Category updated successfully"
                } else {
                    // Check if category already exists
                    if (categoryRepository.categoryExists(name)) {
                        _snackbarMessage.value = "Category '$name' already exists"
                        return@launch
                    }
                    
                    // Create new category
                    categoryRepository.createCategory(
                        name = name,
                        color = color,
                        isIncome = isIncome
                    )
                    _snackbarMessage.value = "Category created successfully"
                }
                
                hideDialog()
            } catch (e: Exception) {
                _snackbarMessage.value = "Error saving category: ${e.message}"
            }
        }
    }
    
    fun deleteCategory(category: CategoryEntity) {
        if (category.isSystem) {
            _snackbarMessage.value = "System categories cannot be deleted"
            return
        }
        
        viewModelScope.launch {
            try {
                val deleted = categoryRepository.deleteCategory(category.id)
                if (deleted) {
                    _snackbarMessage.value = "Category deleted successfully"
                } else {
                    _snackbarMessage.value = "Cannot delete this category"
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "Error deleting category: ${e.message}"
            }
        }
    }
    
    // One lock per category, so taps on the same row are applied one after
    // another instead of overlapping. Dropping a tap instead would lose it: two
    // quick taps have to land as two flips, or the row ends up in the opposite
    // state from what the user last asked for. Touched only from the main thread.
    private val toggleLocks = mutableMapOf<Long, Mutex>()

    /**
     * Hide or unhide a category (#736). Unlike delete, this is allowed for system
     * (default) categories — hiding is the safe way to tuck away an unused default:
     * the row stays in the DB so existing transactions keep their category, it's just
     * removed from the pickers.
     *
     * Takes only the id: the new value is worked out by the database, not by the
     * caller. The screen can only hold a snapshot of the row, and between a write
     * landing and the Room Flow reaching Compose that snapshot is stale — a
     * second tap in that window would ask for the same value again, so a quick
     * hide-then-show left the category hidden.
     *
     * Every tap flips the row exactly once. Taps on the same category queue
     * behind each other rather than being discarded, so two quick taps land as
     * two flips and the row ends up where the user's last tap asked for.
     */
    fun toggleCategoryHidden(categoryId: Long) {
        val lock = toggleLocks.getOrPut(categoryId) { Mutex() }
        viewModelScope.launch {
            lock.withLock {
                try {
                    val updated = categoryRepository.toggleCategoryHidden(categoryId)
                    if (updated != null) {
                        _snackbarMessage.value =
                            if (updated.isHidden) "${updated.name} hidden" else "${updated.name} shown"
                    }
                } catch (e: Exception) {
                    _snackbarMessage.value = "Error updating category: ${e.message}"
                }
            }
        }
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }
}

data class CategoriesUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)