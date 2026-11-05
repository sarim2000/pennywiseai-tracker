package com.pennywiseai.tracker.navigation

import com.pennywiseai.tracker.ui.theme.Spacing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import com.pennywiseai.tracker.ui.theme.Dimensions
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.encodeToSavedState
import com.pennywiseai.tracker.ui.LocalNavAnimatedVisibilityScope
import com.pennywiseai.tracker.ui.LocalSharedTransitionScope
import com.pennywiseai.tracker.ui.MainScreen
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel
import kotlinx.serialization.serializer

/** navigate(key) { launchSingleTop = true }: the same kind of screen on top is replaced, not stacked. */
fun NavBackStack<NavKey>.navigate(key: NavKey) {
    val top = lastOrNull()
    when {
        top == key -> Unit
        top != null && top::class == key::class -> set(lastIndex, key)
        else -> add(key)
    }
}

/**
 * Back from the screen [key]: pops only while it's still on top, so a double tap
 * on a back arrow can't pop the screen under it too (was safePopBackStack).
 */
fun NavBackStack<NavKey>.pop(key: NavKey) {
    if (size > 1 && lastOrNull() == key) removeAt(lastIndex)
}

/** popUpTo(the last screen matching [match]) { inclusive = true }, then [key] in its place. */
fun NavBackStack<NavKey>.replaceLast(key: NavKey, match: (NavKey) -> Boolean) {
    val i = indexOfLast(match)
    if (i < 0) return navigate(key)
    subList(i + 1, size).clear()
    set(i, key)
}

/** Per-entry saveable state and ViewModelStore, so ViewModels stay scoped to their entry. */
@Composable
fun rememberEntryDecorators(): List<NavEntryDecorator<NavKey>> = listOf(
    rememberSaveableStateHolderNavEntryDecorator(),
    rememberViewModelStoreNavEntryDecorator()
)

/**
 * Hands each key's properties to its entry's ViewModels as SavedStateHandle
 * arguments, as navigation-compose did, so `savedStateHandle.get<Long>("goalId")`
 * keeps working. Replaces the inherited default args rather than merging them,
 * or the activity's intent extras would leak into every screen's handle.
 */
fun withRouteArgs(provider: (NavKey) -> NavEntry<NavKey>): (NavKey) -> NavEntry<NavKey> = { key ->
    val entry = provider(key)
    NavEntry(navEntry = entry) { k: NavKey ->
        RouteArgs(k) {
            // Phone layouts stretched across a tablet put amounts a screen-width away from
            // what they belong to, so every screen keeps a readable, centred column. The
            // root Home entry is the tab shell, which stays full width for its rail; the Home
            // tab inside it caps itself.
            // ponytail: one centred column plus a rail; two-pane screens if tablets matter.
            if (k == Home) entry.Content()
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.fillMaxHeight().widthIn(max = Dimensions.Component.contentMaxWidth)) {
                    entry.Content()
                }
            }
        }
    }
}

@Composable
private fun RouteArgs(key: NavKey, content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner" }
    val owner = remember(parent, key) {
        val args = encodeToSavedState(serializer(key.javaClass), key)
        val defaults = parent as? HasDefaultViewModelProviderFactory
        object : ViewModelStoreOwner by parent, HasDefaultViewModelProviderFactory {
            override val defaultViewModelProviderFactory =
                defaults?.defaultViewModelProviderFactory ?: SavedStateViewModelFactory()
            override val defaultViewModelCreationExtras: CreationExtras
                get() = MutableCreationExtras(defaults?.defaultViewModelCreationExtras ?: CreationExtras.Empty)
                    .apply { set(DEFAULT_ARGS_KEY, args) }
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

// M3 Expressive motion: spatial spring for the slide, effects spring for the
// fade, both from the theme's MotionScheme.
private fun enterSlide(m: MotionScheme): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    fadeIn(m.defaultEffectsSpec()) + slideInVertically(m.defaultSpatialSpec()) { it / 4 } togetherWith
        fadeOut(m.fastEffectsSpec())
}
private fun popSlide(m: MotionScheme): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    fadeIn(m.defaultEffectsSpec()) togetherWith
        fadeOut(m.fastEffectsSpec()) + slideOutVertically(m.fastSpatialSpec()) { it / 4 }
}

/** Home and Transaction detail cross-fade both ways (shared-element transaction rows). */
private fun fadeOnly(m: MotionScheme): Map<String, Any> {
    fun fade() = fadeIn(m.defaultEffectsSpec()) togetherWith fadeOut(m.fastEffectsSpec())
    return NavDisplay.transitionSpec { fade() } +
        NavDisplay.popTransitionSpec { fade() } +
        NavDisplay.predictivePopTransitionSpec { fade() }
}

@Composable
fun PennyWiseNavHost(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
    themeViewModel: ThemeViewModel = hiltViewModel(),
    onEditComplete: () -> Unit = {}
) {
    val motion = MaterialTheme.motionScheme
    val enterSlide = remember(motion) { enterSlide(motion) }
    val popSlide = remember(motion) { popSlide(motion) }
    val fadeOnly = remember(motion) { fadeOnly(motion) }
    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        entryDecorators = rememberEntryDecorators(),
        transitionSpec = enterSlide,
        popTransitionSpec = popSlide,
        predictivePopTransitionSpec = { popSlide(this) },
        entryProvider = withRouteArgs(entryProvider {
        // At once: a fade-in shows the screen it's locking through it.
        entry<AppLock>(metadata = NavDisplay.transitionSpec { EnterTransition.None togetherWith fadeOut(motion.fastEffectsSpec()) }) {
            com.pennywiseai.tracker.ui.screens.AppLockScreen(
                onUnlocked = {
                    // Back to the screen it covered. Pops AppLock itself, so a repeat
                    // call (a second success mid-transition) doesn't pop that screen too.
                    val i = backStack.lastIndexOf(AppLock)
                    if (i > 0) {
                        backStack.subList(i, backStack.size).clear()
                    } else if (i == 0) {
                        backStack.replaceLast(Home) { it == AppLock }
                    }
                }
            )
        }
        entry<OnBoarding> {
            com.pennywiseai.tracker.ui.screens.onboarding.OnBoardingScreen(
                onOnboardingComplete = {
                    backStack.replaceLast(Home) { it == OnBoarding }
                }
            )
        }
        entry<Home>(metadata = fadeOnly) {
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current) {
                MainScreen(
                    rootBackStack = backStack
                )
            }
        }

        entry<Settings> { key ->
            com.pennywiseai.tracker.ui.screens.settings.SettingsScreen(
                themeViewModel = themeViewModel,
                onNavigateBack = {
                    backStack.pop(key)
                },
                onNavigateToCategories = {
                    backStack.navigate(Categories)
                },
                onNavigateToUnrecognizedSms = {
                    backStack.navigate(UnrecognizedSms)
                },
                onNavigateToFaq = {
                    backStack.navigate(Faq)
                },
                onNavigateToBudgets = {
                    backStack.navigate(BudgetGroups())
                },
                onNavigateToExchangeRates = {
                    backStack.navigate(ExchangeRates)
                },
                onNavigateToImportStatement = {
                    backStack.navigate(ImportStatement)
                },
                onNavigateToTransactionGroups = {
                    backStack.navigate(TransactionGroups)
                },
                onNavigateToWebhooks = {
                    backStack.navigate(Webhooks)
                },
                onNavigateToAiAccess = {
                    backStack.navigate(AiAccess)
                }
            )
        }

        entry<AiAccess> { key ->
            com.pennywiseai.tracker.ui.screens.settings.aiaccess.AiAccessScreen(
                onNavigateBack = { backStack.pop(key) }
            )
        }

        entry<Webhooks> { key ->
            com.pennywiseai.tracker.ui.screens.settings.webhooks.WebhooksScreen(
                onNavigateBack = { backStack.pop(key) }
            )
        }

        entry<Categories> { key ->
            com.pennywiseai.tracker.presentation.categories.CategoriesScreen(
                onNavigateBack = {
                    backStack.pop(key)
                }
            )
        }

        entry<TransactionDetail>(metadata = fadeOnly) { key ->
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current) {
                com.pennywiseai.tracker.presentation.transactions.TransactionDetailScreen(
                    transactionId = key.transactionId,
                    onNavigateBack = {
                        onEditComplete()
                        backStack.pop(key)
                    },
                    onNavigateToLoanDetail = { loanId ->
                        backStack.navigate(LoanDetail(loanId))
                    },
                    onDuplicateTransaction = { sourceId ->
                        backStack.navigate(AddTransaction(sourceTransactionId = sourceId))
                    },
                    onNavigateToGoal = { goalId ->
                        backStack.navigate(GoalDetail(goalId))
                    },
                    onCreateGoal = {
                        backStack.navigate(GoalEdit())
                    }
                )
            }
        }

        entry<AddTransaction> { key ->
            com.pennywiseai.tracker.presentation.add.AddScreen(
                onNavigateBack = {
                    backStack.pop(key)
                }
            )
        }

        entry<UnrecognizedSms> { key ->
            com.pennywiseai.tracker.ui.screens.unrecognized.UnrecognizedSmsScreen(
                onNavigateBack = {
                    backStack.pop(key)
                },
                onAddSuggestion = { draft ->
                    backStack.navigate(draft)
                }
            )
        }

        entry<Faq> { key ->
            com.pennywiseai.tracker.ui.screens.settings.FAQScreen(
                onNavigateBack = {
                    backStack.pop(key)
                }
            )
        }

        entry<Rules> { key ->
            com.pennywiseai.tracker.ui.screens.rules.RulesScreen(
                onNavigateBack = {
                    backStack.pop(key)
                },
                onNavigateToCreateRule = {
                    backStack.navigate(CreateRule())
                },
                onNavigateToEditRule = { ruleId ->
                    backStack.navigate(CreateRule(ruleId = ruleId))
                },
                onNavigateToDuplicateRule = { ruleId ->
                    backStack.navigate(CreateRule(duplicateFromId = ruleId))
                }
            )
        }

        entry<CreateRule> { createRule ->
            val rulesViewModel: com.pennywiseai.tracker.ui.viewmodel.RulesViewModel = hiltViewModel()

            // Collect rules from the flow to find the rule being edited or duplicated.
            val rules by rulesViewModel.rules.collectAsStateWithLifecycle()
            val isEditing = createRule.ruleId != null

            // Stable id for a duplicate, kept across recompositions/config changes.
            val duplicateId = rememberSaveable { java.util.UUID.randomUUID().toString() }

            // Edit prefills from the saved rule; duplicate prefills from the source but
            // becomes a fresh rule (new id, "(copy)" name) so saving inserts a new one.
            val prefillRule = when {
                createRule.ruleId != null ->
                    rules.firstOrNull { it.id == createRule.ruleId }
                createRule.duplicateFromId != null ->
                    rules.firstOrNull { it.id == createRule.duplicateFromId }?.let { source ->
                        source.copy(
                            id = duplicateId,
                            name = "${source.name} (copy)",
                            isSystemTemplate = false
                        )
                    }
                else -> null
            }
            val accounts by rulesViewModel.accounts.collectAsStateWithLifecycle()

            com.pennywiseai.tracker.ui.screens.rules.CreateRuleScreen(
                onNavigateBack = {
                    backStack.pop(createRule)
                },
                onSaveRule = { rule ->
                    // Edit updates in place; create/duplicate inserts a new rule.
                    if (isEditing) {
                        rulesViewModel.updateRule(rule)
                    } else {
                        rulesViewModel.createRule(rule)
                    }
                    backStack.pop(createRule)
                },
                existingRule = prefillRule,
                isEditing = isEditing,
                allAccounts = accounts
            )
        }

        entry<AccountDetail> { key ->
            com.pennywiseai.tracker.presentation.accounts.AccountDetailScreen(
                onNavigateBack = { backStack.pop(key) },
                onTransactionClick = { id ->
                    backStack.navigate(TransactionDetail(id))
                }
            )
        }

        entry<BudgetGroups> { key ->
            com.pennywiseai.tracker.presentation.budgetgroups.BudgetGroupsScreen(
                createGoalOnOpen = key.createGoal,
                onNavigateBack = {
                    backStack.pop(key)
                },
                onNavigateToGroupEdit = { groupId ->
                    backStack.navigate(BudgetGroupEdit(groupId))
                },
                onNavigateToHistory = { groupId, year, month ->
                    backStack.navigate(BudgetHistory(groupId, year, month))
                },
                onNavigateToCategory = { category, yearMonth, currency ->
                    backStack.navigate(TransactionsWithFilter(category, yearMonth, currency))
                },
                onNavigateToGoal = { goalId ->
                    backStack.navigate(GoalDetail(goalId))
                },
                onNavigateToGoalEdit = { goalId ->
                    backStack.navigate(GoalEdit(goalId))
                }
            )
        }

        entry<GoalDetail> { key ->
            com.pennywiseai.tracker.presentation.goals.GoalDetailScreen(
                onNavigateBack = { backStack.pop(key) },
                onEdit = { goalId -> backStack.navigate(GoalEdit(goalId)) },
                onOpenTransaction = { transactionId ->
                    backStack.navigate(TransactionDetail(transactionId))
                },
                // Repeat for next year opens the new copy in place of this one.
                onOpenGoal = { goalId ->
                    backStack.replaceLast(GoalDetail(goalId)) { it is GoalDetail }
                }
            )
        }

        entry<GoalEdit> { key ->
            com.pennywiseai.tracker.presentation.goals.GoalEditScreen(
                onNavigateBack = { backStack.pop(key) }
            )
        }

        entry<BudgetGroupEdit> { key ->
            com.pennywiseai.tracker.presentation.budgetgroups.BudgetGroupEditScreen(
                onNavigateBack = {
                    backStack.pop(key)
                }
            )
        }

        entry<BudgetHistory> { key ->
            com.pennywiseai.tracker.presentation.budgetgroups.BudgetHistoryScreen(
                onNavigateBack = { backStack.pop(key) }
            )
        }

        entry<Loans> { key ->
            com.pennywiseai.tracker.presentation.loans.LoansScreen(
                onNavigateBack = {
                    backStack.pop(key)
                },
                onNavigateToLoanDetail = { loanId ->
                    backStack.navigate(LoanDetail(loanId))
                }
            )
        }

        entry<RecurringTransactions> { key ->
            com.pennywiseai.tracker.presentation.recurring.RecurringTransactionsScreen(
                onNavigateBack = {
                    backStack.pop(key)
                }
            )
        }

        entry<LoanDetail> { key ->
            com.pennywiseai.tracker.presentation.loans.LoanDetailScreen(
                onNavigateBack = {
                    backStack.pop(key)
                },
                onNavigateToTransactionDetail = { txId ->
                    backStack.navigate(TransactionDetail(txId))
                }
            )
        }

        entry<TransactionGroups> { key ->
            com.pennywiseai.tracker.presentation.groups.TransactionGroupsScreen(
                onNavigateBack = {
                    backStack.pop(key)
                },
                onNavigateToGroupDetail = { groupId ->
                    backStack.navigate(TransactionGroupDetail(groupId))
                }
            )
        }

        entry<TransactionGroupDetail> { key ->
            com.pennywiseai.tracker.presentation.groups.TransactionGroupDetailScreen(
                onNavigateBack = {
                    backStack.pop(key)
                },
                onNavigateToTransactionDetail = { txId ->
                    backStack.navigate(TransactionDetail(txId))
                }
            )
        }

        entry<ExchangeRates> { key ->
            com.pennywiseai.tracker.presentation.exchangerates.ExchangeRatesScreen(
                onNavigateBack = {
                    backStack.pop(key)
                }
            )
        }

        entry<ImportStatement> { key ->
            com.pennywiseai.tracker.presentation.statement.ImportStatementScreen(
                onNavigateBack = {
                    backStack.pop(key)
                }
            )
        }

        entry<TransactionsWithFilter> { args ->
            com.pennywiseai.tracker.presentation.transactions.TransactionsScreen(
                initialCategory = args.category,
                initialPeriod = args.period,
                initialCurrency = args.currency,
                onNavigateBack = { backStack.pop(args) },
                onTransactionClick = { transactionId ->
                    backStack.navigate(TransactionDetail(transactionId))
                },
                onAddTransactionClick = {
                    backStack.navigate(AddTransaction())
                },
                onNavigateToSettings = {
                    backStack.navigate(Settings)
                }
            )
        }
        })
    )
    // Rows fade out above the gesture bar on every screen, instead of text running into it.
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars.add(WindowInsets(bottom = Spacing.sm)))
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.7f to MaterialTheme.colorScheme.background,
                    1f to MaterialTheme.colorScheme.background
                )
            )
    )
    }
    }
    }
}
