package com.motivbot.app.domain

/** Errors of the pipeline client. Port of `MotivBotAPIError`. */
sealed interface MotivBotApiError {
    /** No network at all. */
    data object Offline : MotivBotApiError
    data class Network(val kind: NetworkKind) : MotivBotApiError

    /** 502/503/504 while the Space boots. */
    data object SpaceStarting : MotivBotApiError
    data object RateLimited : MotivBotApiError
    data object QuotaExceeded : MotivBotApiError
    data class InvalidInput(val message: String) : MotivBotApiError

    /** The event stream closed before a result or an error. */
    data object StreamEnded : MotivBotApiError
    data object Decoding : MotivBotApiError
    data class Http(val code: Int) : MotivBotApiError
    data object DeadlineExceeded : MotivBotApiError

    enum class NetworkKind(val isTransient: Boolean) {
        TimedOut(true),
        ConnectionLost(true),
        CannotConnect(true),
        CannotFindHost(true),
        Unknown(false),
    }

    val isRetryable: Boolean
        get() = when (this) {
            is Network -> kind.isTransient
            SpaceStarting, RateLimited, StreamEnded -> true
            else -> false
        }

    val analysisError: AnalysisError
        get() = when (this) {
            Offline, is Network -> AnalysisError.Offline
            SpaceStarting, DeadlineExceeded -> AnalysisError.ServiceWakingUp
            RateLimited, QuotaExceeded -> AnalysisError.ServiceBusy
            is InvalidInput, StreamEnded, Decoding, is Http -> AnalysisError.Unexpected
        }
}

class MotivBotApiException(val error: MotivBotApiError) : Exception(error.toString())
