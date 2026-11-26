package com.motivbot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LooksOne
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.motivbot.app.R
import com.motivbot.app.ui.LocalizationManager
import com.motivbot.app.ui.components.BottomActionBar
import com.motivbot.app.ui.components.DSPrimaryButton
import com.motivbot.app.ui.components.LanguagePicker
import com.motivbot.app.ui.components.ScreenColumn
import com.motivbot.app.ui.components.ScreenScaffold
import com.motivbot.app.ui.components.TopBar
import com.motivbot.app.ui.components.dsAppear
import com.motivbot.app.ui.components.dsCard
import com.motivbot.app.ui.theme.DS
import com.motivbot.app.ui.theme.DSFont
import com.motivbot.app.ui.theme.DSRadius
import com.motivbot.app.ui.theme.DSSpacing

/** The first screen: what MotivBot is, what to expect, and the language choice (the only place it can change). */
@Composable
fun WelcomeScreen(localization: LocalizationManager, onStart: () -> Unit) {
    ScreenScaffold(
        topBar = { TopBar() },
        bottomBar = {
            BottomActionBar(Modifier.dsAppear(300)) {
                DSPrimaryButton(stringResource(R.string.welcome_start), icon = Icons.AutoMirrored.Filled.ArrowForward, onClick = onStart)
            }
        },
    ) { padding ->
        ScreenColumn(Modifier.padding(padding).padding(vertical = DSSpacing.l), spacing = DSSpacing.xl) {
            Header(Modifier.dsAppear())
            Expectations(Modifier.dsAppear(100))
            LanguageSection(localization, Modifier.dsAppear(200))
        }
    }
}

@Composable
private fun Header(modifier: Modifier) {
    val colors = DS.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DSSpacing.s)) {
        Box(
            Modifier.size(64.dp).background(colors.brandSoft, RoundedCornerShape(DSRadius.medium + 4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Eco, contentDescription = null, tint = colors.brand, modifier = Modifier.size(34.dp))
        }
        Text(
            stringResource(R.string.app_title),
            style = DSFont.display,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.welcome_tagline), style = DSFont.body, color = colors.textSecondary)
    }
}

@Composable
private fun Expectations(modifier: Modifier) {
    val colors = DS.colors
    Column(modifier.dsCard(), verticalArrangement = Arrangement.spacedBy(DSSpacing.m)) {
        Text(
            stringResource(R.string.welcome_expect_title),
            style = DSFont.heading,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        ExpectationRow(Icons.Outlined.Schedule, stringResource(R.string.welcome_expect_time))
        ExpectationRow(Icons.AutoMirrored.Outlined.Chat, stringResource(R.string.welcome_expect_steps))
        ExpectationRow(Icons.Outlined.LooksOne, stringResource(R.string.welcome_expect_once))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.stroke))
        Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.welcome_not_diagnosis), style = DSFont.callout, color = colors.textSecondary)
        }
    }
}

@Composable
private fun ExpectationRow(icon: ImageVector, text: String) {
    val colors = DS.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.s),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Icon(icon, contentDescription = null, tint = colors.brand, modifier = Modifier.padding(top = 1.dp).size(22.dp))
        Text(text, style = DSFont.body, color = colors.textPrimary)
    }
}

@Composable
private fun LanguageSection(localization: LocalizationManager, modifier: Modifier) {
    val colors = DS.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
        Text(
            stringResource(R.string.welcome_language_label),
            style = DSFont.label,
            color = colors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        LanguagePicker(localization)
        Text(stringResource(R.string.welcome_language_hint), style = DSFont.footnote, color = colors.textSecondary)
    }
}
