package com.pennywiseai.tracker.data.backup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * kotlinx.serialization plumbing for the backup format.
 *
 * The three custom serializers below emit **byte-identical** output to the
 * legacy Gson type adapters they replace, so that:
 *  - backups written by older (Gson) releases parse here unchanged, and
 *  - backups written here remain readable by the older Gson-based importer.
 *
 * Formats (must not change without bumping the backup format version):
 *  - [BigDecimal]      → plain decimal string  (`toPlainString()`)
 *  - [LocalDateTime]   → `ISO_LOCAL_DATE_TIME` (e.g. `2024-01-02T10:15:30`)
 *  - [LocalDate]       → `ISO_LOCAL_DATE`      (e.g. `2024-01-02`)
 *
 * See `docs/backup-format.md` for the full compatibility contract.
 */

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.math.BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): BigDecimal =
        BigDecimal(decoder.decodeString())
}

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.format(formatter))
    }

    override fun deserialize(decoder: Decoder): LocalDateTime =
        LocalDateTime.parse(decoder.decodeString(), formatter)
}

object LocalDateSerializer : KSerializer<LocalDate> {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.format(formatter))
    }

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString(), formatter)
}

/**
 * Registers the contextual serializers above. Entity fields of these types are
 * marked `@Contextual` so the same serializer is reused everywhere.
 */
val backupSerializersModule: SerializersModule = SerializersModule {
    contextual(BigDecimal::class, BigDecimalSerializer)
    contextual(LocalDateTime::class, LocalDateTimeSerializer)
    contextual(LocalDate::class, LocalDateSerializer)
}

/**
 * The single [Json] instance used for both export and import.
 *
 * Compatibility-critical settings — the whole point of this module:
 *  - `ignoreUnknownKeys = true` → a backup written by a *newer* app (extra
 *    keys) still imports into an *older* app (forward compatibility).
 *  - `coerceInputValues = true` + Kotlin constructor defaults → a backup
 *    written by an *older* app (missing keys) still imports into a *newer*
 *    app; the missing field falls back to its default instead of crashing
 *    (backward compatibility). This is the fix for the "can't restore old
 *    backup" bug — Gson's `Unsafe` allocation ignored these defaults.
 *  - `encodeDefaults = true` → exported JSON is explicit/self-describing.
 *  - `isLenient = true` → tolerant of minor formatting quirks.
 */
val backupJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    isLenient = true
    serializersModule = backupSerializersModule
}
