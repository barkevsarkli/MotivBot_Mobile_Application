package com.motivbot.app.domain

import androidx.annotation.StringRes
import com.motivbot.app.R

/** The five heads of the XLM-R classifier, in model order. Port of `WellBeingDimension`. */
enum class WellBeingDimension(
    val rawValue: String,
    @StringRes val titleRes: Int,
    @StringRes private val noRes: Int,
    @StringRes private val lowRes: Int,
    @StringRes private val highRes: Int,
) {
    Anxiety(
        "Anxiety_Fear_Worry", R.string.dim_anxiety_title,
        R.string.dim_anxiety_no, R.string.dim_anxiety_low, R.string.dim_anxiety_high,
    ),
    Depression(
        "Depression_Hopelessness_Sadness", R.string.dim_mood_title,
        R.string.dim_mood_no, R.string.dim_mood_low, R.string.dim_mood_high,
    ),
    SelfEsteem(
        "Self_Esteem_Issue", R.string.dim_self_esteem_title,
        R.string.dim_self_esteem_no, R.string.dim_self_esteem_low, R.string.dim_self_esteem_high,
    ),
    PositiveAffect(
        "Positive_Affect", R.string.dim_positive_title,
        R.string.dim_positive_no, R.string.dim_positive_low, R.string.dim_positive_high,
    ),
    NegativeAffect(
        "Negative_Affect", R.string.dim_tension_title,
        R.string.dim_tension_no, R.string.dim_tension_low, R.string.dim_tension_high,
    ),
    ;

    /** Positive affect is a strength (High = good). The other four are concerns (High = more difficulty). */
    val isPositiveTrait: Boolean get() = this == PositiveAffect

    @StringRes
    fun messageRes(level: Level): Int = when (level) {
        Level.No -> noRes
        Level.Low -> lowRes
        Level.High -> highRes
    }

    /** How the level should feel to the user, independent of the dimension's direction. */
    enum class Tone { Steady, Gentle, Care }

    fun tone(level: Level): Tone = when {
        level == Level.Low -> Tone.Gentle
        isPositiveTrait == (level == Level.High) -> Tone.Steady
        else -> Tone.Care
    }
}

/** Pipeline probability buckets. */
enum class Level(val rawValue: String, val filledSteps: Int) {
    No("No", 1),
    Low("Low", 2),
    High("High", 3),
}

/** What the reflection asks about after the motivational messages. Each topic is rated on five steps. */
enum class ReflectionTopic(val rawValue: String, @StringRes val titleRes: Int) {
    SelfEsteem("self_esteem", R.string.reflection_topic_self_esteem),
    FearAndWorry("fear_and_worry", R.string.reflection_topic_fear_and_worry),
    HappinessAndHope("happiness_and_hope", R.string.reflection_topic_happiness_and_hope),
    Stress("stress", R.string.reflection_topic_stress),
}

/** From 1 (much worse) through 3 (the same) to 5 (much better). The number is what goes into the CSV. */
enum class ReflectionRating(val rawValue: Int, @StringRes val titleRes: Int) {
    MuchWorse(1, R.string.reflection_rating_1),
    SlightlyWorse(2, R.string.reflection_rating_2),
    Same(3, R.string.reflection_rating_3),
    SlightlyBetter(4, R.string.reflection_rating_4),
    MuchBetter(5, R.string.reflection_rating_5),
}

/** Message categories returned by the pipeline, in the order the deck shows them. */
enum class MessageCategoryStyle(@StringRes val titleRes: Int) {
    Validation(R.string.message_category_validation),
    Reframing(R.string.message_category_reframing),
    Action(R.string.message_category_action),
    Other(R.string.message_category_other),
    ;

    companion object {
        fun of(category: String): MessageCategoryStyle = when (category) {
            "Emotional Validation" -> Validation
            "Cognitive Reframing" -> Reframing
            "Action-Oriented Encouragement" -> Action
            else -> Other
        }

        /** Validation, reframing, action, then anything else; pipeline order within a category. */
        fun ordered(messages: List<PredictionResponse.Message>): List<PredictionResponse.Message> =
            messages.withIndex()
                .sortedWith(compareBy({ of(it.value.category).ordinal }, { it.index }))
                .map { it.value }
    }
}

/** Shown in place of a failed analysis. Port of `AnalysisError`. */
enum class AnalysisError(@StringRes val messageRes: Int) {
    Offline(R.string.error_server_unreachable),
    ServiceWakingUp(R.string.error_model_loading),
    ServiceBusy(R.string.error_service_busy),
    Unexpected(R.string.error_generic),
}
