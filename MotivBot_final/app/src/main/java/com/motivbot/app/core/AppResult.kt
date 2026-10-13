package com.motivbot.app.core

/**
 * A tiny result type for the repository boundary.
 *
 * Swift modelled failure with `throws` + a `LocalizedError` enum. Kotlin has exceptions too, but
 * `suspend fun … : AppResult<T>` makes the failure path part of the signature, which is what the
 * ViewModel actually wants: it must never let an API failure escape into an unhandled crash —
 * the Swift ViewModel already swallowed the error and substituted a fallback quote.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/** Port of `enum OpenAIError: LocalizedError`. */
sealed interface AppError {
    /** No network / DNS / TLS / timeout — Swift surfaced these as thrown `URLError`s. */
    data class Network(val cause: Throwable) : AppError

    /** Non-2xx response. Swift: `.apiError(statusCode:message:)`. */
    data class Api(val statusCode: Int, val body: String) : AppError

    /** 200 with an empty `choices` array. Swift: `.noContent`. */
    data object NoContent : AppError

    /** Decoding failures and anything else unforeseen. */
    data class Unknown(val cause: Throwable) : AppError
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> = apply {
    if (this is AppResult.Success) action(data)
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> = apply {
    if (this is AppResult.Failure) action(error)
}
