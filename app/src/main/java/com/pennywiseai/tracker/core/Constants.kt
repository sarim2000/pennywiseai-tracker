package com.pennywiseai.tracker.core

/**
 * Application-wide constants to avoid hardcoded values
 */
object Constants {

    /**
     * SMS Processing Configuration
     */
    object SmsProcessing {
        /** Stored in [last_scan_period] when SMS scan period is set to all time. */
        const val SCAN_PERIOD_ALL_TIME = -1

        /** Stored in [last_scan_period] when SMS scan period is a custom start date. */
        const val SCAN_PERIOD_CUSTOM_DATE = -2
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
        const val WEB_PARSER_URL = "https://pennywise.zynth.dev"
        /** Where Pro is sold. The Play upgrade sheet shows it as plain text only — never a link. */
        const val PRO_PAGE_URL = "$WEB_PARSER_URL/pro"
        /** `?app=1` serves the guide without Get Pro or links to purchase pages (Play anti-steering). */
        const val USER_GUIDE_URL = "$WEB_PARSER_URL/guide?app=1"
    }
}
