package com.pennywiseai.tracker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.pennywiseai.parser.core.AmountFormat
import com.pennywiseai.parser.core.MessageTemplate
import com.pennywiseai.parser.core.TemplateField
import com.pennywiseai.parser.core.TemplatePart
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.backup.TaughtTemplate
import com.pennywiseai.tracker.data.backup.TaughtTemplatePart
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Message shapes the user taught (#279). One JSON list in preferences: there are a
 * handful per person, so no table and no migration to run on old phones.
 */
@Singleton
class TaughtTemplatesStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<MessageTemplate> = stored().mapNotNull { it.toTemplate() }

    val allFlow: Flow<List<MessageTemplate>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(all()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(all())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun forSender(sender: String): List<MessageTemplate> {
        val key = LearnedSendersStore.normalize(sender)
        return all().filter { it.senderKey == key }
    }

    /** Adds [template], dropping the ones listed in [replacing]: teaching a message again corrects it. */
    fun save(template: MessageTemplate, replacing: Collection<String> = emptyList()) {
        write(stored().filter { it.id != template.id && it.id !in replacing } + template.toStored())
    }

    fun delete(id: String) = write(stored().filter { it.id != id })

    /** Everything stored, including templates a newer version wrote that this one can't read. */
    fun exported(): List<TaughtTemplate> = stored()

    /** Additive, like ignored accounts: a restore never drops a template taught on this device. */
    fun restore(templates: List<TaughtTemplate>) {
        if (templates.isEmpty()) return
        val here = stored()
        val ids = here.map { it.id }.toSet()
        write(here + templates.filter { it.id !in ids })
    }

    /** The last decode: a list screen reads this for every row, and the JSON rarely changes. */
    @Volatile
    private var decoded: Pair<String, List<TaughtTemplate>>? = null

    private fun stored(): List<TaughtTemplate> {
        val raw = prefs.getString(KEY_TEMPLATES, null) ?: return emptyList()
        decoded?.takeIf { it.first == raw }?.let { return it.second }
        return try {
            JSON.decodeFromString(SERIALIZER, raw).also { decoded = raw to it }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Couldn't read taught templates", e)
            emptyList()
        }
    }

    private fun write(templates: List<TaughtTemplate>) {
        prefs.edit().putString(KEY_TEMPLATES, JSON.encodeToString(SERIALIZER, templates)).apply()
    }

    companion object {
        private const val TAG = "TaughtTemplatesStore"
        private const val PREFS_NAME = "taught_sms_templates"
        private const val KEY_TEMPLATES = "templates"
        private val SERIALIZER = ListSerializer(TaughtTemplate.serializer())
        private val JSON = Json { ignoreUnknownKeys = true }

        private fun MessageTemplate.toStored() = TaughtTemplate(
            id = id,
            senderKey = senderKey,
            parts = parts.map { TaughtTemplatePart(it.field.name, it.text) },
            type = type.name,
            currency = currency,
            bankName = bankName,
            amountFormat = amountFormat?.name,
            fixedMerchant = fixedMerchant
        )

        /** Null for a template this version can't fully understand: it reads nothing rather than misreads. */
        internal fun TaughtTemplate.toTemplate(): MessageTemplate? {
            if (version > TaughtTemplate.CURRENT_VERSION || id.isBlank() || senderKey.isBlank() || currency.length != 3 || !currency.all { it in 'A'..'Z' }) return null
            val fields = parts.map { part ->
                val field = TemplateField.entries.firstOrNull { it.name == part.field } ?: return null
                TemplatePart(field, part.text)
            }
            if (fields.none { it.field == TemplateField.AMOUNT }) return null
            return MessageTemplate(
                id = id,
                senderKey = senderKey,
                parts = fields,
                type = TransactionType.entries.firstOrNull { it.name == type } ?: return null,
                currency = currency,
                bankName = bankName,
                amountFormat = amountFormat?.let { name -> AmountFormat.entries.firstOrNull { it.name == name } ?: return null },
                fixedMerchant = fixedMerchant
            )
        }
    }
}
