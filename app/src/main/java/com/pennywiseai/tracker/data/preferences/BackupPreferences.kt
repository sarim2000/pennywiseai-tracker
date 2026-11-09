package com.pennywiseai.tracker.data.preferences

import kotlinx.coroutines.flow.Flow

/**
 * How backups run: the scheduled folder backup and whether receipts are embedded. These are
 * backup settings, not the PreferencesSnapshot a backup file carries. Backed by the single
 * user_preferences DataStore through [UserPreferencesRepository]; keys and defaults are pinned
 * by the UserPreferences golden tests.
 */
interface BackupPreferences {
    val scheduledFolderBackupEnabled: Flow<Boolean>
    val scheduledFolderBackupLastTimestamp: Flow<Long?>

    suspend fun isScheduledFolderBackupEnabled(): Boolean
    suspend fun getScheduledFolderBackupTreeUri(): String?

    suspend fun setScheduledFolderBackupEnabled(enabled: Boolean)
    suspend fun setScheduledFolderBackupTreeUri(treeUri: String)
    suspend fun setScheduledFolderBackupLastTimestamp(timestamp: Long)

    val exportIncludeReceipts: Flow<Boolean>
    val folderBackupIncludeReceipts: Flow<Boolean>

    suspend fun setExportIncludeReceipts(include: Boolean)
    suspend fun setFolderBackupIncludeReceipts(include: Boolean)
}
