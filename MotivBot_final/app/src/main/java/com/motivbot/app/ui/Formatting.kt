package com.motivbot.app.ui

import android.icu.text.DateFormat
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.icu.text.DisplayContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.motivbot.app.core.AppLanguage
import com.motivbot.app.ui.theme.LocalAppLanguage
import java.text.NumberFormat
import java.util.Date
import kotlin.math.abs

// Port of the `LocalizationManager` formatting helpers in DSTheme.swift.

/** A whole number in the language's digits (Persian gets ۱۲۳). */
fun AppLanguage.number(value: Int): String = NumberFormat.getIntegerInstance(locale).format(value.toLong())

/** A long date in the language and its calendar (Solar Hijri for Persian). */
fun AppLanguage.longDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.LONG, ULocale(icuLocaleId)).format(Date(millis))

/** "3 hours ago", like `RelativeDateTimeFormatter` with the full units style. */
fun AppLanguage.relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val formatter = RelativeDateTimeFormatter.getInstance(
        ULocale(icuLocaleId), null, RelativeDateTimeFormatter.Style.LONG, DisplayContext.CAPITALIZATION_NONE,
    )
    val seconds = (now - millis) / 1000
    val direction = if (seconds >= 0) RelativeDateTimeFormatter.Direction.LAST else RelativeDateTimeFormatter.Direction.NEXT
    val elapsed = abs(seconds)
    val (quantity, unit) = when {
        elapsed < 60 -> elapsed to RelativeDateTimeFormatter.RelativeUnit.SECONDS
        elapsed < 3_600 -> elapsed / 60 to RelativeDateTimeFormatter.RelativeUnit.MINUTES
        elapsed < 86_400 -> elapsed / 3_600 to RelativeDateTimeFormatter.RelativeUnit.HOURS
        elapsed < 7 * 86_400 -> elapsed / 86_400 to RelativeDateTimeFormatter.RelativeUnit.DAYS
        else -> elapsed / (7 * 86_400) to RelativeDateTimeFormatter.RelativeUnit.WEEKS
    }
    return formatter.format(quantity.toDouble(), direction, unit)
}

@Composable
@ReadOnlyComposable
fun localizedNumber(value: Int): String = LocalAppLanguage.current.number(value)
