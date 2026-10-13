package com.motivbot.app.core

/**
 * Port of `AppLanguage` in `LocalizationManager.swift`.
 *
 * The app ships exactly two user-facing languages, `tr` ⇄ `fa`, and [next] is a strict two-way
 * toggle over them. English exists only as the default resource folder (`res/values/`), which
 * Android requires as a fallback; it is never selectable, so the toggle can never land on it.
 */
enum class AppLanguage(
    val tag: String,
    val displayLabel: String,
    val isRtl: Boolean,
    /** How to name this language to the AI, so it answers in the language the user is reading. */
    val promptName: String,
) {
    Turkish(tag = "tr", displayLabel = "TR", isRtl = false, promptName = "Turkish (Türkçe)"),
    Persian(tag = "fa", displayLabel = "FA", isRtl = true, promptName = "Persian/Farsi (فارسی)"),
    ;

    /** Swift: `var toggled: AppLanguage`. */
    val next: AppLanguage
        get() = entries[(ordinal + 1) % entries.size]

    companion object {
        val Default: AppLanguage = Turkish

        /**
         * Resolves a BCP-47 tag (`"fa"`, `"fa-IR"`, `"tr-TR"`) to a supported language,
         * falling back to [Default] the way `loadBundle(for:)` fell back to `Bundle.main`.
         */
        fun fromTag(tag: String?): AppLanguage =
            tag?.substringBefore('-')
                ?.lowercase()
                ?.let { prefix -> entries.firstOrNull { it.tag == prefix } }
                ?: Default
    }
}
