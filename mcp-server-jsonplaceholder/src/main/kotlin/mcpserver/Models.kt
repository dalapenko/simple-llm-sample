package mcpserver

import kotlinx.serialization.Serializable

/**
 * A post from JSONPlaceholder /posts endpoint.
 */
@Serializable
data class Post(
    val userId: Int,
    val id: Int,
    val title: String,
    val body: String
)

/**
 * A comment from JSONPlaceholder /posts/{id}/comments endpoint.
 */
@Serializable
data class Comment(
    val postId: Int,
    val id: Int,
    val name: String,
    val email: String,
    val body: String
)
