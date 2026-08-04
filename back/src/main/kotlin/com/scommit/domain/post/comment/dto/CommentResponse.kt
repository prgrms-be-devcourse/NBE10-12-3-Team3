package com.scommit.domain.post.comment.dto

import com.scommit.domain.post.comment.entity.Comment
import java.time.LocalDateTime

@JvmRecord
data class CommentResponse(
    val id: Long,
    val postId: Long,
    val userId: Long,
    val nickname: String,
    val body: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
) {
    constructor(comment: Comment) : this(
        checkNotNull(comment.id),
        checkNotNull(comment.post.id),
        checkNotNull(comment.user.id),
        comment.user.nickname,
        comment.body,
        comment.createdAt,
        comment.updatedAt,
    )
}
