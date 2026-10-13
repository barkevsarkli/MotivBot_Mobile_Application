package com.motivbot.app.core

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves string resources outside of Composition.
 *
 * The Swift `WellBeingViewModel` called `NSLocalizedString` directly when building the AI prompt
 * and the CSV export. Doing the Android equivalent (`context.getString`) from a ViewModel is only
 * safe if the context honours the *app* locale rather than the system one — which the application
 * context does not do reliably. So this provider builds a locale-configured context on demand.
 *
 * Composables never use this: they use `stringResource()`, which is already locale-correct.
 */
interface StringProvider {
    fun getString(language: AppLanguage, @StringRes resId: Int): String
}

@Singleton
class AndroidStringProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : StringProvider {

    /** Locale-configured contexts are cheap but not free; one per language is plenty. */
    private val localizedContexts = mutableMapOf<AppLanguage, Context>()

    override fun getString(language: AppLanguage, @StringRes resId: Int): String =
        contextFor(language).getString(resId)

    @Synchronized
    private fun contextFor(language: AppLanguage): Context =
        localizedContexts.getOrPut(language) {
            val locale = Locale.forLanguageTag(language.tag)
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            context.createConfigurationContext(configuration)
        }
}
