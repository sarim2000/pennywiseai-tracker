package com.pennywiseai.tracker.data.preferences

/**
 * How numeric amounts are grouped for display.
 *
 * - [AUTO]: currency-driven — Indian grouping (1,00,000 / L/Cr) for INR/NPR/PKR,
 *   international grouping (100,000 / K/M) for every other currency.
 * - [INDIAN]: force Indian grouping for all currencies.
 * - [INTERNATIONAL]: force international grouping for all currencies.
 */
enum class NumberFormatStyle {
    AUTO,
    INDIAN,
    INTERNATIONAL
}
