package com.motivbot.app.ui.result

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motivbot.app.R
import com.motivbot.app.ui.Feeling
import com.motivbot.app.ui.FeelingEmojis
import com.motivbot.app.ui.components.FadeInSlideUp
import com.motivbot.app.ui.components.GlassCard
import com.motivbot.app.ui.components.GradientButton
import com.motivbot.app.ui.components.clickableNoRipple
import com.motivbot.app.ui.theme.AccentGoldStart
import com.motivbot.app.ui.theme.BrandViolet
import com.motivbot.app.ui.theme.SuccessGreenEnd
import com.motivbot.app.ui.theme.SuccessGreenStart

/**
 * Port of `ResultView.swift` — screen 4.
 *
 * One behavioural change: the Swift view auto-presented the share sheet 1.5 s after appearing via
 * `DispatchQueue.main.asyncAfter`, which hijacks the screen before the user has read their result.
 * Here the CSV share is offered once through [onShareRequested], driven by a one-shot state flag,
 * and there is an explicit share affordance — no timer racing the user.
 */
@Composable
fun ResultScreen(
    aiResponse: String,
    reflectionText: String,
    selectedFeelingIndex: Int?,
    isCompleted: Boolean,
    shouldOfferShare: Boolean,
    onReflectionTextChange: (String) -> Unit,
    onSelectFeeling: (Int) -> Unit,
    onSubmitReflection: () -> Unit,
    onRestart: () -> Unit,
    onShareRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showQuote by remember { mutableStateOf(false) }
    var showReflection by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showQuote = true
        showReflection = true
    }

    LaunchedEffect(shouldOfferShare) {
        if (shouldOfferShare) onShareRequested()
    }

    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Spacer(Modifier.height(70.dp))

        if (!isCompleted) {
            FadeInSlideUp(visible = showQuote, delayMillis = 200) {
                QuoteCard(aiResponse)
            }

            FadeInSlideUp(visible = showReflection, delayMillis = 800) {
                ReflectionSection(
                    reflectionText = reflectionText,
                    selectedFeelingIndex = selectedFeelingIndex,
                    onReflectionTextChange = onReflectionTextChange,
                    onSelectFeeling = onSelectFeeling,
                )
            }

            FadeInSlideUp(visible = showReflection, delayMillis = 800) {
                GradientButton(
                    text = stringResource(R.string.submit_reflection),
                    enabled = true,
                    onClick = {
                        keyboard?.hide()
                        onSubmitReflection()
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        } else {
            CompletionView(onRestart = onRestart)
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ─── Quote card ─────────────────────────────────────────────────────────────────

@Composable
private fun QuoteCard(aiResponse: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        borderColor = AccentGoldStart.copy(alpha = 0.3f),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = AccentGoldStart,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.result_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

            Text(
                // Swift fell back to `api_error_fallback` when `apiResponse` was empty.
                text = aiResponse.ifBlank { stringResource(R.string.api_error_fallback) },
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "”",
                    fontSize = 30.sp,
                    color = Color.Gray.copy(alpha = 0.3f),
                )
            }
        }
    }
}

// ─── Reflection ─────────────────────────────────────────────────────────────────

@Composable
private fun ReflectionSection(
    reflectionText: String,
    selectedFeelingIndex: Int?,
    onReflectionTextChange: (String) -> Unit,
    onSelectFeeling: (Int) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.reflection_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.reflection_question),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeelingEmojis.forEachIndexed { index, feeling ->
                    FeelingChip(
                        feeling = feeling,
                        selected = selectedFeelingIndex == index,
                        onClick = { onSelectFeeling(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.reflection_optional),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                )
                OutlinedTextField(
                    value = reflectionText,
                    onValueChange = onReflectionTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = 160.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.reflection_placeholder),
                            color = Color.Gray.copy(alpha = 0.5f),
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun FeelingChip(
    feeling: Feeling,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Swift: `.spring(response: 0.3, dampingFraction: 0.7)` on the emoji scale.
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "feelingScale",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) BrandViolet.copy(alpha = 0.1f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) BrandViolet.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickableNoRipple(onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = feeling.emoji,
            fontSize = if (selected) 30.sp else 26.sp,
            modifier = Modifier.scale(scale),
        )
        Text(
            text = stringResource(feeling.labelRes),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            },
        )
    }
}

// ─── Completion ─────────────────────────────────────────────────────────────────

@Composable
private fun CompletionView(onRestart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(SuccessGreenStart, SuccessGreenEnd))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp),
            )
        }

        Text(
            text = stringResource(R.string.completion_title),
            fontSize = 26.sp,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.completion_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .border(2.dp, BrandViolet, CircleShape)
                .clickableNoRipple(onRestart)
                .padding(horizontal = 28.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = BrandViolet,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.restart_button),
                style = MaterialTheme.typography.bodyLarge,
                color = BrandViolet,
            )
        }
    }
}
