package com.pennywiseai.tracker.domain.usecase

import android.util.Log
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class AddSubscriptionUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository
) {
    suspend fun execute(
        merchantName: String,
        amount: BigDecimal,
        nextPaymentDate: LocalDate,
        billingCycle: String,
        category: String,
        autoRenewal: Boolean = true,
        paymentReminder: Boolean = true,
        notes: String? = null,
        currency: String = "INR"
    ): Long {
        Log.d("AddSubscriptionUseCase", "Creating subscription entity...")
        
        val subscription = SubscriptionEntity(
            merchantName = merchantName,
            amount = amount,
            nextPaymentDate = nextPaymentDate,
            state = SubscriptionState.ACTIVE, // Always active for manually added subscriptions
            bankName = "Manual Entry",
            category = category,
            currency = currency,
            smsBody = notes, // Store user notes in smsBody field
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        Log.d("AddSubscriptionUseCase", "Subscription entity created: $subscription")
        Log.d("AddSubscriptionUseCase", "Calling repository.insertSubscription...")
        
        val id = subscriptionRepository.insertSubscription(subscription)
        Log.d("AddSubscriptionUseCase", "Subscription inserted with ID: $id")
        
        return id
    }
}