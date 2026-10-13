package com.motivbot.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Port of the nested `Codable` structs in `OpenAIService.swift`.
 *
 * Swift relied on property names matching the JSON exactly, which forced snake_case properties
 * (`max_tokens`) into otherwise camelCase code. `@SerialName` keeps the wire format identical
 * while letting Kotlin properties stay idiomatic.
 */

@Serializable
data class ChatRequestDto(
    val model: String,
    val messages: List<ChatMessageDto>,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Double,
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class ChatResponseDto(
    val choices: List<ChoiceDto> = emptyList(),
)

@Serializable
data class ChoiceDto(
    val message: ResponseMessageDto,
)

@Serializable
data class ResponseMessageDto(
    /**
     * Nullable on purpose: a response terminated by a content filter carries `"content": null`.
     * The Swift `String` would have thrown a decoding error there; here the caller can treat it
     * as [com.motivbot.app.core.AppError.NoContent].
     */
    val content: String? = null,
)
