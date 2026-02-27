package com.pennywiseai.tracker.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.components.PennyWiseCard
import com.pennywiseai.tracker.ui.components.SectionHeader
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

data class FAQItem(
    val question: String,
    val answer: String
)

data class FAQCategory(
    val title: String,
    val icon: @Composable () -> Unit,
    val items: List<FAQItem>
)

@Composable
fun FAQScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val faqCategories = remember {
        listOf(
            FAQCategory(
                title = "Transaction Types",
                icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                items = listOf(
                    FAQItem(
                        question = "Why are wallet transactions marked as Credit?",
                        answer = "Wallet transactions (Amazon Pay, Paytm, etc.) are marked as Credit because they're charged to your bank account or credit card first, not direct bank debits. This helps track the actual payment method used."
                    ),
                    FAQItem(
                        question = "What's the difference between the 5 transaction types?",
                        answer = """• Expense: Money going out of your account (debits, purchases, bill payments)
• Income: Money coming into your account (salary, refunds, cashback)
• Investment: Mutual funds, stocks, SIPs, trading accounts
• Credit: Credit card transactions and wallet payments (money you'll pay later)
• Transfer: Money moved between your own accounts (self-transfers)"""
                    ),
                    FAQItem(
                        question = "When should I use Transfer vs Expense?",
                        answer = "Use Transfer when moving money between your own accounts (e.g., savings to checking). These don't affect your net worth. Use Expense for actual spending."
                    )
                )
            ),
            FAQCategory(
                title = "SMS Parsing",
                icon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) },
                items = listOf(
                    FAQItem(
                        question = "Why aren't my bank SMS being detected?",
                        answer = "Check if your bank is supported in our list. If not, report it via GitHub. Ensure SMS permissions are granted and the sender format matches standard bank SMS patterns."
                    ),
                    FAQItem(
                        question = "What happens to unrecognized SMS?",
                        answer = "They're saved in 'Unrecognized Messages' where you can manually review them or report them to help us improve parsing."
                    ),
                    FAQItem(
                        question = "Why are some transactions duplicated?",
                        answer = "Some banks send multiple SMS for the same transaction. The app tries to detect duplicates, but you can manually delete any that slip through."
                    )
                )
            ),
            FAQCategory(
                title = "Privacy & Data",
                icon = { Icon(Icons.Default.Security, contentDescription = null) },
                items = listOf(
                    FAQItem(
                        question = "Is my financial data secure?",
                        answer = "Yes! All data stays on your device. We don't have servers or cloud storage. The AI model runs locally for complete privacy."
                    ),
                    FAQItem(
                        question = "Can I backup my data?",
                        answer = "Currently, data is stored locally only. Export/backup features are planned for future updates."
                    ),
                    FAQItem(
                        question = "What data does the app access?",
                        answer = "Only SMS messages from known bank senders. We don't read personal messages or access other app data."
                    )
                )
            ),
            FAQCategory(
                title = "AI Features",
                icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                items = listOf(
                    FAQItem(
                        question = "Why do I need to download the AI model?",
                        answer = "The 750MB model enables on-device chat about your expenses without sending data to any server, ensuring complete privacy."
                    ),
                    FAQItem(
                        question = "What can I ask the AI assistant?",
                        answer = "You can ask about spending patterns, budget advice, transaction summaries, and general financial questions based on your data."
                    )
                )
            ),
            FAQCategory(
                title = "Account Management",
                icon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                items = listOf(
                    FAQItem(
                        question = "What are manual accounts?",
                        answer = "Manual accounts let you track cash, investments, or accounts from unsupported banks. You update balances manually."
                    ),
                    FAQItem(
                        question = "How do I track multiple accounts from the same bank?",
                        answer = "The app automatically detects different accounts based on the last 4 digits shown in SMS."
                    )
                )
            )
        )
    }
    
    var expandedCategories by remember { mutableStateOf(setOf<Int>()) }
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // FAQ Categories
            faqCategories.forEachIndexed { categoryIndex, category ->
                SectionHeader(title = category.title)
                
                PennyWiseCard(
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
                                                modifier = Modifier.size(24.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                category.icon()
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.width(24.dp))
                                        }
                                        
                                        Text(
                                            text = faqItem.question,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
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
                                            text = faqItem.answer,
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
            SectionHeader(title = "Still Need Help?")
            
            PennyWiseCard(
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
                                text = "Report an Issue",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Submit bug reports, bank requests, or feature improvements on GitHub",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(Spacing.lg))
    }
}}