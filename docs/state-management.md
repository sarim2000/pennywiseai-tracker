# PennyWise State Management Guide

## Overview
PennyWise uses the modern Android state management approach with:
- **ViewModel + StateFlow** for UI state
- **DataStore** for persistent preferences
- **Unidirectional Data Flow (UDF)** pattern
- **State hoisting** for composable functions

The snippets below are trimmed for reading. The real files are
`data/preferences/UserPreferencesRepository.kt`, `ui/viewmodel/ThemeViewModel.kt`,
and `PennyWiseApp.kt` — the patterns match, the field lists don't.

## Architecture

### 1. Data Layer (Repository)
```kotlin
// data/preferences/UserPreferencesRepository.kt
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val context: Context
) {
    // DataStore for persistent storage
    private val dataStore = context.dataStore
    
    // Expose preferences as Flow
    val userPreferences: Flow<UserPreferences> = dataStore.data
        .map { preferences -> 
            // Map to domain model
        }
    
    // Update functions
    suspend fun updateDarkThemeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DARK_THEME_ENABLED] = enabled
        }
    }
}
```

### 2. ViewModel Layer
```kotlin
// ui/viewmodel/ThemeViewModel.kt
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val repository: UserPreferencesRepository
) : ViewModel() {
    
    // Convert Flow to StateFlow for UI
    val themeUiState: StateFlow<ThemeUiState> = repository.userPreferences
        .map { preferences ->
            ThemeUiState(
                isDarkTheme = preferences.isDarkThemeEnabled,
                isDynamicColorEnabled = preferences.isDynamicColorEnabled
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeUiState()
        )
    
    // User actions
    fun updateDarkTheme(enabled: Boolean?) {
        viewModelScope.launch {
            repository.updateDarkThemeEnabled(enabled ?: false)
        }
    }
}
```

The production `ThemeUiState` carries more than theme flags — theme style,
accent color, AMOLED mode, app font, blur effects. `PennyWiseApp` forwards all
of it into the `PennyWiseTheme(...)` call.

### 3. UI Layer (Composables)
```kotlin
// SettingsScreen.kt
@Composable
fun SettingsScreen(
    themeViewModel: ThemeViewModel
) {
    // Collect StateFlow with lifecycle awareness
    val themeUiState by themeViewModel.themeUiState.collectAsStateWithLifecycle()
    
    // Use state in UI
    FilterChip(
        selected = themeUiState.isDarkTheme == true,
        onClick = { themeViewModel.updateDarkTheme(true) },
        label = { Text("Dark") }
    )
}
```

## Key Patterns

### 1. Unidirectional Data Flow (UDF)
```
User Action → ViewModel → Repository → DataStore
                ↑                          ↓
                ←── StateFlow ←── Flow ←──
```

### 2. State Hoisting
For local component state:
```kotlin
@Composable
fun StatefulCounter() {
    var count by remember { mutableStateOf(0) }
    StatelessCounter(
        count = count,
        onIncrement = { count++ }
    )
}

@Composable
fun StatelessCounter(
    count: Int,
    onIncrement: () -> Unit
) {
    Button(onClick = onIncrement) {
        Text("Count: $count")
    }
}
```

### 3. App-Wide State Management
```kotlin
// PennyWiseApp.kt
@Composable
fun PennyWiseApp() {
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val themeUiState by themeViewModel.themeUiState.collectAsStateWithLifecycle()
    
    // Apply theme based on state
    val darkTheme = when (themeUiState.isDarkTheme) {
        null -> isSystemInDarkTheme() // Follow system
        else -> themeUiState.isDarkTheme
    }
    
    PennyWiseTheme(
        darkTheme = darkTheme,
        dynamicColor = themeUiState.isDynamicColorEnabled
    ) {
        // All screens inherit theme
        NavHost(...)
    }
}
```

## Best Practices

### 1. StateFlow Configuration
```kotlin
.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000), // Stop after 5s of no collectors
    initialValue = UiState()
)
```

### 2. Lifecycle-Aware Collection
Always use `collectAsStateWithLifecycle()` instead of `collectAsState()`:
```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()
```

### 3. Data Classes for UI State
```kotlin
data class HomeUiState(
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

The production `HomeUiState` in `presentation/home/HomeViewModel.kt` is far
bigger (filters, account balances, budget summaries, currency state). Keep new
fields defaulted so existing `copy()` calls stay valid.

### 4. Single Source of Truth
- Database: Room for transactions
- Preferences: DataStore for settings
- Runtime: ViewModel for UI state

## Benefits

1. **Survives Configuration Changes**: ViewModel persists across rotations
2. **Reactive UI**: Automatic recomposition on state changes
3. **Type Safety**: Kotlin data classes ensure compile-time safety
4. **Testability**: Easy to test ViewModels with mock repositories
5. **Performance**: StateFlow with `WhileSubscribed` prevents memory leaks

## Example: Complete Theme Switching Flow

1. User taps "Dark" theme chip
2. `onClick` calls `themeViewModel.updateDarkTheme(true)`
3. ViewModel launches coroutine to update repository
4. Repository updates DataStore preferences
5. DataStore emits new preferences through Flow
6. Repository maps to `UserPreferences` object
7. ViewModel converts Flow to StateFlow with `ThemeUiState`
8. UI collects StateFlow and recomposes
9. Theme changes instantly across all screens

This architecture ensures smooth, reactive theme switching that persists across app restarts.