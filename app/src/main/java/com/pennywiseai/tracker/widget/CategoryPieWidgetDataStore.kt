package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.categoryPieDataStore by preferencesDataStore(name = "widget_category_pie_data")

object CategoryPieWidgetDataStore {

    private val DATA = stringPreferencesKey("data_json")
    private val json = Json { ignoreUnknownKeys = true }

    fun getData(context: Context): Flow<CategoryPieWidgetData> {
        return context.categoryPieDataStore.data.map { prefs ->
            prefs[DATA]?.let {
                runCatching { json.decodeFromString<CategoryPieWidgetData>(it) }.getOrNull()
            } ?: CategoryPieWidgetData()
        }
    }

    suspend fun update(context: Context, data: CategoryPieWidgetData) {
        context.categoryPieDataStore.edit { prefs ->
            prefs[DATA] = json.encodeToString(CategoryPieWidgetData.serializer(), data)
        }
    }
}
