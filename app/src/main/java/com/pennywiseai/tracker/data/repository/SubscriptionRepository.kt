package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.SubscriptionDao
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.parser.core.bank.HDFCBankParser
import com.pennywiseai.parser.core.bank.IndianBankParser
import com.pennywiseai.parser.core.bank.SBIBankParser
import com.pennywiseai.parser.core.bank.FederalBankParser
import com.pennywiseai.parser.core.MandateInfo
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton
class SubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao
) {
    
    companion object {
        private const val TAG = "SubscriptionRepository"
    }
    
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>> = 
        subscriptionDao.getAllSubscriptions()
    
    fun getActiveSubscriptions(): Flow<List<SubscriptionEntity>> =
        subscriptionDao.getActiveSubscriptions()

    fun getEndedSubscriptions(): Flow<List<SubscriptionEntity>> =
        subscriptionDao.getSubscriptionsByState(SubscriptionState.ENDED)
    
    fun getUpcomingSubscriptions(daysAhead: Int = 7): Flow<List<SubscriptionEntity>> {
        val futureDate = LocalDate.now().plusDays(daysAhead.toLong())
        return subscriptionDao.getUpcomingSubscriptions(futureDate)
    }
    
    suspend fun getSubscriptionById(id: Long): SubscriptionEntity? = 
        subscriptionDao.getSubscriptionById(id)
    
    suspend fun insertSubscription(subscription: SubscriptionEntity): Long = 
        subscriptionDao.insertSubscription(subscription)
    
    suspend fun updateSubscription(subscription: SubscriptionEntity) = 
        subscriptionDao.updateSubscription(subscription)
    
    suspend fun updateSubscriptionState(id: Long, state: SubscriptionState) = 
        subscriptionDao.updateSubscriptionState(id, state)
    
    suspend fun hideSubscription(id: Long) {
        Log.d(TAG, "Hiding subscription with ID: $id")
        updateSubscriptionState(id, SubscriptionState.HIDDEN)
    }
    
    suspend fun unhideSubscription(id: Long) =
        updateSubscriptionState(id, SubscriptionState.ACTIVE)

    /**
     * Marks a subscription as ENDED. Unlike HIDDEN, an ENDED subscription
     * is treated as terminal: new mandate SMS for the same merchant/amount
     * will not auto-reactivate it. The user can still manually reactivate
     * via [reactivateSubscription].
     */
    suspend fun markAsEnded(id: Long) {
        Log.d(TAG, "Marking subscription with ID: $id as ENDED")
        updateSubscriptionState(id, SubscriptionState.ENDED)
    }

    /**
     * Manual escape hatch: revert ENDED → ACTIVE. Same code path as
     * unhideSubscription but separated for symmetry with markAsEnded so
     * callers can express intent clearly.
     */
    suspend fun reactivateSubscription(id: Long) =
        updateSubscriptionState(id, SubscriptionState.ACTIVE)
    
    suspend fun deleteSubscription(id: Long) = 
        subscriptionDao.deleteSubscriptionById(id)
    
    /**
     * Creates or updates a subscription from HDFC E-Mandate info
     */
    suspend fun createOrUpdateFromEMandate(
        eMandateInfo: HDFCBankParser.EMandateInfo,
        bankName: String = "HDFC Bank",
        smsBody: String? = null
    ): Long = createOrUpdateFromMandate(eMandateInfo, bankName, smsBody)
    
    /**
     * Checks if a transaction matches any active subscription
     */
    suspend fun matchTransactionToSubscription(
        merchantName: String,
        amount: BigDecimal
    ): SubscriptionEntity? {
        val activeSubscription = subscriptionDao.getActiveSubscriptionByMerchant(merchantName)
        
        // Check if amounts match (with some tolerance for small variations)
        return if (activeSubscription != null && 
                   areAmountsEqual(activeSubscription.amount, amount)) {
            activeSubscription
        } else {
            null
        }
    }
    
    /**
     * Updates the next payment date after a subscription charge
     */
    suspend fun updateNextPaymentDateAfterCharge(
        subscriptionId: Long,
        chargeDate: LocalDate = LocalDate.now()
    ) {
        // Assume monthly subscription, add 30 days
        val nextDate = chargeDate.plusDays(30)
        subscriptionDao.updateNextPaymentDate(subscriptionId, nextDate)
    }
    
    
    private fun areAmountsEqual(amount1: BigDecimal, amount2: BigDecimal): Boolean {
        // Allow for small variations (up to 5%)
        val tolerance = amount1.multiply(BigDecimal("0.05"))
        val diff = amount1.subtract(amount2).abs()
        return diff <= tolerance
    }
    
    /**
     * Creates or updates a subscription from Indian Bank Mandate info
     */
    suspend fun createOrUpdateFromIndianBankMandate(
        mandateInfo: IndianBankParser.IndianMandateInfo,
        bankName: String = "Indian Bank",
        smsBody: String? = null
    ): Long = createOrUpdateFromMandate(mandateInfo, bankName, smsBody)
    
    /**
     * Creates or updates a subscription from SBI UPI-Mandate info
     */
    suspend fun createOrUpdateFromSBIMandate(
        upiMandateInfo: SBIBankParser.UPIMandateInfo,
        bankName: String = "SBI",
        smsBody: String? = null
    ): Long = createOrUpdateFromMandate(upiMandateInfo, bankName, smsBody)

    /**
     * Creates or updates a subscription from Federal Bank E-Mandate info
     */
    suspend fun createOrUpdateFromFederalBankMandate(
        mandateInfo: FederalBankParser.EMandateInfo,
        bankName: String = "Federal Bank",
        smsBody: String? = null
    ): Long = createOrUpdateFromMandate(mandateInfo, bankName, smsBody)

    /**
     * Creates or updates a subscription from any MandateInfo implementation.
     * This is the unified method that can handle mandates from any bank.
     */
    suspend fun createOrUpdateFromMandate(
        mandateInfo: MandateInfo,
        bankName: String,
        smsBody: String? = null
    ): Long {
        val nextPaymentDate = mandateInfo.nextDeductionDate?.let { dateStr ->
            try {
                // Use the date format specified by the mandate implementation
                LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(mandateInfo.dateFormat))
            } catch (e: Exception) {
                // Fallback to 30 days from now if parsing fails
                LocalDate.now().plusDays(30)
            }
        } ?: LocalDate.now().plusDays(30)

        // For banks that provide UMN (HDFC, SBI, Federal), use it as primary identifier
        val existing = if (mandateInfo.umn != null) {
            subscriptionDao.getSubscriptionByUmn(mandateInfo.umn!!)
        } else {
            // For other banks or when no UMN, use merchant and amount matching
            subscriptionDao.getSubscriptionByMerchantAndAmount(
                mandateInfo.merchant,
                mandateInfo.amount
            )
        }

        Log.d(TAG, "Unified Mandate lookup - Bank: $bankName, Merchant: ${mandateInfo.merchant}, " +
                  "Amount: ${mandateInfo.amount}, UMN: ${mandateInfo.umn}, " +
                  "Next Date: $nextPaymentDate, Existing: ${existing?.let { "ID=${it.id}, State=${it.state}, " +
                  "StoredDate=${it.nextPaymentDate}" } ?: "NOT FOUND"}")

        val subscription = if (existing != null) {
            // ENDED is terminal — the user explicitly cancelled and we never
            // auto-reactivate or overwrite. Return the ID untouched so any
            // downstream linking still resolves but the row stays cancelled.
            if (existing.state == SubscriptionState.ENDED) {
                Log.d(TAG, "Subscription ${existing.id} is ENDED — leaving untouched.")
                return existing.id
            }

            // Check if this is a hidden subscription that should be reactivated
            // Only reactivate if the new payment date is LATER than the stored date
            val shouldReactivate = existing.state == SubscriptionState.HIDDEN &&
                                  nextPaymentDate.isAfter(existing.nextPaymentDate) &&
                                  nextPaymentDate.isAfter(LocalDate.now())

            Log.d(TAG, "Subscription state check - Hidden: ${existing.state == SubscriptionState.HIDDEN}, " +
                      "New date after stored: ${nextPaymentDate.isAfter(existing.nextPaymentDate)}, " +
                      "New date is future: ${nextPaymentDate.isAfter(LocalDate.now())}, " +
                      "Should reactivate: $shouldReactivate")

            // If hidden and payment date hasn't changed, don't update
            if (existing.state == SubscriptionState.HIDDEN && !shouldReactivate) {
                // Return the existing ID without any updates
                Log.d(TAG, "Subscription ${existing.id} is HIDDEN and won't be reactivated " +
                          "(payment date not newer). Skipping update.")
                return existing.id
            }

            // Update existing subscription (reactivate if needed)
            if (shouldReactivate) {
                Log.i(TAG, "REACTIVATING subscription ${existing.id} - ${existing.merchantName} " +
                          "(old date: ${existing.nextPaymentDate}, new date: $nextPaymentDate)")
            }
            existing.copy(
                amount = mandateInfo.amount,
                nextPaymentDate = nextPaymentDate,
                merchantName = mandateInfo.merchant,
                umn = mandateInfo.umn ?: existing.umn, // Update UMN if provided
                state = if (shouldReactivate) SubscriptionState.ACTIVE else existing.state,
                smsBody = smsBody ?: existing.smsBody, // Update SMS body if provided
                updatedAt = java.time.LocalDateTime.now()
            )
        } else {
            // Create new subscription
            Log.d(TAG, "Creating NEW subscription - Bank: $bankName, Merchant: ${mandateInfo.merchant}, " +
                      "Amount: ${mandateInfo.amount}, Date: $nextPaymentDate")
            SubscriptionEntity(
                merchantName = mandateInfo.merchant,
                amount = mandateInfo.amount,
                nextPaymentDate = nextPaymentDate,
                state = SubscriptionState.ACTIVE,
                bankName = bankName,
                umn = mandateInfo.umn,
                category = determineCategory(mandateInfo.merchant),
                smsBody = smsBody
            )
        }

        return subscriptionDao.insertSubscription(subscription)
    }

    private fun determineCategory(merchantName: String): String {
        // Use unified category mapping
        return CategoryMapping.getCategory(merchantName)
    }
}