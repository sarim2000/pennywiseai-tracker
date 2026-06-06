package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

@Dao
interface SubscriptionDao {
    
    @Query("SELECT * FROM subscriptions ORDER BY next_payment_date ASC")
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>
    
    @Query("SELECT * FROM subscriptions WHERE state = :state ORDER BY next_payment_date ASC")
    fun getSubscriptionsByState(state: SubscriptionState): Flow<List<SubscriptionEntity>>
    
    @Query("SELECT * FROM subscriptions WHERE state = 'ACTIVE' ORDER BY next_payment_date ASC")
    fun getActiveSubscriptions(): Flow<List<SubscriptionEntity>>
    
    @Query("SELECT * FROM subscriptions WHERE next_payment_date <= :date AND state = 'ACTIVE' ORDER BY next_payment_date ASC")
    fun getUpcomingSubscriptions(date: LocalDate): Flow<List<SubscriptionEntity>>

    /**
     * Active INCOME-direction subscriptions whose next_payment_date is today
     * or earlier — the phantom auto-creator (#371) materialises one
     * transaction per missed cycle for each row returned here.
     */
    @Query("""
        SELECT * FROM subscriptions
        WHERE direction = 'INCOME'
          AND state = 'ACTIVE'
          AND next_payment_date IS NOT NULL
          AND next_payment_date <= :date
        ORDER BY next_payment_date ASC
    """)
    suspend fun getDueIncomeSubscriptions(date: LocalDate): List<SubscriptionEntity>
    
    /**
     * Used by the SMS-debit matcher only — explicitly filtered to
     * EXPENSE-direction so an incoming bank-debit SMS doesn't accidentally
     * match an INCOME-direction subscription (#371). INCOME subs are
     * advanced by the phantom auto-creator, not by SMS match.
     */
    @Query("SELECT * FROM subscriptions WHERE merchant_name = :merchantName AND state = 'ACTIVE' AND direction = 'EXPENSE' LIMIT 1")
    suspend fun getActiveSubscriptionByMerchant(merchantName: String): SubscriptionEntity?
    
    @Query("SELECT * FROM subscriptions WHERE merchant_name = :merchantName AND state = 'HIDDEN' LIMIT 1")
    suspend fun getHiddenSubscriptionByMerchant(merchantName: String): SubscriptionEntity?
    
    @Query("SELECT * FROM subscriptions WHERE umn = :umn LIMIT 1")
    suspend fun getSubscriptionByUmn(umn: String): SubscriptionEntity?
    
    @Query("SELECT * FROM subscriptions WHERE merchant_name = :merchantName AND amount = :amount AND next_payment_date = :paymentDate LIMIT 1")
    suspend fun getSubscriptionByMerchantAmountAndDate(
        merchantName: String,
        amount: BigDecimal,
        paymentDate: LocalDate
    ): SubscriptionEntity?
    
    @Query("SELECT * FROM subscriptions WHERE merchant_name = :merchantName AND amount = :amount LIMIT 1")
    suspend fun getSubscriptionByMerchantAndAmount(
        merchantName: String,
        amount: BigDecimal
    ): SubscriptionEntity?
    
    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getSubscriptionById(id: Long): SubscriptionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionEntity): Long
    
    @Update
    suspend fun updateSubscription(subscription: SubscriptionEntity)
    
    @Query("UPDATE subscriptions SET state = :state, updated_at = datetime('now') WHERE id = :id")
    suspend fun updateSubscriptionState(id: Long, state: SubscriptionState)
    
    @Query("UPDATE subscriptions SET next_payment_date = :nextPaymentDate, updated_at = datetime('now') WHERE id = :id")
    suspend fun updateNextPaymentDate(id: Long, nextPaymentDate: LocalDate)

    /**
     * Atomic write of both fields when the user marks-as-paid (#412): we
     * record the date they confirmed payment AND advance the schedule in
     * one DB hit. The "Paid" badge on the row reads `last_paid_at`; the
     * use case reads it to refuse a same-cycle re-tap.
     */
    @Query("""
        UPDATE subscriptions
        SET last_paid_at = :paidAt,
            next_payment_date = :nextPaymentDate,
            updated_at = datetime('now')
        WHERE id = :id
    """)
    suspend fun markPaid(id: Long, paidAt: LocalDate, nextPaymentDate: LocalDate)

    @Delete
    suspend fun deleteSubscription(subscription: SubscriptionEntity)
    
    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscriptionById(id: Long)
    
    @Query("SELECT * FROM subscriptions WHERE state = :state ORDER BY next_payment_date ASC")
    suspend fun getSubscriptionsByStateList(state: SubscriptionState): List<SubscriptionEntity>
    
    @Query("DELETE FROM subscriptions")
    suspend fun deleteAllSubscriptions()
}