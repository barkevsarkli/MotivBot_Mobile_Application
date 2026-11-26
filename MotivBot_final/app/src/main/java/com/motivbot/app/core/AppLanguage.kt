package com.motivbot.app.core

import androidx.annotation.StringRes
import com.motivbot.app.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Port of `AppLanguage` in `LocalizationManager.swift`: the two languages the app speaks.
 *
 * Turkish is the default resource folder (`res/values`), Persian is `res/values-fa`.
 * The tag is also what the saved session and result store on disk, as on iOS.
 */
@Serializable
enum class AppLanguage(val tag: String, val isRtl: Boolean, @StringRes val nameRes: Int) {
    @SerialName("tr")
    Turkish(tag = "tr", isRtl = false, nameRes = R.string.language_name_tr),

    @SerialName("fa")
    Persian(tag = "fa", isRtl = true, nameRes = R.string.language_name_fa),
    ;

    val toggled: AppLanguage
        get() = if (this == Turkish) Persian else Turkish

    /** Number and date formatting locale (`tr_TR`, `fa_IR`), as `AppLanguage.locale` on iOS. */
    val locale: Locale
        get() = if (this == Turkish) Locale("tr", "TR") else Locale("fa", "IR")

    /** The ICU locale for dates: Persian uses the Solar Hijri calendar, as on iOS. */
    val icuLocaleId: String
        get() = if (this == Turkish) "tr_TR" else "fa_IR@calendar=persian"

    companion object {
        val Default: AppLanguage = Turkish
    }
}
