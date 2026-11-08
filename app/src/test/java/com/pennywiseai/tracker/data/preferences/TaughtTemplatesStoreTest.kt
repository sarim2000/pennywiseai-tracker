package com.pennywiseai.tracker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.pennywiseai.parser.core.MessageTemplate
import com.pennywiseai.parser.core.TemplateField
import com.pennywiseai.parser.core.TemplatePart
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.backup.TaughtTemplate
import com.pennywiseai.tracker.data.backup.TaughtTemplatePart
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class TaughtTemplatesStoreTest {

    /** Two instances over one file, as the app's store and a backup restore are (#279). */
    @Test
    fun `a restore overlapping a save loses neither`() {
        val saved = AtomicReference<String?>(null)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } answers { saved.set(secondArg()); editor }
        val prefs = mockk<SharedPreferences>()
        every { prefs.getString(any(), any()) } answers { saved.get() }
        every { prefs.edit() } returns editor
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs

        val app = TaughtTemplatesStore(context)
        val restoring = TaughtTemplatesStore(context)
        val rounds = 200
        listOf(
            thread { repeat(rounds) { app.save(template("saved$it")) } },
            thread { repeat(rounds) { restoring.restore(listOf(stored("restored$it"))) } }
        ).forEach { it.join() }

        assertEquals(rounds * 2, app.exported().size)
    }

    private fun template(id: String) = MessageTemplate(
        id = id,
        senderKey = "ZQTBNK",
        parts = listOf(TemplatePart(TemplateField.AMOUNT, "")),
        type = TransactionType.EXPENSE,
        currency = "SAR",
        bankName = "Zqt Bank"
    )

    private fun stored(id: String) = TaughtTemplate(
        id = id,
        senderKey = "ZQTBNK",
        parts = listOf(TaughtTemplatePart("AMOUNT")),
        type = "EXPENSE",
        currency = "SAR",
        bankName = "Zqt Bank"
    )
}
