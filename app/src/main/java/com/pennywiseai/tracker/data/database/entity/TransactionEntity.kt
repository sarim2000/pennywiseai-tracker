package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDateTime
import com.pennywiseai.parser.core.TransactionType

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["transaction_hash"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "amount")
    val amount: BigDecimal,
    
    @ColumnInfo(name = "merchant_name")
    val merchantName: String,
    
    @ColumnInfo(name = "category")
    val category: String,
    
    @ColumnInfo(name = "transaction_type")
    val transactionType: TransactionType,
    
    @ColumnInfo(name = "date_time")
    val dateTime: LocalDateTime,
    
    @ColumnInfo(name = "description")
    val description: String? = null,
    
    @ColumnInfo(name = "sms_body")
    val smsBody: String? = null,
    
    @ColumnInfo(name = "bank_name")
    val bankName: String? = null,
    
    @ColumnInfo(name = "sms_sender")
    val smsSender: String? = null,
    
    @ColumnInfo(name = "account_number")
    val accountNumber: String? = null,
    
    @ColumnInfo(name = "balance_after")
    val balanceAfter: BigDecimal? = null,
    
    @ColumnInfo(name = "transaction_hash", defaultValue = "")
    val transactionHash: String,
    
    @ColumnInfo(name = "is_recurring")
    val isRecurring: Boolean = false,
    
    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean = false,
    
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    @ColumnInfo(name = "from_account")
    val fromAccount: String? = null,

    @ColumnInfo(name = "to_account")
    val toAccount: String? = null,

    @ColumnInfo(name = "reference")
    val reference: String? = null
)