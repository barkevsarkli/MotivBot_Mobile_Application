package com.motivbot.app.ui.questionnaire

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motivbot.app.R
import com.motivbot.app.domain.model.AnswerOption
import com.motivbot.app.domain.model.Question
import com.motivbot.app.domain.model.QuestionType
import com.motivbot.app.domain.model.SectionDisplayMode
import com.motivbot.app.domain.model.TestSection
import com.motivbot.app.ui.WellBeingUiState
import com.motivbot.app.ui.components.BrandGradient
import com.motivbot.app.ui.components.BrandGradientDiagonal
import com.motivbot.app.ui.components.BrandRadioDot
import com.motivbot.app.ui.components.GlassCard
import com.motivbot.app.ui.components.GradientButton
import com.motivbot.app.ui.components.clickableNoRipple
import com.motivbot.app.ui.theme.BrandViolet
import kotlin.math.roundToInt

/**
 * Port of `QuestionnaireView.swift` — screen 2.
 *
 * Structure is unchanged: progress bar, section header, then either a one-question-at-a-time card
 * or the `AllAtOnce` radio grid, then the navigation row. The Swift `.transition(.asymmetric(...))`
 * on question change becomes `AnimatedContent` keyed on the section/question index pair.
 */
@Composable
fun QuestionnaireScreen(
    state: WellBeingUiState,
    onSelectOption: (Int) -> Unit,
    onSelectGridOption: (questionId: Int, optionId: Int) -> Unit,
    onScaleChange: (Int) -> Unit,
    onFreeTextChange: (String) -> Unit,
    onNumberChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showContent = true }

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(70.dp))

        ProgressSection(
            answered = state.answeredQuestions,
            total = state.totalQuestions,
            progress = state.overallProgress,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // `weight` rather than `fillMaxSize`: the progress bar above must keep its height,
                // and only the remaining space should scroll.
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            state.currentSection?.let { section ->
                SectionHeader(section)

                if (section.displayMode == SectionDisplayMode.AllAtOnce) {
                    GridSection(
                        section = section,
                        gridAnswers = state.gridAnswers,
                        onSelect = onSelectGridOption,
                    )
                } else {
                    AnimatedContent(
                        targetState = state.currentSectionIndex to state.currentQuestionIndex,
                        transitionSpec = {
                            (slideInHorizontally(tween(350)) { it } + fadeIn(tween(350)))
                                .togetherWith(
                                    slideOutHorizontally(tween(350)) { -it } + fadeOut(tween(350)),
                                )
                        },
                        label = "questionTransition",
                    ) { (sectionIndex, questionIndex) ->
                        state.sections.getOrNull(sectionIndex)
                            ?.questions
                            ?.getOrNull(questionIndex)
                            ?.let { question ->
                                QuestionCard(
                                    question = question,
                                    questionNumber = questionIndex + 1,
                                    selectedOptionId = state.selectedOptionId,
                                    scaleValue = state.scaleValue,
                                    freeTextAnswer = state.freeTextAnswer,
                                    numberAnswer = state.numberAnswer,
                                    showNumberError = state.showNumberError,
                                    onSelectOption = onSelectOption,
                                    onScaleChange = onScaleChange,
                                    onFreeTextChange = onFreeTextChange,
                                    onNumberChange = onNumberChange,
                                )
                            }
                    }
                }
            }

            NavigationButtons(
                canGoBack = state.canGoBack,
                canProceed = state.canProceedQuestion,
                isLastQuestion = state.isLastQuestion,
                onNext = onNext,
                onPrevious = onPrevious,
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─── Progress ───────────────────────────────────────────────────────────────────

@Composable
private fun ProgressSection(answered: Int, total: Int, progress: Float) {
    // Swift: `.animation(.spring(response: 0.5, dampingFraction: 0.7), value: overallProgress)`
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.7f),
        label = "progress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 0.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.progress_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            )
            Text(
                text = "$answered/$total",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Gray.copy(alpha = 0.15f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BrandGradient),
            )
        }
    }
}

@Composable
private fun SectionHeader(section: TestSection) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BrandGradientDiagonal),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(section.titleRes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Text(
            text = stringResource(section.descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        )
    }
}

// ─── AllAtOnce grid (Section E) ─────────────────────────────────────────────────

@Composable
private fun GridSection(
    section: TestSection,
    gridAnswers: Map<Int, Int>,
    onSelect: (questionId: Int, optionId: Int) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Column headers, taken from the first question's option labels.
            section.questions.firstOrNull()?.options?.let { options ->
                Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Spacer(Modifier.width(100.dp))
                    options.forEach { option ->
                        Text(
                            text = stringResource(option.textRes),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            section.questions.forEach { question ->
                GridRow(
                    question = question,
                    selectedOptionId = gridAnswers[question.id],
                    onSelect = { optionId -> onSelect(question.id, optionId) },
                )
            }
        }
    }
}

@Composable
private fun GridRow(
    question: Question,
    selectedOptionId: Int?,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selectedOptionId != null) BrandViolet.copy(alpha = 0.04f) else Color.Transparent,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(question.textRes),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(100.dp),
        )

        question.options?.forEach { option ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickableNoRipple { onSelect(option.id) },
                contentAlignment = Alignment.Center,
            ) {
                BrandRadioDot(
                    selected = selectedOptionId == option.id,
                    outerSize = 22.dp,
                    innerSize = 12.dp,
                )
            }
        }
    }
}

// ─── Sequential question card ───────────────────────────────────────────────────

@Composable
private fun QuestionCard(
    question: Question,
    questionNumber: Int,
    selectedOptionId: Int?,
    scaleValue: Int,
    freeTextAnswer: String,
    numberAnswer: String,
    showNumberError: Boolean,
    onSelectOption: (Int) -> Unit,
    onScaleChange: (Int) -> Unit,
    onFreeTextChange: (String) -> Unit,
    onNumberChange: (String) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Question-number pill.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(BrandGradient)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "${stringResource(R.string.question_label)} $questionNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }

            Text(
                text = stringResource(question.textRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            // The SAM artwork for section F.
            question.imageRes?.let { imageRes ->
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                )
            }

            when (val type = question.type) {
                is QuestionType.SingleChoice -> Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    question.options?.forEach { option ->
                        OptionRow(
                            option = option,
                            selected = selectedOptionId == option.id,
                            onClick = { onSelectOption(option.id) },
                        )
                    }
                }

                is QuestionType.Scale -> ScaleInput(
                    range = type.range,
                    value = scaleValue,
                    onValueChange = onScaleChange,
                )

                is QuestionType.FreeText -> FreeTextInput(
                    value = freeTextAnswer,
                    onValueChange = onFreeTextChange,
                )

                is QuestionType.Number -> NumberInput(
                    value = numberAnswer,
                    range = type.range,
                    isError = showNumberError,
                    onValueChange = onNumberChange,
                )
            }
        }
    }
}

@Composable
private fun OptionRow(option: AnswerOption, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) BrandViolet.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) BrandViolet.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrandRadioDot(selected = selected)
        Text(
            text = stringResource(option.textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ScaleInput(range: IntRange, value: Int, onValueChange: (Int) -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = value.toString(),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = BrandViolet,
        )

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            // `steps` counts the *intermediate* stops, hence the -1 for an inclusive 1..9 range.
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = BrandViolet,
                activeTrackColor = BrandViolet,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = range.first.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            )
            Text(
                text = range.last.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            )
        }
    }
}

/**
 * Bounded whole-number entry — used for the age question.
 *
 * Three things make garbage impossible rather than merely discouraged: the keyboard is numeric, the
 * ViewModel filters non-digits out of the state, and Next stays disabled until the value parses
 * inside [range]. The inline hint tells the user the accepted range up front instead of only
 * scolding them after a bad entry.
 */
@Composable
private fun NumberInput(
    value: String,
    range: IntRange,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = isError,
            placeholder = {
                Text(
                    text = stringResource(R.string.age_placeholder),
                    color = Color.Gray.copy(alpha = 0.5f),
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
        )

        Text(
            text = if (isError) {
                stringResource(R.string.age_error, range.first, range.last)
            } else {
                stringResource(R.string.age_hint, range.first, range.last)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            },
        )
    }
}

@Composable
private fun FreeTextInput(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        placeholder = {
            Text(
                text = stringResource(R.string.free_text_placeholder),
                color = Color.Gray.copy(alpha = 0.5f),
            )
        },
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

// ─── Navigation ─────────────────────────────────────────────────────────────────

@Composable
private fun NavigationButtons(
    canGoBack: Boolean,
    canProceed: Boolean,
    isLastQuestion: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (canGoBack) {
            val backDescription = stringResource(R.string.previous_button_desc)
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BrandViolet.copy(alpha = 0.1f))
                    .clickableNoRipple(onPrevious),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backDescription,
                    tint = BrandViolet,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        GradientButton(
            text = stringResource(
                if (isLastQuestion) R.string.finish_button else R.string.next_button,
            ),
            enabled = canProceed,
            onClick = onNext,
            modifier = Modifier.weight(1f),
            trailingIcon = {
                Icon(
                    imageVector = if (isLastQuestion) {
                        Icons.Filled.Check
                    } else {
                        Icons.AutoMirrored.Filled.ArrowForward
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}
