package com.motivbot.app.domain

import java.util.Locale

/**
 * Port of `WellBeingViewModel.generateCSV()`. Same layout as the iOS export so both apps produce
 * files the analysis scripts read the same way: commas in text become ";" and newlines spaces.
 */
object CsvBuilder {

    fun build(
        sections: List<TestSection>,
        answers: List<UserAnswer>,
        prediction: PredictionResponse?,
        request: PredictionRequest?,
        elevatedRisk: Boolean,
        reflectionRatings: Map<ReflectionTopic, ReflectionRating>,
        string: (Int) -> String,
    ): String = buildString {
        append("Section,Question,Answer,Score\n")
        for (section in sections) {
            val sectionTitle = string(section.titleRes).clean()
            for (question in section.questions) {
                val answer = answers.firstOrNull { it.questionId == question.id } ?: continue
                val questionText = string(question.textRes).clean()
                val (answerText, scoreText) = when (val value = answer.answerValue) {
                    is AnswerValue.Option -> string(value.option.textRes).clean() to "${value.option.value}"
                    is AnswerValue.Scale -> "${value.value}" to "${value.value}"
                    is AnswerValue.Text -> value.text.replace("\n", " ").clean() to "N/A"
                }
                append("$sectionTitle,$questionText,$answerText,$scoreText\n")
            }
        }

        if (prediction == null || request == null) return@buildString

        append("\nModel input,Value\n")
        append("Age,${request.age}\n")
        append("Gender,${request.gender.rawValue}\n")
        append("Gender (0 = male; 0.5 = not specified; 1 = female),${request.gender.numericValue}\n")
        append("PHQ-9 (0-27),${request.phq9Score}\n")
        append("RSES (0-30),${request.rsesScore}\n")
        append("RSES sent to model (10-40),${request.pipelineRsesScore}\n")

        append("\nDimension,Score,Level\n")
        for (dimension in WellBeingDimension.entries) {
            val probability = prediction.probability(dimension) ?: continue
            val level = prediction.level(dimension) ?: continue
            append("${dimension.rawValue},${String.format(Locale.ROOT, "%.3f", probability)},${level.rawValue}\n")
        }
        append("elevated_risk,$elevatedRisk\n")

        append("\nMessage category,Relevance,Message\n")
        for (message in prediction.messages) {
            append("${message.category},${message.score},${message.text.replace("\n", " ").clean()}\n")
        }

        append("\nReflection after the messages,Rating (1 = much worse; 2 = a bit worse; 3 = same; 4 = a bit better; 5 = much better)\n")
        for (topic in ReflectionTopic.entries) {
            append("${topic.rawValue},${reflectionRatings[topic]?.rawValue?.toString() ?: ""}\n")
        }
    }

    private fun String.clean() = replace(",", ";")
}
