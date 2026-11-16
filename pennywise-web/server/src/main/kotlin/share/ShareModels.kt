package com.example.share

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Share snapshot v1 — what the app publishes to /api/shares.
 *
 * Every user-visible string arrives already formatted and localized by the app. The
 * server never formats money or does currency maths: it validates the shape, stores the
 * snapshot, and renders the strings as given. `chart.points[].value` is only geometry.
 */
@Serializable
data class ShareSnapshot(
    val v: Int,
    val kind: String,
    val locale: String,
    val theme: String = ShareTheme.DEFAULT,
    val title: String,
    val subtitle: String? = null,
    val periodLabel: String? = null,
    val hero: ShareHero? = null,
    val stats: List<ShareStat> = emptyList(),
    val chart: ShareChart? = null,
    val achievements: List<ShareAchievement> = emptyList(),
    // Absent means the app's default (30 days); an explicit null means "never expires".
    val expiresInDays: Int? = 30,
) {
    /** Language subtag decides direction; the region never does. */
    val isRtl: Boolean get() = locale.substringBefore('-').lowercase() in RTL_LANGUAGES

    companion object {
        private val RTL_LANGUAGES = setOf("ar", "fa", "he", "iw", "ur", "ps", "ckb", "sd", "ug", "yi", "dv")
    }
}

@Serializable
data class ShareHero(
    val label: String,
    val value: String,
    val delta: String? = null,
    val sentiment: String,
)

@Serializable
data class ShareStat(
    val label: String,
    val value: String,
    val sentiment: String,
)

@Serializable
data class ShareChart(
    val type: String,
    val points: List<ShareChartPoint>,
    val highlightIndex: Int? = null,
)

@Serializable
data class ShareChartPoint(
    val label: String,
    val value: Double,
    val display: String? = null,
)

@Serializable
data class ShareAchievement(
    val id: String,
    val title: String,
    val description: String,
    val tier: Int,
    val unlockedOn: String? = null,
)

/** Fixed palettes the app also draws its card in; anything unknown falls back to midnight. */
object ShareTheme {
    const val DEFAULT = "midnight"
    val ALL = setOf("midnight", "aurora", "paper")
    fun normalize(theme: String): String = if (theme in ALL) theme else DEFAULT
}

@Serializable
data class CreateShareResponse(
    val id: String,
    val url: String,
    val deleteToken: String,
    val expiresAt: String?,
)

@Serializable
data class ShareErrorResponse(val error: String)

object ShareSnapshotValidator {
    const val VERSION = 1
    val KINDS = setOf("recap", "spending", "savings", "goal", "budget", "achievements")
    val SENTIMENTS = setOf("good", "bad", "neutral")
    val CHART_TYPES = setOf("bars", "line", "ring", "diverging")
    val EXPIRY_DAYS = setOf(7, 30, 90)

    private val LOCALE = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8}){0,4}$")
    private val ACHIEVEMENT_ID = Regex("^[A-Za-z0-9_.:-]+$")
    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** Strict on types, lenient on unknown keys so a newer app can add optional fields. */
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        allowSpecialFloatingPointValues = false
        encodeDefaults = true
        explicitNulls = true
    }

    /** Parses and validates; returns the normalized snapshot, or a client-facing error. */
    fun parse(raw: String): Result<ShareSnapshot> {
        val snapshot = try {
            json.decodeFromString(ShareSnapshot.serializer(), raw)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("snapshot is not valid share JSON"))
        }
        val error = validate(snapshot)
        return if (error != null) Result.failure(IllegalArgumentException(error))
        else Result.success(snapshot.copy(theme = ShareTheme.normalize(snapshot.theme)))
    }

    fun validate(s: ShareSnapshot): String? {
        if (s.v != VERSION) return "unsupported snapshot version ${s.v}"
        if (s.kind !in KINDS) return "unknown kind"
        if (s.locale.length > 35 || !LOCALE.matches(s.locale)) return "invalid locale"
        text("title", s.title, 80, required = true)?.let { return it }
        s.subtitle?.let { v -> text("subtitle", v, 140)?.let { return it } }
        s.periodLabel?.let { v -> text("periodLabel", v, 60)?.let { return it } }
        s.hero?.let { h ->
            text("hero.label", h.label, 60, required = true)?.let { return it }
            text("hero.value", h.value, 40, required = true)?.let { return it }
            h.delta?.let { v -> text("hero.delta", v, 30)?.let { return it } }
            if (h.sentiment !in SENTIMENTS) return "invalid hero.sentiment"
        }
        if (s.stats.size > 6) return "at most 6 stats"
        s.stats.forEachIndexed { i, st ->
            text("stats[$i].label", st.label, 40, required = true)?.let { return it }
            text("stats[$i].value", st.value, 40, required = true)?.let { return it }
            if (st.sentiment !in SENTIMENTS) return "invalid stats[$i].sentiment"
        }
        s.chart?.let { c -> validateChart(c)?.let { return it } }
        if (s.achievements.size > 24) return "at most 24 achievements"
        s.achievements.forEachIndexed { i, a ->
            if (a.id.isEmpty() || a.id.length > 40 || !ACHIEVEMENT_ID.matches(a.id)) return "invalid achievements[$i].id"
            text("achievements[$i].title", a.title, 60, required = true)?.let { return it }
            text("achievements[$i].description", a.description, 120)?.let { return it }
            if (a.tier !in 0..5) return "achievements[$i].tier must be 0..5"
            a.unlockedOn?.let { d -> if (!isIsoDate(d)) return "achievements[$i].unlockedOn must be YYYY-MM-DD" }
        }
        if (s.kind == "achievements" && s.achievements.isEmpty()) return "an achievements share needs at least one achievement"
        if (s.hero == null && s.stats.isEmpty() && s.chart == null && s.achievements.isEmpty()) return "snapshot has nothing to show"
        if (s.expiresInDays != null && s.expiresInDays !in EXPIRY_DAYS) return "expiresInDays must be 7, 30, 90 or null"
        return null
    }

    private fun validateChart(c: ShareChart): String? {
        if (c.type !in CHART_TYPES) return "invalid chart.type"
        if (c.points.isEmpty() || c.points.size > 12) return "chart needs 1 to 12 points"
        c.points.forEachIndexed { i, p ->
            text("chart.points[$i].label", p.label, 24)?.let { return it }
            p.display?.let { v -> text("chart.points[$i].display", v, 30)?.let { return it } }
            if (!p.value.isFinite()) return "chart.points[$i].value must be finite"
        }
        if (c.type == "ring" && (c.points.size != 1 || c.points[0].value !in 0.0..1.0)) {
            return "a ring chart takes one point with a value from 0 to 1"
        }
        c.highlightIndex?.let { if (it !in c.points.indices) return "chart.highlightIndex out of range" }
        return null
    }

    /** Lengths are counted in code points, so an emoji or a combining Arabic mark isn't double-billed. */
    private fun text(field: String, value: String, max: Int, required: Boolean = false): String? {
        if (required && value.isBlank()) return "$field is required"
        if (value.codePointCount(0, value.length) > max) return "$field is longer than $max characters"
        // Control characters have no business in a one-line label and only help spoofing.
        if (value.any { it.code < 0x20 || it.code == 0x7F }) return "$field contains control characters"
        return null
    }

    private fun isIsoDate(value: String): Boolean = ISO_DATE.matches(value) && try {
        LocalDate.parse(value); true
    } catch (e: DateTimeParseException) {
        false
    }
}
