package com.motivbot.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.motivbot.app.core.AppLanguage

/**
 * Port of `LocalizationManager`: the language the app speaks, switched at runtime.
 *
 * Once an assessment starts the language is locked, so the text, the answers and the results stay in
 * one language. While locked, [toggleLanguage] and [setLanguage] do nothing. Like iOS, every launch
 * starts in Turkish unless a saved session or result says otherwise.
 */
class LocalizationManager {
    var currentLanguage by mutableStateOf(AppLanguage.Default)
        private set

    /** True from the start of an assessment until restart. */
    var isLocked by mutableStateOf(false)
        private set

    fun toggleLanguage() = setLanguage(currentLanguage.toggled)

    fun setLanguage(language: AppLanguage) {
        if (isLocked) return
        currentLanguage = language
    }

    /** Locks the current language, or switches to [language] first (e.g. a resumed session's). */
    fun lock(language: AppLanguage? = null) {
        if (language != null) currentLanguage = language
        isLocked = true
    }

    fun unlock() {
        isLocked = false
    }
}
