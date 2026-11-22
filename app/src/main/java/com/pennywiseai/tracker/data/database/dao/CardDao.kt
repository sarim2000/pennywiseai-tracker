package com.pennywiseai.tracker.data.database.dao

import androidx.room3.*
import com.pennywiseai.tracker.data.database.entity.CardEntity
import com.pennywiseai.tracker.data.database.entity.CardType
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity): Long

    @Update
    suspend fun updateCard(card: CardEntity)

    @Delete
    suspend fun deleteCard(card: CardEntity)

    @Query("SELECT * FROM cards WHERE bank_name = :bankName AND card_last4 = :cardLast4 LIMIT 1")
    suspend fun getCard(bankName: String, cardLast4: String): CardEntity?

    @Query("SELECT * FROM cards WHERE id = :cardId LIMIT 1")
    suspend fun getCardById(cardId: Long): CardEntity?

    @Query("SELECT * FROM cards WHERE card_last4 = :cardLast4")
    suspend fun getCardsByLast4(cardLast4: String): List<CardEntity>

    @Query("""
        SELECT * FROM cards 
        WHERE account_last4 IS NULL 
        AND card_type = :cardType 
        AND is_active = 1
    """)
    suspend fun getOrphanedCards(cardType: CardType): List<CardEntity>

    @Query("SELECT * FROM cards ORDER BY bank_name, card_last4")
    fun getAllCards(): Flow<List<CardEntity>>

    @Query("""
        UPDATE cards 
        SET account_last4 = :accountLast4
        WHERE id = :cardId
    """)
    suspend fun linkCardToAccount(cardId: Long, accountLast4: String?)

    @Query("DELETE FROM cards")
    suspend fun deleteAllCards()
}
