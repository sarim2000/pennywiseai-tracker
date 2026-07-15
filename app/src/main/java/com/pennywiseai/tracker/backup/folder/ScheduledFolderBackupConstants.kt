package com.pennywiseai.tracker.backup.folder

object ScheduledFolderBackupConstants {
    const val BACKUP_FILE_NAME = "PennyWise_Backup.pennywisebackup"
    /** Sibling temp file written first, then swapped into [BACKUP_FILE_NAME]. */
    const val TEMP_BACKUP_FILE_NAME = "PennyWise_Backup.pennywisebackup.tmp"
    const val BACKUP_HOUR_OF_DAY = 2
}
