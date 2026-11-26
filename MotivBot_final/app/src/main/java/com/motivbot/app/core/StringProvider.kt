package com.motivbot.app.core

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves strings in a given language outside of composition (the CSV export).
 * Composables use `stringResource()`, which reads the localized context set up by `MotivBotTheme`.
 */
interface StringProvider {
    fun getString(language: AppLanguage, @StringRes resId: Int): String
}

@Singleton
class AndroidStringProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : StringProvider {

    private val localizedContexts = mutableMapOf<AppLanguage, Context>()

    override fun getString(language: AppLanguage, @StringRes resId: Int): String =
        contextFor(language).getString(resId)

    @Synchronized
    fun contextFor(language: AppLanguage): Context =
        localizedContexts.getOrPut(language) { context.localized(language) }
}

/** A context whose resources resolve in [language], with the matching layout direction. */
fun Context.localized(language: AppLanguage): Context {
    val configuration = Configuration(resources.configuration).apply {
        setLocale(language.locale)
        setLayoutDirection(language.locale)
    }
    return createConfigurationContext(configuration)
}
