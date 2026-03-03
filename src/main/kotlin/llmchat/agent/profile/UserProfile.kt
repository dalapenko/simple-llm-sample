package llmchat.agent.profile

/**
 * Immutable snapshot of the user profile loaded from disk.
 * [content] is the raw markdown text, validated as non-blank by [ProfileManager].
 */
data class UserProfile(val content: String)
