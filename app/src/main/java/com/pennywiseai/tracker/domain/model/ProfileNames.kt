package com.pennywiseai.tracker.domain.model

import androidx.annotation.StringRes
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.ProfileEntity

/**
 * The seeded profiles' names are stored in English, so while a seeded row
 * still holds its seeded name it shows in the app's language. A renamed
 * profile, or any other, shows exactly as stored.
 */
@StringRes
fun ProfileEntity.seededNameRes(): Int? = when {
    id == ProfileEntity.PERSONAL_ID && name == "Personal" -> R.string.profile_default_personal
    id == ProfileEntity.BUSINESS_ID && name == "Business" -> R.string.profile_default_business
    else -> null
}
