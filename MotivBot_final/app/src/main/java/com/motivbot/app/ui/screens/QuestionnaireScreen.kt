package com.motivbot.app.ui.screens

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.motivbot.app.R
import com.motivbot.app.domain.AnswerOption
import com.motivbot.app.domain.PredictionRequestBuilder
import com.motivbot.app.domain.Question
import com.motivbot.app.domain.QuestionType
import com.motivbot.app.domain.SectionDisplayMode
import com.motivbot.app.domain.TestSection
import com.motivbot.app.ui.WellBeingViewModel
import com.motivbot.app.ui.components.AnswerOptionRow
import com.motivbot.app.ui.components.Banner
import com.motivbot.app.ui.components.BannerKind
import com.motivbot.app.ui.components.BottomActionBar
import com.motivbot.app.ui.components.ChipGroup
import com.motivbot.app.ui.components.DSPrimaryButton
import com.motivbot.app.ui.components.GlassIconButton
import com.motivbot.app.ui.components.ScreenColumn
import com.motivbot.app.ui.components.ScreenScaffold
import com.motivbot.app.ui.components.SegmentedProgressBar
import com.motivbot.app.ui.components.SelectableChip
import com.motivbot.app.ui.components.TopBar
import com.motivbot.app.ui.components.dsCard
import com.motivbot.app.ui.components.pressable
import com.motivbot.app.ui.localizedNumber
import com.motivbot.app.ui.theme.DS
import com.motivbot.app.ui.theme.DSFont
import com.motivbot.app.ui.theme.DSMotion
import com.motivbot.app.ui.theme.DSRadius
import com.motivbot.app.ui.theme.DSSize
import com.motivbot.app.ui.theme.DSSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class Position(val section: Int, val question: Int)

@Composable
fun QuestionnaireScreen(viewModel: WellBeingViewModel) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val reduceMotion = DS.reduceMotion
    val rtl = DS.isRtl

    // Moves on after a single-choice answer; cancelled when the user acts first.
    var advanceJob by remember { mutableStateOf<Job?>(null) }
    var savedJob by remember { mutableStateOf<Job?>(null) }
    // Sets which way the question card slides.
    var isNavigatingBack by remember { mutableStateOf(false) }
    var showsSaved by remember { mutableStateOf(false) }
    var previousAnswerCount by remember { mutableIntStateOf(viewModel.answers.size) }

    val canGoBack = viewModel.currentSectionIndex > 0 || viewModel.currentQuestionIndex > 0
    val position = Position(viewModel.currentSectionIndex, viewModel.currentQuestionIndex)

    fun advance() {
        advanceJob?.cancel()
        focusManager.clearFocus()
        isNavigatingBack = false
        viewModel.nextQuestion()
    }

    fun goBack() {
        if (!(viewModel.currentSectionIndex > 0 || viewModel.currentQuestionIndex > 0)) return
        advanceJob?.cancel()
        focusManager.clearFocus()
        isNavigatingBack = true
        // previousQuestion() clears the selection; the earlier answer is put back so going back is cheap.
        viewModel.goBackRestoringAnswer()
    }

    fun choose(option: AnswerOption) {
        viewModel.selectOption(option.id)
        advanceJob?.cancel()
        if (viewModel.isLastQuestion) return
        val key = Position(viewModel.currentSectionIndex, viewModel.currentQuestionIndex)
        advanceJob = scope.launch {
            delay(DSMotion.AUTO_ADVANCE_DELAY_MILLIS)
            val current = Position(viewModel.currentSectionIndex, viewModel.currentQuestionIndex)
            if (key == current && viewModel.canProceedQuestion) advance()
        }
    }

    LaunchedEffect(viewModel.selectedOptionId) {
        if (viewModel.selectedOptionId != null) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    var lastSectionIndex by remember { mutableIntStateOf(viewModel.currentSectionIndex) }
    LaunchedEffect(viewModel.currentSectionIndex) {
        if (viewModel.currentSectionIndex != lastSectionIndex) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        lastSectionIndex = viewModel.currentSectionIndex
    }
    LaunchedEffect(viewModel.answers.size) {
        val count = viewModel.answers.size
        if (count > previousAnswerCount) {
            savedJob?.cancel()
            savedJob = scope.launch {
                // Saving is debounced in the view model; show the note once it has had time to run.
                delay(700)
                showsSaved = true
                delay(1800)
                showsSaved = false
            }
        }
        previousAnswerCount = count
    }
    DisposableEffect(Unit) {
        onDispose {
            advanceJob?.cancel()
            savedJob?.cancel()
        }
    }
    BackHandler(enabled = canGoBack) { goBack() }

    val swipeThreshold = 80.dp
    val swipeTolerance = 60.dp

    ScreenScaffold(
        topBar = { Header(viewModel, canGoBack, showsSaved) { goBack() } },
        bottomBar = {
            BottomActionBar {
                val isLast = viewModel.isLastQuestion
                DSPrimaryButton(
                    stringResource(if (isLast) R.string.finish_button else R.string.next_button),
                    icon = if (isLast) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                    enabled = viewModel.canProceedQuestion,
                ) { advance() }
            }
        },
        // Swiping towards the trailing edge goes back, like the system back gesture (mirrored in Persian).
        // The gesture only watches, so scrolling and taps still work.
        contentModifier = Modifier.pointerInput(rtl) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var last = down.position
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    last = change.position
                    if (!change.pressed) break
                }
                val dx = last.x - down.position.x
                val dy = last.y - down.position.y
                if (abs(dx) > swipeThreshold.toPx() && abs(dy) < swipeTolerance.toPx()) {
                    val towardsTrailing = if (rtl) dx < 0 else dx > 0
                    if (towardsTrailing) goBack()
                }
            }
        },
    ) { padding ->
        ScreenColumn(Modifier.padding(padding).padding(top = DSSpacing.xs, bottom = DSSpacing.l)) {
            val section = viewModel.currentSection ?: return@ScreenColumn
            SectionIntro(section, isFirstQuestion = viewModel.currentQuestionIndex == 0 || section.displayMode == SectionDisplayMode.AllAtOnce)

            AnimatedContent(
                targetState = position,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(DSMotion.standard()) togetherWith fadeOut(DSMotion.standard())
                    } else if (isNavigatingBack) {
                        (slideIntoContainer(SlideDirection.End, DSMotion.standard()) + fadeIn(DSMotion.standard())) togetherWith
                            (slideOutOfContainer(SlideDirection.End, DSMotion.standard()) + fadeOut(DSMotion.standard()))
                    } else {
                        (slideIntoContainer(SlideDirection.Start, DSMotion.standard()) + fadeIn(DSMotion.standard())) togetherWith
                            (slideOutOfContainer(SlideDirection.Start, DSMotion.standard()) + fadeOut(DSMotion.standard()))
                    }
                },
                label = "question",
            ) { target ->
                val targetSection = viewModel.sections.getOrNull(target.section) ?: return@AnimatedContent
                if (targetSection.displayMode == SectionDisplayMode.AllAtOnce) {
                    GridSection(viewModel, targetSection)
                } else {
                    val question = targetSection.questions.getOrNull(target.question) ?: return@AnimatedContent
                    QuestionBlock(viewModel, targetSection, target.question, question, ::choose)
                }
            }
        }
    }
}

// MARK: - Header

@Composable
private fun Header(viewModel: WellBeingViewModel, canGoBack: Boolean, showsSaved: Boolean, onBack: () -> Unit) {
    val colors = DS.colors
    Column {
        TopBar(leading = {
            AnimatedVisibility(canGoBack, enter = fadeIn(DSMotion.standard()), exit = fadeOut(DSMotion.standard())) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.previous_button), onClick = onBack)
            }
        })
        ScreenColumn(Modifier.padding(bottom = DSSpacing.s), spacing = DSSpacing.xs) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        R.string.section_progress,
                        localizedNumber(viewModel.currentSectionIndex + 1),
                        localizedNumber(viewModel.sections.size),
                    ),
                    style = DSFont.label,
                    color = colors.brand,
                )
                Spacer(Modifier.weight(1f).width(DSSpacing.xs))
                AnimatedVisibility(showsSaved, enter = fadeIn(DSMotion.gentle()), exit = fadeOut(DSMotion.gentle())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.progress_saved), style = DSFont.caption, color = colors.textSecondary)
                    }
                }
            }
            SegmentedProgressBar(
                weights = viewModel.sections.map { it.questions.size },
                fractions = viewModel.sections.map { section ->
                    val answered = viewModel.answers.count { it.sectionId == section.id }
                    answered.toDouble() / maxOf(viewModel.shownQuestionCount(section), 1)
                },
                label = stringResource(R.string.progress_label),
                value = stringResource(
                    R.string.overall_progress_value,
                    localizedNumber(viewModel.answeredQuestions),
                    localizedNumber(viewModel.totalQuestions),
                ),
            )
        }
    }
}

// MARK: - Section intro

/** The full title and description on the first question of a section, the title alone afterwards. */
@Composable
private fun SectionIntro(section: TestSection, isFirstQuestion: Boolean) {
    val colors = DS.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
        Text(
            stringResource(section.titleRes),
            style = if (isFirstQuestion) DSFont.title else DSFont.label,
            color = if (isFirstQuestion) colors.textPrimary else colors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        AnimatedVisibility(isFirstQuestion, enter = fadeIn(DSMotion.standard()), exit = fadeOut(DSMotion.standard())) {
            Text(stringResource(section.descriptionRes), style = DSFont.callout, color = colors.textSecondary)
        }
    }
}

// MARK: - Question

@Composable
private fun QuestionBlock(
    viewModel: WellBeingViewModel,
    section: TestSection,
    questionIndex: Int,
    question: Question,
    onChoose: (AnswerOption) -> Unit,
) {
    val colors = DS.colors
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.m)) {
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            Text(
                stringResource(
                    R.string.question_progress,
                    localizedNumber(questionIndex + 1),
                    localizedNumber(viewModel.shownQuestionCount(section)),
                ),
                style = DSFont.caption,
                color = colors.textSecondary,
            )
            Text(
                stringResource(question.textRes),
                style = DSFont.question,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        when (val type = question.type) {
            QuestionType.SingleChoice -> Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                question.options.orEmpty().forEach { option ->
                    AnswerOptionRow(stringResource(option.textRes), viewModel.selectedOptionId == option.id) { onChoose(option) }
                }
            }
            is QuestionType.Scale -> ScaleInput(viewModel, type.range)
            QuestionType.FreeText -> FreeTextInput(viewModel, question.isOptional)
            is QuestionType.IntegerInput -> IntegerInput(viewModel, question, type.range)
        }
    }
}

// MARK: - Inputs

@Composable
private fun ScaleInput(viewModel: WellBeingViewModel, range: IntRange) {
    val colors = DS.colors
    val view = LocalView.current
    LaunchedEffect(viewModel.scaleValue) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    Column(Modifier.dsCard(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.m)) {
        Text(localizedNumber(viewModel.scaleValue), style = DSFont.display, color = colors.brand)
        val sliderLabel = stringResource(R.string.question_label)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.s)) {
            StepButton(Icons.Filled.Remove, viewModel.scaleValue > range.first) { viewModel.scaleValue -= 1 }
            Text(localizedNumber(range.first), style = DSFont.footnote, color = colors.textSecondary)
            Slider(
                value = viewModel.scaleValue.toFloat(),
                onValueChange = { viewModel.scaleValue = Math.round(it) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first - 1).coerceAtLeast(0),
                colors = SliderDefaults.colors(thumbColor = colors.brand, activeTrackColor = colors.brand, inactiveTrackColor = colors.brandSoft),
                modifier = Modifier.weight(1f).semantics { contentDescription = sliderLabel },
            )
            Text(localizedNumber(range.last), style = DSFont.footnote, color = colors.textSecondary)
            StepButton(Icons.Filled.Add, viewModel.scaleValue < range.last) { viewModel.scaleValue += 1 }
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val colors = DS.colors
    Box(
        Modifier
            .size(DSSize.minTap)
            .background(colors.brandSoft, CircleShape)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) colors.brand else colors.textTertiary)
    }
}

@Composable
private fun FreeTextInput(viewModel: WellBeingViewModel, isOptional: Boolean) {
    val colors = DS.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(DSRadius.medium)
    val stroke by animateColorAsState(if (focused) colors.brand else colors.stroke, DSMotion.quick(), label = "stroke")
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
        if (isOptional) Text(stringResource(R.string.question_optional), style = DSFont.caption, color = colors.textSecondary)
        BasicTextField(
            value = viewModel.freeTextAnswer,
            onValueChange = { viewModel.freeTextAnswer = it },
            textStyle = DSFont.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.brand),
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .background(colors.surface, shape)
                        .border(if (focused) 1.5.dp else 1.dp, stroke, shape)
                        .padding(DSSpacing.s),
                ) {
                    if (viewModel.freeTextAnswer.isEmpty()) {
                        Text(stringResource(R.string.free_text_placeholder), style = DSFont.body, color = colors.textTertiary)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun IntegerInput(viewModel: WellBeingViewModel, question: Question, range: IntRange) {
    val colors = DS.colors
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(DSRadius.medium)
    val warning = integerWarning(viewModel, range, focused)
    val strokeColor by animateColorAsState(
        when {
            warning != null -> colors.gentle
            focused -> colors.brand
            else -> colors.stroke
        },
        DSMotion.quick(),
        label = "stroke",
    )
    val strokeWidth by animateDpAsState(if (focused) 1.5.dp else 1.dp, DSMotion.quick(), label = "width")
    val label = stringResource(question.textRes)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.s)) {
        BasicTextField(
            value = viewModel.freeTextAnswer,
            onValueChange = { viewModel.freeTextAnswer = it.take(3) },
            singleLine = true,
            textStyle = DSFont.title.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.brand),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DSSize.answerOptionMinHeight)
                        .background(colors.surface, shape)
                        .border(strokeWidth, strokeColor, shape)
                        .padding(horizontal = DSSpacing.m),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (viewModel.freeTextAnswer.isEmpty()) {
                        Text(stringResource(R.string.free_text_placeholder), style = DSFont.title, color = colors.textTertiary)
                    }
                    inner()
                }
            },
        )
        AnimatedVisibility(warning != null, enter = fadeIn(DSMotion.quick()), exit = fadeOut(DSMotion.quick())) {
            // Keep the last text while fading out.
            var shown by remember { mutableStateOf(warning.orEmpty()) }
            if (warning != null) shown = warning
            Banner(BannerKind.Notice, shown)
        }
    }
}

/**
 * The message under a number answer, or null when it is empty or valid.
 * An age under 18 gets its own message; a single digit waits until the user stops typing.
 */
@Composable
private fun integerWarning(viewModel: WellBeingViewModel, range: IntRange, focused: Boolean): String? {
    val text = viewModel.freeTextAnswer.trim()
    if (text.isEmpty() || viewModel.integerAnswer(range) != null) return null
    if (range == PredictionRequestBuilder.AGE_RANGE) {
        val value = wholeNumber(text)
        if (value != null && value < range.first) {
            return if (text.codePointCount(0, text.length) >= 2 || !focused) stringResource(R.string.age_under_18_warning) else null
        }
    }
    return stringResource(R.string.integer_invalid_hint, range.first, range.last)
}

/** Reads any Unicode digits, e.g. Persian "۱۶". Display only; the view model validates the answer. */
private fun wholeNumber(text: String): Int? {
    var value = 0
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val digit = Character.digit(codePoint, 10)
        if (digit < 0) return null
        value = value * 10 + digit
        index += Character.charCount(codePoint)
    }
    return value
}

// MARK: - Grid section

/** Each item gets its own card with wrapping answer chips. */
@Composable
private fun GridSection(viewModel: WellBeingViewModel, section: TestSection) {
    val colors = DS.colors
    val view = LocalView.current
    LaunchedEffect(viewModel.gridAnswers) {
        if (viewModel.gridAnswers.isNotEmpty()) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.s)) {
        section.questions.forEach { question ->
            Column(Modifier.dsCard(padding = DSSpacing.m), verticalArrangement = Arrangement.spacedBy(DSSpacing.s)) {
                Text(stringResource(question.textRes), style = DSFont.heading, color = colors.textPrimary)
                ChipGroup {
                    question.options.orEmpty().forEach { option ->
                        SelectableChip(stringResource(option.textRes), viewModel.gridAnswers[question.id] == option.id) {
                            viewModel.selectGridOption(question.id, option.id)
                        }
                    }
                }
            }
        }
    }
}
