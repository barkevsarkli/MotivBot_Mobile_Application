package com.motivbot.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Port of `ThemePreference`: follow the system, or force light or dark. */
enum class ThemePreference(val storageValue: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun from(value: String?): ThemePreference = entries.firstOrNull { it.storageValue == value } ?: System
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** Remembers the theme across launches, like `@AppStorage("themePreference")` on iOS. */
@Singleton
class ThemePreferenceStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val preference: Flow<ThemePreference> = context.dataStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { ThemePreference.from(it[KEY]) }

    suspend fun set(preference: ThemePreference) {
        context.dataStore.edit { it[KEY] = preference.storageValue }
    }

    private companion object {
        val KEY = stringPreferencesKey("theme_preference")
    }
}
