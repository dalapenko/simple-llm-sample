package llmchat.agent

/**
 * Represents errors that can occur during chat operations.
 */
sealed class ChatError {
    /**
     * Error communicating with the LLM API.
     *
     * @property message Error description
     * @property cause The underlying exception
     */
    data class ApiError(val message: String, val cause: Throwable?) : ChatError()

    /**
     * Network-related error.
     *
     * @property message Error description
     */
    data class NetworkError(val message: String) : ChatError()

    /**
     * Configuration error.
     *
     * @property message Error description
     */
    data class ConfigurationError(val message: String) : ChatError()

    /**
     * Get a user-friendly error message.
     */
    fun toUserMessage(): String = when (this) {
        is ApiError -> "Error communicating with LLM: $message"
        is NetworkError -> "Network error: $message"
        is ConfigurationError -> "Configuration error: $message"
    }
}

/**
 * Result type for chat operations.
 */
typealias ChatResult<T> = Result<T>
