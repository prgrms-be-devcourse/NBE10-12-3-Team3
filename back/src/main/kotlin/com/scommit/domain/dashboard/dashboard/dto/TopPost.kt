package com.scommit.domain.dashboard.dashboard.dto

import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import java.time.format.DateTimeFormatter

data class TopPost(
    val id: Long,
    val title: String,
    val accessLevel: String,
    val viewCount: Long,
    val likeCount: Long,
    val bookmarkCount: Long,
    val createdAt: String,
    val authorId: Long,
    val authorNickname: String,
) {
    companion object {
        fun from(post: Post): TopPost =
            TopPost(
                id = checkNotNull(post.id),
                title = checkNotNull(post.title),
                accessLevel = if (post.accessLevel == PostAccessLevel.FREE) "FREE" else "PAID",
                viewCount = post.viewCount,
                likeCount = post.likeCount,
                bookmarkCount = post.bookmarkCount,
                createdAt = checkNotNull(post.createdAt).format(DateTimeFormatter.ISO_LOCAL_DATE),
                authorId = checkNotNull(post.user.id),
                authorNickname = post.user.nickname,
            )
    }
}
