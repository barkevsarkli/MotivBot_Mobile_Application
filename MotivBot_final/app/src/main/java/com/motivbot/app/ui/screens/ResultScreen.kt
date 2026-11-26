package com.motivbot.app.ui.screens

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.motivbot.app.R
import com.motivbot.app.domain.MessageCategoryStyle
import com.motivbot.app.domain.PredictionResponse
import com.motivbot.app.domain.ReflectionTopic
import com.motivbot.app.ui.WellBeingViewModel
import com.motivbot.app.ui.components.DSPrimaryButton
import com.motivbot.app.ui.components.DSSecondaryButton
import com.motivbot.app.ui.components.GlassIconButton
import com.motivbot.app.ui.components.InfoNote
import com.motivbot.app.ui.components.MessageDeck
import com.motivbot.app.ui.components.MessageDeckStrings
import com.motivbot.app.ui.components.ReflectionRatingRow
import com.motivbot.app.ui.components.ScreenColumn
import com.motivbot.app.ui.components.ScreenScaffold
import com.motivbot.app.ui.components.SwipeTip
import com.motivbot.app.ui.components.TopBar
import com.motivbot.app.ui.components.dsAppear
import com.motivbot.app.ui.components.dsCard
import com.motivbot.app.ui.components.dsGlass
import com.motivbot.app.ui.components.icon
import com.motivbot.app.ui.components.pressable
import com.motivbot.app.ui.export.ExportFiles
import com.motivbot.app.ui.export.ExportOptionsSheet
import com.motivbot.app.ui.theme.DS
import com.motivbot.app.ui.theme.DSFont
import com.motivbot.app.ui.theme.DSMotion
import com.motivbot.app.ui.theme.DSRadius
import com.motivbot.app.ui.theme.DSSize
import com.motivbot.app.ui.theme.DSSpacing
import kotlinx.coroutines.delay

@Composable
fun ResultScreen(viewModel: WellBeingViewModel) {
    val view = LocalView.current
    val context = LocalContext.current
    var showExportOptions by rememberSaveable { mutableStateOf(false) }
    // The download button is only on the results, not on the thank-you screen after Submit.
    val canExport = !viewModel.isCompleted && viewModel.resultSummary != null
    val reduceMotion = DS.reduceMotion

    LaunchedEffect(Unit) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }

    ScreenScaffold(
        topBar = {
            TopBar(trailing = {
                AnimatedVisibility(
                    canExport,
                    enter = fadeIn(DSMotion.soft()) + scaleIn(DSMotion.soft(), initialScale = if (reduceMotion) 1f else 0.8f),
                    exit = fadeOut(DSMotion.soft()) + scaleOut(DSMotion.soft(), targetScale = if (reduceMotion) 1f else 0.8f),
                ) {
                    GlassIconButton(Icons.Outlined.Download, stringResource(R.string.export_button)) { showExportOptions = true }
                }
            })
        },
    ) { padding ->
        ScreenColumn(Modifier.padding(padding).padding(top = DSSpacing.xs, bottom = DSSpacing.xl)) {
            AnimatedContent(
                viewModel.isCompleted,
                transitionSpec = {
                    (fadeIn(DSMotion.soft()) + scaleIn(DSMotion.soft(), initialScale = if (reduceMotion) 1f else 0.96f)) togetherWith
                        fadeOut(DSMotion.soft())
                },
                label = "result",
            ) { completed ->
                if (completed) {
                    Completion {
                        ExportFiles.removeExports(context)
                        viewModel.restartAssessment()
                    }
                } else {
                    Results(viewModel)
                }
            }
        }
    }

    if (showExportOptions) {
        val summary = viewModel.resultSummary
        if (summary == null) {
            showExportOptions = false
        } else {
            ExportOptionsSheet(summary, canShareCsv = viewModel.hasCsv, onDismiss = { showExportOptions = false })
        }
    }
}

// MARK: - Results

@Composable
private fun Results(viewModel: WellBeingViewModel) {
    val colors = DS.colors
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.l)) {
        Column(Modifier.dsAppear(), verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            Text(
                stringResource(R.string.result_title),
                style = DSFont.display,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.result_subtitle), style = DSFont.body, color = colors.textSecondary)
        }

        val prediction = viewModel.prediction
        if (prediction != null) {
            // The levels are only in the PDF / image summary, not on this screen.
            if (prediction.messages.isNotEmpty()) Messages(prediction.messages, Modifier.dsAppear(200))
            InfoNote(stringResource(R.string.result_disclaimer), Modifier.padding(horizontal = DSSpacing.xxs).dsAppear(350))
        }

        Reflection(viewModel, Modifier.dsAppear(450))
    }
}

@Composable
private fun Messages(messages: List<PredictionResponse.Message>, modifier: Modifier) {
    val ordered = remember(messages) { MessageCategoryStyle.ordered(messages) }
    // The swipe tip pops up once by itself; the info button next to the title shows it again.
    var showSwipeTip by remember { mutableStateOf(false) }
    var hasShownSwipeTip by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (hasShownSwipeTip) return@LaunchedEffect
        delay(1_200)
        hasShownSwipeTip = true
        showSwipeTip = true
        delay(5_000)
        showSwipeTip = false
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(DSSpacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            SectionTitle(stringResource(R.string.messages_title), Modifier.weight(1f))
            SwipeTipButton(showSwipeTip, stringResource(R.string.messages_swipe_hint), stringResource(R.string.messages_swipe_info)) {
                showSwipeTip = it
            }
        }
        MessageDeck(
            ordered,
            MessageDeckStrings(
                hint = stringResource(R.string.messages_swipe_hint),
                next = stringResource(R.string.message_next),
                doneTitle = stringResource(R.string.messages_done_title),
                doneBody = stringResource(R.string.messages_done_body),
                replay = stringResource(R.string.messages_replay),
                categoryTitle = { stringResource(it.titleRes) },
            ),
        )
    }
}

@Composable
private fun SwipeTipButton(isShown: Boolean, tip: String, label: String, onShownChange: (Boolean) -> Unit) {
    val colors = DS.colors
    val density = LocalDensity.current
    Box {
        Box(
            Modifier
                .size(DSSize.minTap)
                .pressable { onShownChange(true) }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.brand, modifier = Modifier.size(22.dp))
        }
        if (isShown) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(density) { (DSSize.minTap + 4.dp).roundToPx() }),
                onDismissRequest = { onShownChange(false) },
                properties = PopupProperties(focusable = false),
            ) {
                val appear = remember { Animatable(0f) }
                LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) }
                Box(
                    Modifier
                        .padding(DSSpacing.xs)
                        .widthIn(max = 300.dp)
                        .graphicsLayer {
                            alpha = appear.value
                            scaleX = 0.9f + 0.1f * appear.value
                            scaleY = 0.9f + 0.1f * appear.value
                        }
                        .dsGlass(RoundedCornerShape(DSRadius.medium))
                        .pressable { onShownChange(false) },
                ) {
                    SwipeTip(tip)
                }
            }
        }
    }
}

// MARK: - Reflection

@Composable
private fun Reflection(viewModel: WellBeingViewModel, modifier: Modifier) {
    val colors = DS.colors
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    var lastRatings by remember { mutableStateOf(viewModel.reflectionRatings) }
    LaunchedEffect(viewModel.reflectionRatings) {
        if (viewModel.reflectionRatings != lastRatings) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        lastRatings = viewModel.reflectionRatings
    }

    Column(modifier.dsCard(), verticalArrangement = Arrangement.spacedBy(DSSpacing.m)) {
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xxs)) {
            Text(
                stringResource(R.string.reflection_title),
                style = DSFont.heading,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.reflection_question), style = DSFont.callout, color = colors.textSecondary)
        }

        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.m)) {
            ReflectionTopic.entries.forEach { topic ->
                ReflectionRatingRow(
                    title = stringResource(topic.titleRes),
                    icon = topic.icon,
                    ratingTitle = { stringResource(it.titleRes) },
                    selection = viewModel.reflectionRatings[topic],
                ) { rating -> viewModel.rateReflection(topic, rating) }
            }
        }

        AnimatedVisibility(!viewModel.canSubmitReflection, enter = fadeIn(DSMotion.quick()), exit = fadeOut(DSMotion.quick())) {
            Text(stringResource(R.string.reflection_required_hint), style = DSFont.caption, color = colors.textSecondary)
        }

        NoteField(viewModel.reflectionText, { viewModel.reflectionText = it })

        DSPrimaryButton(
            stringResource(R.string.submit_reflection),
            icon = Icons.Filled.Check,
            enabled = viewModel.canSubmitReflection,
        ) {
            focusManager.clearFocus()
            viewModel.submitReflection()
        }
    }
}

@Composable
private fun NoteField(text: String, onChange: (String) -> Unit) {
    val colors = DS.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(DSRadius.medium)
    val stroke by animateColorAsState(if (focused) colors.brand else colors.stroke, DSMotion.quick(), label = "stroke")
    val label = stringResource(R.string.reflection_optional)
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
        Text(label, style = DSFont.caption, color = colors.textSecondary)
        BasicTextField(
            value = text,
            onValueChange = onChange,
            minLines = 3,
            maxLines = 6,
            textStyle = DSFont.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.brand),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DSSize.minTap)
                        .background(colors.surfaceMuted, shape)
                        .border(if (focused) 1.5.dp else 1.dp, stroke, shape)
                        .padding(DSSpacing.s),
                ) {
                    if (text.isEmpty()) {
                        Text(stringResource(R.string.reflection_placeholder), style = DSFont.body, color = colors.textTertiary)
                    }
                    inner()
                }
            },
        )
    }
}

// MARK: - Completion

@Composable
private fun Completion(onRestart: () -> Unit) {
    val colors = DS.colors
    val reduceMotion = DS.reduceMotion
    val bounce = remember { Animatable(if (reduceMotion) 1f else 0.6f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) bounce.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DSSpacing.l),
    ) {
        Spacer(Modifier.height(DSSpacing.xxl))
        Box(
            Modifier
                .size(96.dp)
                .graphicsLayer {
                    scaleX = bounce.value
                    scaleY = bounce.value
                }
                .background(colors.steadySoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Eco, contentDescription = null, tint = colors.steady, modifier = Modifier.size(44.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.s)) {
            Text(
                stringResource(R.string.completion_title),
                style = DSFont.display,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.completion_message),
                style = DSFont.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        DSSecondaryButton(stringResource(R.string.restart_button), icon = Icons.Filled.Replay, onClick = onRestart)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = DSFont.heading,
        color = DS.colors.textPrimary,
        modifier = modifier.semantics { heading() },
    )
}
