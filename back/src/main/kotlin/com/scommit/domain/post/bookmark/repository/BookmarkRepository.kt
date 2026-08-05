package com.scommit.domain.post.bookmark.repository

import com.scommit.domain.post.bookmark.entity.Bookmark
import com.scommit.domain.post.post.entity.PublishStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface BookmarkRepository : JpaRepository<Bookmark, Long> {
    fun existsByPostIdAndUserId(
        postId: Long,
        userId: Long,
    ): Boolean

    // 목록에 노출할 대상: 삭제되지 않았고, PUBLIC이거나 본인이 작성한 게시글(북마크 시점엔 PUBLIC이었다가
    // 작성자가 나중에 PRIVATE/DRAFT로 돌린 타인의 글은 상세(403-1)와 마찬가지로 목록에서도 숨긴다).
    // 북마크 행 자체는 지우지 않으므로 다시 PUBLIC이 되면 목록에 복귀한다.
    @EntityGraph(attributePaths = ["post", "post.user"])
    @Query(
        "SELECT b FROM Bookmark b WHERE b.user.id = :userId AND b.post.deletedAt IS NULL " +
            "AND (b.post.publishStatus = :publicStatus OR b.post.user.id = :userId)",
    )
    fun findByUserIdAndPostDeletedAtIsNull(
        @Param("userId") userId: Long,
        @Param("publicStatus") publicStatus: PublishStatus,
        pageable: Pageable,
    ): Page<Bookmark>

    fun findByPostIdAndUserId(
        postId: Long,
        userId: Long,
    ): Bookmark?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Bookmark b WHERE b.post.id = :postId AND b.user.id = :userId")
    fun deleteByPostIdAndUserId(
        @Param("postId") postId: Long,
        @Param("userId") userId: Long,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Bookmark b WHERE b.post.id = :postId")
    fun deleteAllByPostId(
        @Param("postId") postId: Long,
    ): Int

    @Query("SELECT b.post.id FROM Bookmark b WHERE b.post.id IN :postIds AND b.user.id = :userId")
    fun findPostIdsByPostIdInAndUserId(
        @Param("postIds") postIds: List<Long>,
        @Param("userId") userId: Long,
    ): List<Long>

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
