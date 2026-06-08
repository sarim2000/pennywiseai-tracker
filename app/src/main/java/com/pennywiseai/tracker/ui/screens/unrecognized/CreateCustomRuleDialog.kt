package com.pennywiseai.tracker.ui.screens.unrecognized

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.database.entity.CustomParserRuleEntity
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity

/**
 * A Jetpack Compose Dialog that lets users manually define rules for parsing unrecognized messages.
 * 
 * ## Mentorship Corner: Compose States vs. Static Variables
 * In normal Python or C, when you update a variable (e.g. `amount = "150"`), the compiler does
 * not automatically redraw the screen. You have to manually call a function to refresh the GUI.
 * 
 * Jetpack Compose uses a reactive model called **Recomposition**:
 * - `remember { mutableStateOf("") }` creates a special "observable state wrapper".
 * - When you read its value, Compose registers a dependency.
 * - When you modify its value (e.g., `amount = "250"`), Compose automatically detects the change
 *   and instantly redrafts (re-runs) the UI functions that read it.
 * - `remember` acts like a cache that saves the variable's value across these redraws (otherwise,
 *   recomposing would reset the text field back to empty!).
 */
@Composable
fun CreateCustomRuleDialog(
    unrecognizedSms: UnrecognizedSmsEntity,
    onDismiss: () -> Unit,
    onSaveRule: (CustomParserRuleEntity) -> Unit
) {
    // -------------------------------------------------------------
    // 1. STATE VARIABLES (Reactively updates the UI when modified)
    // -------------------------------------------------------------
    var ruleName by remember { mutableStateOf("${unrecognizedSms.sender} Rule") }
    var amountText by remember { mutableStateOf("") }
    var merchantText by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // -------------------------------------------------------------
    // 2. REGEX GENERATION MATH & LOGIC
    // -------------------------------------------------------------
    // This helper runs every time the state inputs change. It automatically
    // constructs a regex based on the user's marked amount and merchant substrings.
    val regexGenerationResult = remember(amountText, merchantText) {
        if (amountText.isBlank() || merchantText.isBlank()) {
            return@remember RegexResult.Pending
        }

        val rawBody = unrecognizedSms.smsBody
        
        // Find 0-indexed position of where the amount and merchant appear in the raw SMS
        val idxAmount = rawBody.indexOf(amountText)
        val idxMerchant = rawBody.indexOf(merchantText)

        // Validation: Verify both inputs actually exist in the message text
        if (idxAmount == -1) {
            return@remember RegexResult.Error("The amount '$amountText' was not found in the message text.")
        }
        if (idxMerchant == -1) {
            return@remember RegexResult.Error("The merchant '$merchantText' was not found in the message text.")
        }

        val pattern: String
        val amountGroupIndex: Int
        val merchantGroupIndex: Int

        // Step-by-Step Regex Generation Logic:
        // We split the string into 3 parts (excluding the amount and merchant).
        // Then we escape each part so that special regex symbols (like '.', '$', '?') 
        // are treated as literal text. Finally, we insert our wildcard capture groups:
        //   - "([0-9.,]+)" extracts the amount digits
        //   - Merchant wildcard with lookahead-bound to prevent over-greedy captures
        //
        // To make the generated regex robust against variable dates, times, and ending text,
        // we truncate the trailing suffix (part3) using getRobustSuffix() and append a wildcard (.*).
        val safeMerchantGroup = "((?:(?!\\b(?:bal|balance|ref|txn|transaction|debited|credited|avail|available|limit)\\b).)+?)"

        if (idxAmount < idxMerchant) {
            // Case A: Amount comes first in the text, e.g., "Paid 150.00 to Starbucks"
            val part1 = rawBody.substring(0, idxAmount)
            val part2 = rawBody.substring(idxAmount + amountText.length, idxMerchant)
            val part3 = rawBody.substring(idxMerchant + merchantText.length)
            val part3Robust = getRobustSuffix(part3)

            pattern = if (part3Robust.isNotEmpty()) {
                "^" + Regex.escape(part1) + "([0-9.,]+)" + Regex.escape(part2) + safeMerchantGroup + Regex.escape(part3Robust) + ".*$"
            } else {
                "^" + Regex.escape(part1) + "([0-9.,]+)" + Regex.escape(part2) + safeMerchantGroup + "$"
            }
            amountGroupIndex = 1
            merchantGroupIndex = 2
        } else {
            // Case B: Merchant comes first in the text, e.g., "Starbucks debited 150.00"
            val part1 = rawBody.substring(0, idxMerchant)
            val part2 = rawBody.substring(idxMerchant + merchantText.length, idxAmount)
            val part3 = rawBody.substring(idxAmount + amountText.length)
            val part3Robust = getRobustSuffix(part3)

            pattern = if (part3Robust.isNotEmpty()) {
                "^" + Regex.escape(part1) + safeMerchantGroup + Regex.escape(part2) + "([0-9.,]+)" + Regex.escape(part3Robust) + ".*$"
            } else {
                "^" + Regex.escape(part1) + safeMerchantGroup + Regex.escape(part2) + "([0-9.,]+)$"
            }
            merchantGroupIndex = 1
            amountGroupIndex = 2
        }

        RegexResult.Success(pattern, amountGroupIndex, merchantGroupIndex)
    }

    // -------------------------------------------------------------
    // 3. DIALOG UI LAYOUT
    // -------------------------------------------------------------
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Custom Parser Rule",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info Section
                Text(
                    text = "Help PennyWise learn! Tell us the exact Amount and Merchant text as they appear in the message below, and we will generate a matching template rule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Message Text Box (Display the raw text of unrecognized message)
                Text(
                    text = "Raw Message Content:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Text(
                        text = unrecognizedSms.smsBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Input Field: Rule Name
                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("Rule Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Input Field: Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Exact Amount (e.g. 150.00)") },
                    placeholder = { Text("Enter exact number from text") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Input Field: Merchant
                OutlinedTextField(
                    value = merchantText,
                    onValueChange = { merchantText = it },
                    label = { Text("Exact Merchant Name (e.g. Swiggy)") },
                    placeholder = { Text("Enter exact name from text") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Input Selection: Type (Expense vs Income)
                Text(
                    text = "Transaction Type:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = isExpense,
                        onClick = { isExpense = true },
                        label = { Text("Expense") }
                    )
                    FilterChip(
                        selected = !isExpense,
                        onClick = { isExpense = false },
                        label = { Text("Income") }
                    )
                }

                // Display Live Regex Preview / Error Warnings
                when (regexGenerationResult) {
                    is RegexResult.Success -> {
                        errorMessage = null
                        Text(
                            text = "Generated Rule Pattern Preview:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = regexGenerationResult.pattern,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is RegexResult.Error -> {
                        errorMessage = regexGenerationResult.message
                        Text(
                            text = regexGenerationResult.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    is RegexResult.Pending -> {
                        errorMessage = null
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = regexGenerationResult is RegexResult.Success && ruleName.isNotBlank(),
                onClick = {
                    val successResult = regexGenerationResult as? RegexResult.Success
                    if (successResult != null) {
                        // Create rule entity using our model constructor
                        val rule = CustomParserRuleEntity(
                            packageOrSender = unrecognizedSms.sender,
                            ruleName = ruleName.trim(),
                            regexPattern = successResult.pattern,
                            amountGroupIndex = successResult.amountGroupIndex,
                            merchantGroupIndex = successResult.merchantGroupIndex,
                            type = if (isExpense) "EXPENSE" else "INCOME"
                        )
                        onSaveRule(rule)
                    }
                }
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Represents states of the dynamic regex creation process.
 */
private sealed class RegexResult {
    data class Success(val pattern: String, val amountGroupIndex: Int, val merchantGroupIndex: Int) : RegexResult()
    data class Error(val message: String) : RegexResult()
    object Pending : RegexResult()
}

/**
 * Truncates the trailing suffix of a message to make the regex robust
 * against variable dates, times, transaction IDs, or ending text.
 * It finds the first digit in the suffix, and truncates up to that digit or
 * the first whitespace/punctuation before it to keep a stable word transition,
 * then appends a wildcard.
 *
 * ## Mentorship Corner: Suffix Truncation Math/Logic
 * If our suffix is " at 10:00 AM.", the numbers "10:00" will change in future messages.
 * If we keep them literally, the pattern will fail to match those messages.
 * So:
 * 1. Find the first digit ("1" in "10:00 AM.").
 * 2. Look at the text before it: " at ".
 * 3. Find the last word boundary or space in that text to get a clean cut-off (" at").
 * 4. Keep only that cut-off suffix and append a regex wildcard `.*$` to match anything remaining.
 */
private fun getRobustSuffix(part3: String): String {
    if (part3.isEmpty()) return ""
    
    // 1. Sentence boundary check: truncate at the first sentence boundary (". ", "! ", "? ")
    // but keep the punctuation mark itself.
    val boundaryIdx = findSentenceBoundary(part3)
    val cleanPart3 = if (boundaryIdx != -1) {
        part3.substring(0, boundaryIdx + 1)
    } else {
        part3
    }

    // 2. Digit/timestamp truncation check on the clean suffix
    val firstDigitIdx = cleanPart3.indexOfFirst { it.isDigit() }
    val limit = if (firstDigitIdx != -1) firstDigitIdx else cleanPart3.length
    
    var foundWord = false
    var stopIdx = -1
    for (i in 0 until limit) {
        val char = cleanPart3[i]
        if (char.isLetter()) {
            foundWord = true
        } else if (foundWord && (char.isWhitespace() || char == '.' || char == ',')) {
            stopIdx = i
            break
        }
    }
    val endIdx = if (stopIdx != -1) stopIdx else limit
    return cleanPart3.substring(0, endIdx)
}

private fun findSentenceBoundary(text: String): Int {
    val patterns = listOf(". ", "! ", "? ")
    var minIdx = -1
    for (p in patterns) {
        val idx = text.indexOf(p)
        if (idx != -1) {
            if (minIdx == -1 || idx < minIdx) {
                minIdx = idx
            }
        }
    }
    return minIdx
}
