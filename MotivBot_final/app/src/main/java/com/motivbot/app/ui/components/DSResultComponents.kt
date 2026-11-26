package com.motivbot.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motivbot.app.domain.Level
import com.motivbot.app.domain.MessageCategoryStyle
import com.motivbot.app.domain.PredictionResponse
import com.motivbot.app.domain.ReflectionRating
import com.motivbot.app.domain.ReflectionTopic
import com.motivbot.app.domain.WellBeingDimension
import com.motivbot.app.ui.theme.DS
import com.motivbot.app.ui.theme.DSColors
import com.motivbot.app.ui.theme.DSFont
import com.motivbot.app.ui.theme.DSMotion
import com.motivbot.app.ui.theme.DSRadius
import com.motivbot.app.ui.theme.DSSize
import com.motivbot.app.ui.theme.DSSpacing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

// Port of DSResultComponents.swift.

// MARK: - Colours and icons

fun WellBeingDimension.Tone.color(colors: DSColors): Color = when (this) {
    WellBeingDimension.Tone.Steady -> colors.steady
    WellBeingDimension.Tone.Gentle -> colors.gentle
    WellBeingDimension.Tone.Care -> colors.care
}

val WellBeingDimension.icon: ImageVector
    get() = when (this) {
        WellBeingDimension.Anxiety -> Icons.Outlined.Umbrella
        WellBeingDimension.Depression -> Icons.Outlined.WbTwilight
        WellBeingDimension.SelfEsteem -> Icons.Outlined.HowToReg
        WellBeingDimension.PositiveAffect -> Icons.Outlined.AutoAwesome
        WellBeingDimension.NegativeAffect -> Icons.Outlined.Air
    }

val ReflectionTopic.icon: ImageVector
    get() = when (this) {
        ReflectionTopic.SelfEsteem -> Icons.Outlined.HowToReg
        ReflectionTopic.FearAndWorry -> Icons.Outlined.Umbrella
        ReflectionTopic.HappinessAndHope -> Icons.Outlined.WbSunny
        ReflectionTopic.Stress -> Icons.Outlined.Bolt
    }

val MessageCategoryStyle.icon: ImageVector
    get() = when (this) {
        MessageCategoryStyle.Validation -> Icons.Outlined.FavoriteBorder
        MessageCategoryStyle.Reframing -> Icons.Outlined.Autorenew
        MessageCategoryStyle.Action -> Icons.Outlined.Eco
        MessageCategoryStyle.Other -> Icons.Outlined.AutoAwesome
    }

fun MessageCategoryStyle.color(colors: DSColors): Color = when (this) {
    MessageCategoryStyle.Validation -> colors.peach
    MessageCategoryStyle.Reframing -> colors.sky
    MessageCategoryStyle.Action -> colors.mint
    MessageCategoryStyle.Other -> colors.brand
}

fun MessageCategoryStyle.softColor(colors: DSColors): Color = when (this) {
    MessageCategoryStyle.Validation -> colors.peachSoft
    MessageCategoryStyle.Reframing -> colors.skySoft
    MessageCategoryStyle.Action -> colors.mintSoft
    MessageCategoryStyle.Other -> colors.brandSoft
}

val Level.steps: Int get() = filledSteps

// MARK: - Message card

/** One message as a large slide: category pill, the quote, and the author when there is one. */
@Composable
fun MessageCard(
    style: MessageCategoryStyle,
    categoryTitle: String,
    text: String,
    author: String?,
    modifier: Modifier = Modifier,
) {
    val colors = DS.colors
    val tint = style.color(colors)
    val shape = RoundedCornerShape(DSRadius.slide)
    val attribution = author?.trim()?.takeIf { it.isNotEmpty() }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 380.dp)
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(style.softColor(colors), colors.surface)))
            .border(1.dp, tint.copy(alpha = 0.2f), shape)
            .semantics(mergeDescendants = true) {},
    ) {
        // Faint watermark of the category icon in the trailing bottom corner.
        Icon(
            style.icon,
            contentDescription = null,
            tint = tint.copy(alpha = 0.08f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 28.dp, y = 28.dp)
                .size(150.dp),
        )
        Column(Modifier.padding(DSSpacing.l).heightIn(min = 380.dp - DSSpacing.l * 2)) {
            CategoryPill(style, categoryTitle)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(DSSpacing.l))
            if (attribution != null) {
                Icon(
                    Icons.Filled.FormatQuote,
                    contentDescription = null,
                    tint = tint.copy(alpha = 0.55f),
                    modifier = Modifier.size(34.dp).graphicsLayer { scaleX = -1f },
                )
                Spacer(Modifier.height(DSSpacing.s))
            }
            Text(text, style = DSFont.quote, color = colors.textPrimary)
            if (attribution != null) {
                Spacer(Modifier.height(DSSpacing.s + DSSpacing.xxs))
                Text("— $attribution", style = DSFont.callout.copy(fontWeight = FontWeight.Medium), color = colors.textSecondary)
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(DSSpacing.xl))
        }
    }
}

@Composable
private fun CategoryPill(style: MessageCategoryStyle, title: String) {
    val colors = DS.colors
    val tint = style.color(colors)
    Row(
        Modifier
            .background(colors.surface, CircleShape)
            .border(1.dp, tint.copy(alpha = 0.2f), CircleShape)
            .padding(horizontal = DSSpacing.s, vertical = DSSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(style.icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(title, style = DSFont.label, color = tint)
    }
}

// MARK: - Message deck

class MessageDeckStrings(
    val hint: String,
    val next: String,
    val doneTitle: String,
    val doneBody: String,
    val replay: String,
    val categoryTitle: @Composable (MessageCategoryStyle) -> String,
)

/** The messages as a stack of slides: swipe either way or tap the arrow for the next one. */
@Composable
fun MessageDeck(messages: List<PredictionResponse.Message>, strings: MessageDeckStrings) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val reduceMotion = DS.reduceMotion
    val rtl = DS.isRtl
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val leaveAlpha = remember { Animatable(1f) }
    var isLeaving by remember { mutableStateOf(false) }
    val threshold = with(density) { 110.dp.toPx() }
    val flyDistance = with(density) { 600.dp.toPx() }

    LaunchedEffect(index) { if (index > 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    fun advance(direction: Float) {
        if (index >= messages.size || isLeaving) return
        isLeaving = true
        scope.launch {
            coroutineScope {
                launch { leaveAlpha.animateTo(0f, tween(if (reduceMotion) 200 else 250, easing = FastOutLinearInEasing)) }
                if (!reduceMotion) launch { offset.animateTo(direction * flyDistance, tween(250, easing = FastOutLinearInEasing)) }
            }
            index += 1
            offset.snapTo(0f)
            leaveAlpha.snapTo(1f)
            isLeaving = false
        }
    }

    // Next moves the card towards the leading edge, the way a page turns.
    fun showNext() = advance(if (rtl) 1f else -1f)

    AnimatedContent(
        targetState = index < messages.size,
        transitionSpec = { fadeIn(DSMotion.standard()) togetherWith fadeOut(DSMotion.standard()) },
        label = "deck",
    ) { showingCards ->
        if (!showingCards) {
            DoneCard(strings) { index = 0 }
            return@AnimatedContent
        }
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.m)) {
            Box(Modifier.fillMaxWidth().padding(bottom = DSSpacing.l)) {
                // Every slide gets the height of the tallest one, so the deck does not jump between
                // messages and the card behind always peeks out below the top one.
                messages.forEach { message ->
                    val style = MessageCategoryStyle.of(message.category)
                    MessageCard(
                        style, strings.categoryTitle(style), message.text, message.author,
                        Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics { },
                    )
                }
                val visible = messages.indices.filter { it >= index && it <= index + 2 }.reversed()
                visible.forEach { position ->
                    key(position) {
                        val depth = position - index
                        val isTop = depth == 0
                        val scale by animateFloatAsState(1f - depth * 0.05f, DSMotion.soft(), label = "scale")
                        val lift by animateDpAsState(DSSpacing.s * depth, DSMotion.soft(), label = "lift")
                        val dim by animateFloatAsState(1f - depth * 0.3f, DSMotion.soft(), label = "dim")
                        val message = messages[position]
                        val style = MessageCategoryStyle.of(message.category)
                        MessageCard(
                            style, strings.categoryTitle(style), message.text, message.author,
                            Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                    scaleX = scale
                                    scaleY = scale
                                    translationY = lift.toPx()
                                    if (isTop) {
                                        translationX = offset.value
                                        rotationZ = if (reduceMotion) 0f else offset.value / density.density / 20f
                                        alpha = leaveAlpha.value
                                    } else {
                                        alpha = dim
                                    }
                                }
                                .then(
                                    if (isTop) {
                                        Modifier
                                            .semantics {
                                                customActions = listOf(CustomAccessibilityAction(strings.next) { showNext(); true })
                                            }
                                            .pointerInput(index) {
                                                val tracker = VelocityTracker()
                                                detectHorizontalDragGestures(
                                                    onDragStart = { tracker.resetTracking() },
                                                    onDragEnd = {
                                                        val velocity = tracker.calculateVelocity().x
                                                        val current = offset.value
                                                        val distance = if (abs(current) > threshold) current else current + velocity * 0.2f
                                                        if (abs(distance) > threshold) {
                                                            advance(if (distance > 0) 1f else -1f)
                                                        } else {
                                                            scope.launch { offset.animateTo(0f, DSMotion.soft()) }
                                                        }
                                                    },
                                                    onDragCancel = { scope.launch { offset.animateTo(0f, DSMotion.soft()) } },
                                                ) { change, dragAmount ->
                                                    if (isLeaving) return@detectHorizontalDragGestures
                                                    change.consume()
                                                    tracker.addPosition(change.uptimeMillis, change.position)
                                                    scope.launch { offset.snapTo(offset.value + dragAmount) }
                                                }
                                            }
                                    } else {
                                        Modifier.clearAndSetSemantics { }
                                    },
                                ),
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = DSSpacing.xs), contentAlignment = Alignment.Center) {
                ProgressDots(count = messages.size, index = index)
                Box(Modifier.align(Alignment.CenterEnd)) {
                    NextButton(strings.next) { showNext() }
                }
            }
        }
    }
}

@Composable
private fun ProgressDots(count: Int, index: Int) {
    val colors = DS.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clearAndSetSemantics { }) {
        repeat(count) { position ->
            val width by animateDpAsState(if (position == index) 22.dp else 8.dp, DSMotion.soft(), label = "dot")
            val color by animateColorAsState(
                when {
                    position == index -> colors.brand
                    position < index -> colors.brand.copy(alpha = 0.35f)
                    else -> colors.stroke
                },
                DSMotion.soft(),
                label = "dotColor",
            )
            Box(Modifier.size(width = width, height = 8.dp).background(color, CircleShape))
        }
    }
}

@Composable
private fun NextButton(label: String, onClick: () -> Unit) {
    val colors = DS.colors
    Box(
        Modifier
            .size(64.dp)
            .shadow(6.dp, CircleShape)
            .background(colors.brand, CircleShape)
            .pressable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.onBrand, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun DoneCard(strings: MessageDeckStrings, onReplay: () -> Unit) {
    val colors = DS.colors
    Column(
        Modifier.dsCard(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DSSpacing.s),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.steady, modifier = Modifier.size(30.dp))
        Text(strings.doneTitle, style = DSFont.heading, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(strings.doneBody, style = DSFont.callout, color = colors.textSecondary, textAlign = TextAlign.Center)
        DSTertiaryButton(strings.replay, onClick = onReplay)
    }
}

// MARK: - Swipe tip

@Composable
fun SwipeTip(text: String) {
    val colors = DS.colors
    Row(
        Modifier.padding(DSSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(36.dp).background(colors.brandSoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Swipe, contentDescription = null, tint = colors.brand, modifier = Modifier.size(20.dp))
        }
        Text(text, style = DSFont.callout, color = colors.textPrimary, modifier = Modifier.widthIn(max = 240.dp))
    }
}

// MARK: - Reflection rating

/** A topic and its five-step scale from "much worse" to "much better". */
@Composable
fun ReflectionRatingRow(
    title: String,
    icon: ImageVector,
    ratingTitle: @Composable (ReflectionRating) -> String,
    selection: ReflectionRating?,
    onSelect: (ReflectionRating) -> Unit,
) {
    val colors = DS.colors
    val container = RoundedCornerShape(DSRadius.medium)
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        ) {
            Icon(icon, contentDescription = null, tint = colors.brand, modifier = Modifier.size(20.dp))
            Text(title, style = DSFont.label, color = colors.textPrimary)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceMuted, container)
                .border(1.dp, colors.stroke, container)
                .padding(DSSpacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(DSSpacing.xxs),
        ) {
            ReflectionRating.entries.forEach { rating ->
                val isSelected = selection == rating
                val fill by animateColorAsState(if (isSelected) colors.brand else Color.Transparent, DSMotion.quick(), label = "rating")
                Box(
                    Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = DSSize.minTap)
                        .background(fill, RoundedCornerShape(DSRadius.small))
                        .pressable(role = Role.RadioButton) { onSelect(rating) }
                        .semantics { selected = isSelected }
                        .padding(horizontal = 2.dp, vertical = DSSpacing.xxs),
                    contentAlignment = Alignment.Center,
                ) {
                    // Long single words (e.g. "Değişmedi") carry a soft hyphen in the strings so they break cleanly.
                    Text(
                        ratingTitle(rating),
                        style = DSFont.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 15.sp, hyphens = Hyphens.Auto),
                        color = if (isSelected) colors.onBrand else colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// MARK: - Banner

enum class BannerKind { Info, Notice }

@Composable
fun Banner(kind: BannerKind, text: String, modifier: Modifier = Modifier) {
    val colors = DS.colors
    val tint = if (kind == BannerKind.Info) colors.sky else colors.gentle
    val soft = if (kind == BannerKind.Info) colors.skySoft else colors.gentleSoft
    Row(
        modifier
            .fillMaxWidth()
            .background(soft, RoundedCornerShape(DSRadius.small))
            .padding(DSSpacing.s)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
    ) {
        Icon(
            if (kind == BannerKind.Info) Icons.Outlined.Info else Icons.Outlined.Lightbulb,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = DSFont.callout, color = colors.textPrimary, modifier = Modifier.weight(1f))
    }
}

/** A footnote line with a leading info icon (`Label` with `info.circle` on iOS). */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    val colors = DS.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Text(text, style = DSFont.footnote, color = colors.textSecondary, modifier = Modifier.weight(1f))
    }
}
