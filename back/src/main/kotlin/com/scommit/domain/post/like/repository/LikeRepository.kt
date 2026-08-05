package com.scommit.domain.post.like.repository

import com.scommit.domain.post.like.entity.Like
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface LikeRepository : JpaRepository<Like, Long> {
    fun existsByPostIdAndUserId(
        postId: Long,
        userId: Long,
    ): Boolean

    fun findByPostIdAndUserId(
        postId: Long,
        userId: Long,
    ): Like?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Like l WHERE l.post.id = :postId AND l.user.id = :userId")
    fun deleteByPostIdAndUserId(
        @Param("postId") postId: Long,
        @Param("userId") userId: Long,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Like l WHERE l.post.id = :postId")
    fun deleteAllByPostId(
        @Param("postId") postId: Long,
    ): Int

    @Query("SELECT l.post.id FROM Like l WHERE l.post.id IN :postIds AND l.user.id = :userId")
    fun findPostIdsByPostIdInAndUserId(
        @Param("postIds") postIds: List<Long>,
        @Param("userId") userId: Long,
    ): List<Long>
  
    // 사용자 Radar 차트용 (최근 1년 고정)
    @Query(
        "SELECT COUNT(l) FROM Like l WHERE l.user.id = :userId AND l.createdAt >= :createdAt AND l.deletedAt IS NULL",
    )
    fun countByUserIdAndPeriod(
        @Param("userId") userId: Long,
        @Param("createdAt") createdAt: LocalDateTime,
    ): Long

    // 플랫폼 전체 좋아요 수 (플랫폼 평균 계산용)
    @Query("SELECT COUNT(l) FROM Like l WHERE l.createdAt >= :createdAt AND l.deletedAt IS NULL")
    fun countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(
        @Param("createdAt") createdAt: LocalDateTime,
    ): Long
}
