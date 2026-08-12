package com.pennywiseai.tracker.core


/**
 * Application-wide constants to avoid hardcoded values
 */
object Constants {
    
    /**
     * SMS Processing Configuration
     */
    object SmsProcessing {
        const val DEFAULT_BATCH_SIZE = 100
        const val SMS_PREVIEW_LENGTH = 200
        const val QUERY_LIMIT = 100
        const val INITIAL_SCAN_MONTHS = 3
        const val SCANNING_DELAY_MS = 3000L
        /** Stored in [last_scan_period] when SMS scan period is set to all time. */
        const val SCAN_PERIOD_ALL_TIME = -1
        /** Stored in [last_scan_period] when SMS scan period is a custom start date. */
        const val SCAN_PERIOD_CUSTOM_DATE = -2
    }
    
    /**
     * UI Configuration - Moved to ui/theme/Dimensions.kt for better organization
     * Keeping only non-dimension constants here
     */
    object UI {
        const val BUTTON_WIDTH_RATIO = 0.8f
        const val PROGRESS_STROKE_WIDTH = 2f
    }
    
    /**
     * Database Configuration
     */
    object Database {
        const val DATABASE_NAME = "pennywise_database"
        const val CURRENT_VERSION = 2
        const val TRANSACTION_HASH_DEFAULT = ""
    }
    
    /**
     * WorkManager Configuration
     */
    object WorkManager {
        const val SMS_READER_WORK_NAME = "sms_reader_work"
        const val PERIODIC_SCAN_INTERVAL_HOURS = 24L
        const val INITIAL_DELAY_MINUTES = 15L
    }
    
    /**
     * Parsing Configuration
     */
    object Parsing {
        const val MIN_MERCHANT_NAME_LENGTH = 2
        const val MD5_ALGORITHM = "MD5"
        const val AMOUNT_SCALE = 2
        const val CONFIDENCE_PATTERN_BASED = 0.7f
        const val CONFIDENCE_AI_BASED = 0.9f
    }
    
    /**
     * Navigation Routes
     */
    object Routes {
        const val HOME = "home"
        const val TRANSACTIONS = "transactions"
        const val ANALYTICS = "analytics"
        const val CHAT = "chat"
        const val SETTINGS = "settings"
    }
    
    /**
     * LLM Model Configuration
     *
     * The chat model is distributed from a **versioned, immutable object key**
     * and verified against a pinned SHA-256 before it is ever loaded. To publish
     * a new model: upload it under a fresh [MODEL_VERSION] key (never overwrite an
     * existing version in place), then bump [MODEL_VERSION] and [MODEL_SHA256]
     * together. This makes a silently-swapped model impossible to ship.
     */
    object ModelDownload {
        const val MODEL_FILE_NAME = "Qwen2.5-1.5B-Instruct-q8-ekv4096.litertlm"

        /** Bump when a new model build is published under a new versioned key. */
        const val MODEL_VERSION = "v1"

        private const val R2_PUBLIC_BASE =
            "https://pub-fcfb3ffddb184540a758a7fe68249908.r2.dev"

        /**
         * Pinned to an immutable, versioned path — NOT the bare bucket root, so a
         * given URL always resolves to the same bytes. The object must be uploaded
         * to this exact key in R2 (`models/$MODEL_VERSION/$MODEL_FILE_NAME`).
         */
        const val MODEL_URL = "$R2_PUBLIC_BASE/models/$MODEL_VERSION/$MODEL_FILE_NAME"

        /**
         * Lowercase-hex SHA-256 of the exact bytes served at [MODEL_URL]. The
         * downloaded file is hashed and compared against this before it is loaded;
         * a mismatch is rejected and the file deleted. Recompute and bump on every
         * model change. Leave blank ONLY to intentionally disable verification (a
         * warning is logged and any file is accepted).
         */
        const val MODEL_SHA256 = "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9"

        const val MODEL_SIZE_MB = 1524L
        const val MODEL_SIZE_BYTES = 1_597_931_520L
        const val REQUIRED_SPACE_BYTES = 3_195_863_040L // ~3.0GB (2x model size for safety)
    }

    /**
     * External Links
     */
    object Links {
        const val DISCORD_URL = "https://discord.gg/H3xWeMWjKQ"
        const val GITHUB_URL = "https://github.com/sarim2000/pennywiseai-tracker"
        const val WEB_PARSER_URL = "https://pennywise.zynth.dev"
    }
}
