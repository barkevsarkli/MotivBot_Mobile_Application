package com.motivbot.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.motivbot.app.R
import com.motivbot.app.ui.ResumableSession
import com.motivbot.app.ui.WellBeingViewModel
import com.motivbot.app.ui.components.BottomActionBar
import com.motivbot.app.ui.components.DSPrimaryButton
import com.motivbot.app.ui.components.DSSecondaryButton
import com.motivbot.app.ui.components.ProgressRing
import com.motivbot.app.ui.components.ScreenColumn
import com.motivbot.app.ui.components.ScreenScaffold
import com.motivbot.app.ui.components.TopBar
import com.motivbot.app.ui.components.dsAppear
import com.motivbot.app.ui.components.dsCard
import com.motivbot.app.ui.localizedNumber
import com.motivbot.app.ui.relativeTime
import com.motivbot.app.ui.theme.DS
import com.motivbot.app.ui.theme.DSFont
import com.motivbot.app.ui.theme.DSSpacing
import com.motivbot.app.ui.theme.LocalAppLanguage

/** Shown at launch when an unfinished assessment was saved. It appears in the saved session's language. */
@Composable
fun ResumePromptScreen(viewModel: WellBeingViewModel, onContinue: () -> Unit) {
    var isConfirmingStartOver by rememberSaveable { mutableStateOf(false) }
    val session = viewModel.resumableSession

    // Speak the saved session's language; Continue then locks it.
    LaunchedEffect(session?.language) {
        session?.language?.let { viewModel.localization.setLanguage(it) }
    }

    ScreenScaffold(
        topBar = { TopBar() },
        bottomBar = {
            BottomActionBar(Modifier.dsAppear(150)) {
                DSPrimaryButton(stringResource(R.string.resume_continue), icon = Icons.AutoMirrored.Filled.ArrowForward) {
                    onContinue()
                    viewModel.continueSession()
                }
                DSSecondaryButton(stringResource(R.string.resume_start_over)) { isConfirmingStartOver = true }
            }
        },
    ) { padding ->
        // Centred vertically when it fits, like `defaultScrollAnchor(.center)`.
        ScreenColumn(Modifier.align(Alignment.Center).padding(padding).padding(vertical = DSSpacing.l)) {
            if (session != null) ResumeCard(session, Modifier.dsAppear())
        }
    }

    if (isConfirmingStartOver) {
        val colors = DS.colors
        AlertDialog(
            onDismissRequest = { isConfirmingStartOver = false },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text(stringResource(R.string.resume_confirm_title), style = DSFont.heading) },
            text = { Text(stringResource(R.string.resume_confirm_message), style = DSFont.callout) },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingStartOver = false
                    viewModel.startOver()
                }) {
                    Text(stringResource(R.string.resume_confirm_action), style = DSFont.label, color = colors.care)
                }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmingStartOver = false }) {
                    Text(stringResource(R.string.cancel_button), style = DSFont.label, color = colors.brand)
                }
            },
        )
    }
}

@Composable
private fun ResumeCard(session: ResumableSession, modifier: Modifier) {
    val colors = DS.colors
    val language = LocalAppLanguage.current
    val fraction = if (session.totalQuestions > 0) session.answeredQuestions.toDouble() / session.totalQuestions else 0.0
    val started = remember(session.startedAtMillis, language) { language.relativeTime(session.startedAtMillis) }

    Column(modifier.dsCard(), verticalArrangement = Arrangement.spacedBy(DSSpacing.m)) {
        Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.m), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                ProgressRing(fraction, Modifier.size(64.dp))
                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = colors.brand, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DSSpacing.xxs)) {
                Text(
                    stringResource(R.string.resume_title),
                    style = DSFont.title,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.resume_body), style = DSFont.callout, color = colors.textSecondary)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(DSSpacing.xxs),
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Text(
                stringResource(R.string.resume_progress, localizedNumber(session.answeredQuestions), localizedNumber(session.totalQuestions)),
                style = DSFont.bodyEmphasis,
                color = colors.textPrimary,
            )
            Text(stringResource(R.string.resume_started, started), style = DSFont.footnote, color = colors.textSecondary)
        }
    }
}
