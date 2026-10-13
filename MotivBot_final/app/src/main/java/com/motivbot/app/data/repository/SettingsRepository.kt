package com.motivbot.app.data.repository

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.motivbot.app.core.AppLanguage
import com.motivbot.app.data.local.prefs.UserPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of `LocalizationManager` plus the `isDarkMode` half of the Swift ViewModel.
 *
 * The Swift manager kept a hand-loaded `Bundle` and resolved every string through it. Android has
 * first-class support for a per-app locale — `AppCompatDelegate.setApplicationLocales` — which
 * makes `stringResource()` in Compose and `getString()` in code both resolve correctly with no
 * custom lookup path. That is what this repository drives; DataStore just remembers the choice.
 */
interface SettingsRepository {
    val isDarkMode: Flow<Boolean>
    val language: Flow<AppLanguage>

    suspend fun toggleTheme(current: Boolean)
    suspend fun toggleLanguage(current: AppLanguage)
    suspend fun setLanguage(language: AppLanguage)

    /**
     * Makes the process locale agree with [language], applying it if it does not already.
     *
     * Without this the app launches showing the *system* locale's strings while the toggle badge
     * reports the stored one — the two disagree until the user taps the toggle. Swift had no such
     * gap because `LocalizationManager.init()` loaded the Turkish bundle unconditionally.
     */
    suspend fun ensureLocaleApplied(language: AppLanguage)
}

@Singleton
class DefaultSettingsRepository @Inject constructor(
    private val preferences: UserPreferences,
) : SettingsRepository {

    override val isDarkMode: Flow<Boolean> = preferences.isDarkMode

    override val language: Flow<AppLanguage> = preferences.language

    /** Swift: `toggleTheme()`. The animation that wrapped it belongs in the UI layer now. */
    override suspend fun toggleTheme(current: Boolean) = preferences.setDarkMode(!current)

    /** Swift: `toggleLanguage()`. */
    override suspend fun toggleLanguage(current: AppLanguage) = setLanguage(current.next)

    override suspend fun setLanguage(language: AppLanguage) {
        preferences.setLanguage(language)
        // Applies the locale to the whole process (and, on API 33+, tells the system about it).
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    override suspend fun ensureLocaleApplied(language: AppLanguage) {
        val applied = AppCompatDelegate.getApplicationLocales()

        // An empty list means no per-app locale has been set yet, so resources are still resolving
        // against the system locale — apply even if the tags would otherwise appear to match.
        val alreadyCorrect = !applied.isEmpty &&
            AppLanguage.fromTag(applied.toLanguageTags().substringBefore(',')) == language

        if (!alreadyCorrect) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }
}
