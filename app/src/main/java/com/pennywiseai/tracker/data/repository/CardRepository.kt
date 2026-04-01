package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.CardDao
import com.pennywiseai.tracker.data.database.entity.CardEntity
import com.pennywiseai.tracker.data.database.entity.CardType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepository @Inject constructor(
    private val cardDao: CardDao
) {
    
    suspend fun insertCard(card: CardEntity): Long {
        return cardDao.insertCard(card)
    }
    
    suspend fun updateCard(card: CardEntity) {
        cardDao.updateCard(card.copy(updatedAt = LocalDateTime.now()))
    }
    
    suspend fun deleteCard(card: CardEntity) {
        cardDao.deleteCard(card)
    }
    
    suspend fun deleteCard(cardId: Long) {
        val card = cardDao.getCardById(cardId)
        if (card != null) {
            cardDao.deleteCard(card)
        }
    }
    
    suspend fun getCard(bankName: String, cardLast4: String): CardEntity? {
        return cardDao.getCard(bankName, cardLast4)
    }
    
    suspend fun getCardById(cardId: Long): CardEntity? {
        return cardDao.getCardById(cardId)
    }
    
    suspend fun findCard(cardLast4: String): CardEntity? {
        // Return first matching card (in case of multiple banks with same last4)
        val cards = cardDao.getCardsByLast4(cardLast4)
        return cards.firstOrNull()
    }
    
    suspend fun findOrCreateCard(
        cardLast4: String,
        bankName: String,
        isCredit: Boolean = false
    ): CardEntity {
        val existingCard = cardDao.getCard(bankName, cardLast4)
        if (existingCard != null) {
            // Upgrade DEBIT → CREDIT if we now know it's a credit card
            if (isCredit && existingCard.cardType == CardType.DEBIT) {
                val upgraded = existingCard.copy(
                    cardType = CardType.CREDIT,
                    updatedAt = LocalDateTime.now()
                )
                cardDao.updateCard(upgraded)
                return upgraded
            }
            return existingCard
        }
        
        // Create new card
        val newCard = CardEntity(
            cardLast4 = cardLast4,
            cardType = if (isCredit) CardType.CREDIT else CardType.DEBIT,
            bankName = bankName,
            accountLast4 = null // Never self-reference - cards start unlinked
        )
        
        val id = cardDao.insertCard(newCard)
        return newCard.copy(id = id)
    }
    
    suspend fun getCardsForAccount(accountLast4: String): List<CardEntity> {
        return cardDao.getCardsForAccount(accountLast4)
    }
    
    fun getCardsForAccountFlow(accountLast4: String): Flow<List<CardEntity>> {
        return cardDao.getCardsForAccountFlow(accountLast4)
    }
    
    suspend fun getOrphanedDebitCards(): List<CardEntity> {
        return cardDao.getOrphanedCards(CardType.DEBIT)
    }
    
    suspend fun getOrphanedCreditCards(): List<CardEntity> {
        return cardDao.getOrphanedCards(CardType.CREDIT)
    }
    
    fun getAllActiveCards(): Flow<List<CardEntity>> {
        return cardDao.getAllActiveCards()
    }
    
    fun getAllCards(): Flow<List<CardEntity>> {
        return cardDao.getAllCards()
    }
    
    suspend fun linkCardToAccount(cardId: Long, accountLast4: String?) {
        cardDao.linkCardToAccount(cardId, accountLast4)
    }
    
    suspend fun unlinkCard(cardId: Long) {
        cardDao.linkCardToAccount(cardId, null)
    }
    
    suspend fun setCardActive(cardId: Long, isActive: Boolean) {
        cardDao.setCardActive(cardId, isActive)
    }
    
    suspend fun getCardCount(): Int {
        return cardDao.getCardCount()
    }
    
    suspend fun getCardCountByType(cardType: CardType): Int {
        return cardDao.getCardCountByType(cardType)
    }
    
    suspend fun getBankCards(bankName: String, cardType: CardType): List<CardEntity> {
        return cardDao.getBankCards(bankName, cardType)
    }
    
    /**
     * Determines the target account for a transaction based on card type.
     * For debit cards, returns the linked account.
     * For credit cards or orphaned cards, returns the card's own number.
     */
    suspend fun getTargetAccount(cardLast4: String, bankName: String): String {
        val card = getCard(bankName, cardLast4)
        
        return when {
            card == null -> cardLast4 // Unknown card, use as-is
            card.cardType == CardType.DEBIT && card.accountLast4 != null -> card.accountLast4
            else -> cardLast4 // Credit card or orphaned debit card
        }
    }
    
    /**
     * Updates the last known balance for a card from a transaction.
     * This is used to track balance for unlinked cards.
     */
    suspend fun updateCardBalance(
        cardId: Long,
        balance: BigDecimal?,
        source: String?,
        date: LocalDateTime = LocalDateTime.now()
    ) {
        val card = cardDao.getCardById(cardId)
        android.util.Log.d("CardRepository", """
            Updating balance for card $cardId:
            - Card found: ${card != null}
            - New balance: $balance
            - Previous balance: ${card?.lastBalance}
        """.trimIndent())
        
        if (card != null) {
            val updatedCard = card.copy(
                lastBalance = balance,
                lastBalanceSource = source?.take(200),  // Limit source length
                lastBalanceDate = date,
                updatedAt = LocalDateTime.now()
            )
            cardDao.updateCard(updatedCard)
            android.util.Log.d("CardRepository", "Card balance updated successfully")
        } else {
            android.util.Log.e("CardRepository", "Card not found for ID: $cardId")
        }
    }
}