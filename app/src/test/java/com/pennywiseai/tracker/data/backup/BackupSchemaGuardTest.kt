package com.pennywiseai.tracker.data.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.serializer
import org.junit.Assert.fail
import org.junit.Test

/**
 * CI guard for the backup compatibility contract.
 *
 * The whole forward/backward-compatibility scheme relies on **every field in
 * the backup wrapper models having a default value**, so that a backup which
 * omits a field (an older export, or a partial/corrupt file) still decodes —
 * the missing key falls back to its default instead of throwing
 * `MissingFieldException`.
 *
 * This test fails the build if someone adds a non-defaulted field to any
 * backup model, and names the offender. (Entity-level backward compatibility
 * is covered separately by `BackupModelsTest.oldBackupMissingNewerFields_*`,
 * which decodes an older-shaped JSON fixture.)
 *
 * See docs/backup-format.md.
 */
@OptIn(ExperimentalSerializationApi::class)
class BackupSchemaGuardTest {

    private val backupModelDescriptors: List<SerialDescriptor> = listOf(
        serializer<PennyWiseBackup>().descriptor,
        serializer<BackupMetadata>().descriptor,
        serializer<BackupStatistics>().descriptor,
        serializer<DateRange>().descriptor,
        serializer<DatabaseSnapshot>().descriptor,
        serializer<PreferencesSnapshot>().descriptor,
        serializer<ThemePreferences>().descriptor,
        serializer<SmsPreferences>().descriptor,
        serializer<DeveloperPreferences>().descriptor,
        serializer<AppPreferences>().descriptor,
    )

    @Test
    fun everyBackupModelFieldHasADefault() {
        val offenders = mutableListOf<String>()
        for (descriptor in backupModelDescriptors) {
            for (i in 0 until descriptor.elementsCount) {
                if (!descriptor.isElementOptional(i)) {
                    offenders += "${descriptor.serialName}.${descriptor.getElementName(i)}"
                }
            }
        }
        if (offenders.isNotEmpty()) {
            fail(
                "These backup-model fields have no default value, which breaks " +
                    "backward/forward compatibility — a backup that omits them can no " +
                    "longer be imported. Give each a Kotlin default:\n  " +
                    offenders.joinToString("\n  ")
            )
        }
    }
}
