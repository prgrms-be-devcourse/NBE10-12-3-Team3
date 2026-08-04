package com.scommit.domain.post.post.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import java.time.LocalDateTime

// GET /posts 게시글 목록 조회 응답 DTO
// GET /posts?creatorId={id} 특정 유저 게시글 목록 조회에도 재사용
// body 제외 → 목록에서 본문까지 전송하면 데이터 낭비
// @JvmRecord: PostResponse와 같은 이유 — 원래 Java record였고 여러 도메인의 Java 호출부가
// xxx() record 접근자를 그대로 쓴다.
@JvmRecord
data class PostListResponse(
    val id: Long?,
    val userId: Long?,
    val nickname: String,
    val seriesId: Long?,
    val title: String?,
    val publishStatus: PublishStatus,
    val accessLevel: PostAccessLevel,
    val viewCount: Long,
    val likeCount: Long,
    val bookmarkCount: Long,
    @get:JsonProperty("isLiked") val isLiked: Boolean,
    @get:JsonProperty("isBookmarked") val isBookmarked: Boolean,
    val createdAt: LocalDateTime?,
) {
    constructor(post: Post, isLiked: Boolean, isBookmarked: Boolean) : this(
        post.id,
        post.user.id,
        post.user.nickname,
        post.series?.id,
        post.title,
        post.publishStatus,
        post.accessLevel,
        post.viewCount,
        post.likeCount,
        post.bookmarkCount,
        isLiked,
        isBookmarked,
        post.createdAt,
    )
}
