package com.scommit.domain.post.bookmark.repository

import com.scommit.domain.post.bookmark.entity.Bookmark
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface BookmarkRepository : JpaRepository<Bookmark, Long> {
    fun existsByPostIdAndUserId(
        postId: Long,
        userId: Long,
    ): Boolean

    @EntityGraph(attributePaths = ["post", "post.user"])
    fun findByUserIdAndPostDeletedAtIsNull(
        userId: Long,
        pageable: Pageable,
    ): Page<Bookmark>

    fun findByPostIdAndUserId(
        postId: Long,
        userId: Long,
    ): Bookmark?

    // 사용자 Radar 차트용 (최근 1년 고정)
    @Query(
        "SELECT COUNT(b) FROM Bookmark b WHERE b.user.id = :userId " +
            "AND b.createdAt >= :createdAt AND b.deletedAt IS NULL",
    )
    fun countByUserIdAndPeriod(
        @Param("userId") userId: Long,
        @Param("createdAt") createdAt: LocalDateTime,
    ): Long

    // 플랫폼 전체 북마크 수 (플랫폼 평균 계산용)
    @Query("SELECT COUNT(b) FROM Bookmark b WHERE b.createdAt >= :createdAt AND b.deletedAt IS NULL")
    fun countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(
        @Param("createdAt") createdAt: LocalDateTime,
    ): Long
}
