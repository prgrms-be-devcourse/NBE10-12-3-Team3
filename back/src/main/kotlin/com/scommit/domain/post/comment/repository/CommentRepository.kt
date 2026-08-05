package com.scommit.domain.post.comment.repository

import com.scommit.domain.post.comment.entity.Comment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface CommentRepository : JpaRepository<Comment, Long> {
    // 특정 게시글의 삭제되지 않은 댓글 페이지 조회 (GET /posts/{postId}/comments)
    fun findAllByPostIdAndDeletedAtIsNull(
        postId: Long,
        pageable: Pageable,
    ): Page<Comment>

    // 사용자 Radar 차트용 (최근 1년 고정)
    @Query(
        "SELECT COUNT(c) FROM Comment c WHERE c.user.id = :userId " +
            "AND c.createdAt >= :createdAt AND c.deletedAt IS NULL",
    )
    fun countByUserIdAndPeriod(
        @Param("userId") userId: Long,
        @Param("createdAt") createdAt: LocalDateTime,
    ): Long

    // 플랫폼 전체 댓글 수 (플랫폼 평균 계산용)
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.createdAt >= :createdAt AND c.deletedAt IS NULL")
    fun countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(
        @Param("createdAt") createdAt: LocalDateTime,
    ): Long
}
