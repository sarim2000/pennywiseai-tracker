package com.pennywiseai.tracker.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.pennywiseai.tracker.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

data class FAQItem(
    @StringRes val question: Int,
    @StringRes val answer: Int
)

data class FAQCategory(
    @StringRes val title: Int,
    val icon: @Composable () -> Unit,
    val items: List<FAQItem>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FAQScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val faqCategories = remember {
        listOf(
            FAQCategory(
                title = R.string.faq_transaction_types_section,
                icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                items = listOf(
                    FAQItem(
                        question = R.string.faq_wallet_credit_question,
                        answer = R.string.faq_wallet_credit_answer
                    ),
                    FAQItem(
                        question = R.string.faq_transaction_types_question,
                        answer = R.string.faq_transaction_types_answer
                    ),
                    FAQItem(
                        question = R.string.faq_transfer_vs_expense_question,
                        answer = R.string.faq_transfer_vs_expense_answer
                    )
                )
            ),
            FAQCategory(
                title = R.string.faq_sms_parsing_section,
                icon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) },
                items = listOf(
                    FAQItem(
                        question = R.string.faq_sms_not_detected_question,
                        answer = R.string.faq_sms_not_detected_answer
                    ),
                    FAQItem(
                        question = R.string.faq_unrecognized_sms_question,
                        answer = R.string.faq_unrecognized_sms_answer
                    ),
                    FAQItem(
                        question = R.string.faq_duplicates_question,
                        answer = R.string.faq_duplicates_answer
                    )
                )
            ),
            FAQCategory(
                title = R.string.faq_privacy_section,
                icon = { Icon(Icons.Default.Security, contentDescription = null) },
                items = listOf(
                    FAQItem(
                        question = R.string.faq_data_secure_question,
                        answer = R.string.faq_data_secure_answer
                    ),
                    FAQItem(
                        question = R.string.faq_backup_question,
                        answer = R.string.faq_backup_answer
                    ),
                    FAQItem(
                        question = R.string.faq_data_access_question,
                        answer = R.string.faq_data_access_answer
                    )
                )
            ),
            FAQCategory(
                title = R.string.faq_ai_section,
                icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                items = listOf(
                    FAQItem(
                        question = R.string.faq_ai_download_question,
                        answer = R.string.faq_ai_download_answer
                    ),
                    FAQItem(
                        question = R.string.faq_ai_ask_question,
                        answer = R.string.faq_ai_ask_answer
                    )
                )
            ),
            FAQCategory(
                title = R.string.faq_accounts_section,
                icon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                items = listOf(
                    FAQItem(
                        question = R.string.faq_manual_accounts_question,
                        answer = R.string.faq_manual_accounts_answer
                    ),
                    FAQItem(
                        question = R.string.faq_multiple_accounts_question,
                        answer = R.string.faq_multiple_accounts_answer
                    )
                )
            )
        )
    }
    
    var expandedCategories by remember { mutableStateOf(setOf<Int>()) }

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.faq_title),
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.faq_back))
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // FAQ Categories
            faqCategories.forEachIndexed { categoryIndex, category ->
                SectionHeaderV2(title = stringResource(category.title))
                
                PennyWiseCardV2(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        category.items.forEachIndexed { itemIndex, faqItem ->
                            val isExpanded = expandedCategories.contains(categoryIndex * 100 + itemIndex)
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCategories = if (isExpanded) {
                                            expandedCategories - (categoryIndex * 100 + itemIndex)
                                        } else {
                                            expandedCategories + (categoryIndex * 100 + itemIndex)
                                        }
                                    }
                                    .padding(Dimensions.Padding.content)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                    ) {
                                        if (itemIndex == 0) {
                                            Box(
                                                modifier = Modifier.size(Dimensions.Icon.medium),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                category.icon()
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.width(Dimensions.Icon.medium))
                                        }
                                        
                                        Text(
                                            text = stringResource(faqItem.question),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) stringResource(R.string.faq_collapse) else stringResource(R.string.faq_expand),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Spacer(modifier = Modifier.height(Spacing.sm))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                    ) {
                                        Spacer(modifier = Modifier.width(24.dp))
                                        Text(
                                            text = stringResource(faqItem.answer),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = Spacing.sm)
                                        )
                                    }
                                }
                            }
                            
                            if (itemIndex < category.items.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                                )
                            }
                        }
                    }
                }
            }
            
            // Still need help section
            SectionHeaderV2(title = stringResource(R.string.faq_still_need_help_section))
            
            PennyWiseCardV2(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sarim2000/pennywiseai-tracker/issues/new/choose"))
                        context.startActivity(intent)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimensions.Padding.content),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.faq_report_issue_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.faq_report_issue_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.medium),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}