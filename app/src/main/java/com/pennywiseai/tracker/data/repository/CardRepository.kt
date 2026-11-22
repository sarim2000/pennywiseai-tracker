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

    fun getAllCards(): Flow<List<CardEntity>> {
        return cardDao.getAllCards()
    }

    suspend fun linkCardToAccount(cardId: Long, accountLast4: String?) {
        cardDao.linkCardToAccount(cardId, accountLast4)
    }

    suspend fun unlinkCard(cardId: Long) {
        cardDao.linkCardToAccount(cardId, null)
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
