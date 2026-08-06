package com.scommit.domain.post.post.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import java.time.LocalDateTime

// GET /posts/{id} 게시글 상세 조회 응답 DTO
// User, Series 객체 대신 id만 반환 → 순환참조 방지
// 엔티티 → DTO 변환 로직을 보조 생성자 안에 포함
// @JvmRecord: 원래 Java record였고, Java 쪽 호출부(E2E 테스트 포함, 다른 도메인 것도)가
// getXxx()가 아니라 xxx() record 접근자를 그대로 쓰고 있어서 이 어노테이션 없이 평범한
// Kotlin data class로만 바꾸면 전부 컴파일이 깨진다. JVM 타겟이 21이라 문제없이 지원된다.
@JvmRecord
data class PostResponse(
    val id: Long?,
    val userId: Long?,
    val nickname: String,
    val seriesId: Long?,
    val title: String?,
    val body: String?,
    val publishStatus: PublishStatus,
    val accessLevel: PostAccessLevel,
    val viewCount: Long,
    val likeCount: Long,
    val bookmarkCount: Long,
    @get:JsonProperty("isLocked") val isLocked: Boolean,
    @get:JsonProperty("isLiked") val isLiked: Boolean,
    @get:JsonProperty("isBookmarked") val isBookmarked: Boolean,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val thumbnailUrl: String? = null,
) {
    constructor(post: Post) : this(
        post.id,
        post.user.id,
        post.user.nickname,
        post.series?.id,
        post.title,
        post.body,
        post.publishStatus,
        post.accessLevel,
        post.viewCount,
        post.likeCount,
        post.bookmarkCount,
        false,
        false,
        false,
        post.createdAt,
        post.updatedAt,
        null,
    )

    constructor(post: Post, isLocked: Boolean, isLiked: Boolean, isBookmarked: Boolean) : this(
        post.id,
        post.user.id,
        post.user.nickname,
        post.series?.id,
        post.title,
        if (isLocked) null else post.body,
        post.publishStatus,
        post.accessLevel,
        post.viewCount,
        post.likeCount,
        post.bookmarkCount,
        isLocked,
        isLiked,
        isBookmarked,
        post.createdAt,
        post.updatedAt,
        null,
    )

    constructor(
        post: Post,
        isLocked: Boolean,
        isLiked: Boolean,
        isBookmarked: Boolean,
        thumbnailUrl: String?,
    ) : this(
        post.id,
        post.user.id,
        post.user.nickname,
        post.series?.id,
        post.title,
        if (isLocked) null else post.body,
        post.publishStatus,
        post.accessLevel,
        post.viewCount,
        post.likeCount,
        post.bookmarkCount,
        isLocked,
        isLiked,
        isBookmarked,
        post.createdAt,
        post.updatedAt,
        thumbnailUrl,
    )
}
