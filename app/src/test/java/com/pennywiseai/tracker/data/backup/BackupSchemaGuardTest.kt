package com.pennywiseai.tracker.data.backup

import com.pennywiseai.tracker.data.database.entity.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.serializer
import org.junit.Assert.fail
import org.junit.Test

/**
 * CI guard for the backup compatibility contract.
 *
 * The forward/backward-compatibility scheme relies on a backup that omits a
 * field still decoding — the missing key falls back to a Kotlin default
 * instead of throwing `MissingFieldException`. This test fails the build (and
 * names the offender) if that property is violated.
 *
 * Two layers:
 *  1. **Wrapper models** ([backupModelDescriptors]) — EVERY field must have a
 *     default. These are pure envelope types; there is never a reason for a
 *     required field.
 *  2. **Entities** ([entityDescriptors]) — entities have legitimately-required
 *     core columns (e.g. `merchantName`) that have always been present in every
 *     backup, so we can't demand a default on all of them. Instead we pin the
 *     set of required fields to a baseline: a *new* required field (added
 *     without a default) is exactly the #414 footgun, and makes this test fail.
 *
 * See docs/backup-format.md.
 */
@OptIn(ExperimentalSerializationApi::class)
class BackupSchemaGuardTest {

    private val backupModelDescriptors: List<SerialDescriptor> = listOf(
        serializer<PennyWiseBackup>().descriptor,
        serializer<BackupMetadata>().descriptor,
        serializer<BackupStatistics>().descriptor,
        serializer<DateRange>().descriptor,
        serializer<DatabaseSnapshot>().descriptor,
        serializer<PreferencesSnapshot>().descriptor,
        serializer<ThemePreferences>().descriptor,
        serializer<SmsPreferences>().descriptor,
        serializer<DeveloperPreferences>().descriptor,
        serializer<AppPreferences>().descriptor,
    )

    /**
     * Every entity that lands in [DatabaseSnapshot]. Keep in sync with the
     * lists there — if you add a table to the backup, add its entity here too.
     */
    private val entityDescriptors: List<SerialDescriptor> = listOf(
        serializer<TransactionEntity>().descriptor,
        serializer<CategoryEntity>().descriptor,
        serializer<CardEntity>().descriptor,
        serializer<AccountBalanceEntity>().descriptor,
        serializer<SubscriptionEntity>().descriptor,
        serializer<MerchantMappingEntity>().descriptor,
        serializer<MerchantAliasEntity>().descriptor,
        serializer<UnrecognizedSmsEntity>().descriptor,
        serializer<ChatMessage>().descriptor,
        serializer<RuleEntity>().descriptor,
        serializer<RuleApplicationEntity>().descriptor,
        serializer<ExchangeRateEntity>().descriptor,
        serializer<BudgetEntity>().descriptor,
        serializer<BudgetCategoryEntity>().descriptor,
        serializer<TransactionSplitEntity>().descriptor,
        serializer<BankNotificationEntity>().descriptor,
        serializer<LoanEntity>().descriptor,
        serializer<TransactionGroupEntity>().descriptor,
        serializer<ProfileEntity>().descriptor,
        serializer<BudgetMonthSnapshotEntity>().descriptor,
        serializer<BudgetCategoryMonthSnapshotEntity>().descriptor,
        serializer<TagEntity>().descriptor,
        serializer<TransactionTagCrossRef>().descriptor,
    )

    private fun SerialDescriptor.requiredFields(): List<String> =
        (0 until elementsCount).filterNot { isElementOptional(it) }
            .map { "${serialName.substringAfterLast('.')}.${getElementName(it)}" }

    @Test
    fun everyBackupModelFieldHasADefault() {
        val offenders = backupModelDescriptors.flatMap { it.requiredFields() }
        if (offenders.isNotEmpty()) {
            fail(
                "These backup-model fields have no default value, which breaks " +
                    "backward/forward compatibility — a backup that omits them can no " +
                    "longer be imported. Give each a Kotlin default:\n  " +
                    offenders.joinToString("\n  ")
            )
        }
    }

    @Test
    fun noEntityGainsANewRequiredField() {
        val current = entityDescriptors.flatMap { it.requiredFields() }.toSortedSet()
        if (current != KNOWN_REQUIRED_ENTITY_FIELDS) {
            val added = current - KNOWN_REQUIRED_ENTITY_FIELDS
            val removed = KNOWN_REQUIRED_ENTITY_FIELDS - current
            fail(
                buildString {
                    appendLine("The set of REQUIRED (non-defaulted) backup entity fields changed.")
                    if (added.isNotEmpty()) {
                        appendLine()
                        appendLine("NEWLY REQUIRED (this BREAKS restoring older backups — give each a")
                        appendLine("Kotlin default value, e.g. `val x: T = ...`):")
                        added.forEach { appendLine("  + $it") }
                    }
                    if (removed.isNotEmpty()) {
                        appendLine()
                        appendLine("No longer required (safe — just update the baseline below):")
                        removed.forEach { appendLine("  - $it") }
                    }
                    appendLine()
                    appendLine("If the change is intentional and safe, update KNOWN_REQUIRED_ENTITY_FIELDS:")
                    current.forEach { appendLine("        \"$it\",") }
                }
            )
        }
    }

    companion object {
        /**
         * Baseline of entity fields that are required (no Kotlin default) as of
         * the current backup contract. These have always been present in every
         * backup, so their absence of a default is safe. Adding to this set is a
         * deliberate act — only do so for a field guaranteed present in every
         * backup ever written; otherwise give the field a default instead.
         */
        private val KNOWN_REQUIRED_ENTITY_FIELDS: Set<String> = sortedSetOf(
            "AccountBalanceEntity.accountLast4",
            "AccountBalanceEntity.balance",
            "AccountBalanceEntity.bankName",
            "AccountBalanceEntity.timestamp",
            "BankNotificationEntity.messageBody",
            "BankNotificationEntity.messageHash",
            "BankNotificationEntity.packageName",
            "BankNotificationEntity.postedAt",
            "BankNotificationEntity.senderAlias",
            "BudgetCategoryEntity.budgetId",
            "BudgetCategoryEntity.categoryName",
            "BudgetCategoryMonthSnapshotEntity.budgetAmount",
            "BudgetCategoryMonthSnapshotEntity.budgetId",
            "BudgetCategoryMonthSnapshotEntity.categoryName",
            "BudgetCategoryMonthSnapshotEntity.month",
            "BudgetCategoryMonthSnapshotEntity.year",
            "BudgetEntity.endDate",
            "BudgetEntity.limitAmount",
            "BudgetEntity.name",
            "BudgetEntity.periodType",
            "BudgetEntity.startDate",
            "BudgetMonthSnapshotEntity.budgetId",
            "BudgetMonthSnapshotEntity.budgetName",
            "BudgetMonthSnapshotEntity.limitAmount",
            "BudgetMonthSnapshotEntity.month",
            "BudgetMonthSnapshotEntity.year",
            "CardEntity.bankName",
            "CardEntity.cardLast4",
            "CardEntity.cardType",
            "CategoryEntity.color",
            "CategoryEntity.name",
            "ChatMessage.isUser",
            "ChatMessage.message",
            "ExchangeRateEntity.expiresAt",
            "ExchangeRateEntity.fromCurrency",
            "ExchangeRateEntity.provider",
            "ExchangeRateEntity.rate",
            "ExchangeRateEntity.toCurrency",
            "ExchangeRateEntity.updatedAt",
            "LoanEntity.direction",
            "LoanEntity.originalAmount",
            "LoanEntity.personName",
            "LoanEntity.remainingAmount",
            "MerchantAliasEntity.alias",
            "MerchantAliasEntity.merchantName",
            "MerchantMappingEntity.category",
            "MerchantMappingEntity.merchantName",
            "ProfileEntity.colorHex",
            "ProfileEntity.id",
            "ProfileEntity.name",
            "RuleApplicationEntity.appliedAt",
            "RuleApplicationEntity.fieldsModified",
            "RuleApplicationEntity.id",
            "RuleApplicationEntity.ruleId",
            "RuleApplicationEntity.ruleName",
            "RuleApplicationEntity.transactionId",
            "RuleEntity.actions",
            "RuleEntity.conditions",
            "RuleEntity.createdAt",
            "RuleEntity.description",
            "RuleEntity.id",
            "RuleEntity.isActive",
            "RuleEntity.name",
            "RuleEntity.priority",
            "RuleEntity.updatedAt",
            "SubscriptionEntity.amount",
            "SubscriptionEntity.merchantName",
            "SubscriptionEntity.nextPaymentDate",
            "TransactionEntity.amount",
            "TransactionEntity.category",
            "TransactionEntity.dateTime",
            "TransactionEntity.merchantName",
            "TransactionEntity.transactionHash",
            "TransactionEntity.transactionType",
            "TransactionGroupEntity.name",
            "TransactionSplitEntity.amount",
            "TransactionSplitEntity.category",
            "TransactionSplitEntity.transactionId",
            "UnrecognizedSmsEntity.receivedAt",
            "UnrecognizedSmsEntity.sender",
            "UnrecognizedSmsEntity.smsBody",
        )
    }
}
