package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.BankNotificationDao
import com.pennywiseai.tracker.data.database.entity.BankNotificationEntity
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankNotificationRepository @Inject constructor(
    private val dao: BankNotificationDao
) {

    suspend fun logNotification(
        packageName: String,
        senderAlias: String,
        messageBody: String,
        postedAtMillis: Long
    ): Long {
        val postedAt = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(postedAtMillis),
            ZoneId.systemDefault()
        )

        val messageHash = hash("$packageName|$senderAlias|$messageBody")

        val entity = BankNotificationEntity(
            packageName = packageName,
            senderAlias = senderAlias,
            messageBody = messageBody,
            messageHash = messageHash,
            postedAt = postedAt
        )

        return dao.insert(entity)
    }

    suspend fun markProcessed(id: Long, transactionId: Long?) {
        dao.updateStatus(id, processed = true, transactionId = transactionId)
    }

    private fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
