package com.motivbot.app.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.motivbot.app.core.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/**
 * Theme and language preference storage.
 *
 * In the Swift app `isDarkMode` lived on the ViewModel and `currentLanguage` on
 * `LocalizationManager` — both in memory only, so both reset on every launch. DataStore keeps them
 * across launches without blocking the main thread the way `SharedPreferences.commit` would.
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val isDarkMode: Flow<Boolean> = context.dataStore.data
        .safe()
        .map { it[KEY_DARK_MODE] ?: false }

    val language: Flow<AppLanguage> = context.dataStore.data
        .safe()
        .map { AppLanguage.fromTag(it[KEY_LANGUAGE]) }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DARK_MODE] = enabled }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[KEY_LANGUAGE] = language.tag }
    }

    /** A corrupt preferences file must not take the app down; fall back to defaults. */
    private fun Flow<Preferences>.safe(): Flow<Preferences> = catch { cause ->
        if (cause is IOException) emit(emptyPreferences()) else throw cause
    }

    private companion object {
        val KEY_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val KEY_LANGUAGE = stringPreferencesKey("language_tag")
    }
}
