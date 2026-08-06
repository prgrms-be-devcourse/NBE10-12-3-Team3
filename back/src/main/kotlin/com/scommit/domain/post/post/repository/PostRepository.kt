package com.scommit.domain.post.post.repository

import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.user.user.entity.User
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

@Suppress("TooManyFunctions")
interface PostRepository : JpaRepository<Post, Long> {
    // 삭제되지 않은 게시글 단건 조회
    fun findByIdAndDeletedAtIsNull(id: Long): Post?

    // 좋아요 추가/취소 시 post row를 먼저 잠가 post <-> post_likes 간 lock 순서를 고정한다.
    // (INSERT/UPDATE가 FK·유니크 인덱스 체크로 두 테이블의 lock을 서로 다른 순서로 잡으면 데드락 발생 — 실제 운영 DB에서 확인됨)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :id AND p.deletedAt IS NULL")
    fun findByIdAndDeletedAtIsNullForUpdate(
        @Param("id") id: Long,
    ): Post?

    // 특정 유저 게시글 전체 조회 - 관리자용 (삭제된 게시글 포함)
    fun findByUser(user: User): List<Post>

    // 특정 유저의 삭제되지 않은 게시글 페이지 조회 (본인 조회용 — PRIVATE/DRAFT 포함)
    fun findByUserAndDeletedAtIsNull(
        user: User,
        pageable: Pageable,
    ): Page<Post>

    // 특정 유저의 삭제되지 않은 게시글 무한 스크롤 조회 (본인 조회용 — PRIVATE/DRAFT 포함)
    fun findSliceByUserAndDeletedAtIsNull(
        user: User,
        pageable: Pageable,
    ): Slice<Post>

    // 특정 유저의 게시글 페이지 조회 (제3자/비로그인용 — publishStatus로 좁힘, 프로필 화면)
    fun findByUserAndDeletedAtIsNullAndPublishStatus(
        user: User,
        publishStatus: PublishStatus,
        pageable: Pageable,
    ): Page<Post>

    // 특정 유저의 게시글 무한 스크롤 조회 (제3자/비로그인용 — publishStatus로 좁힘, creatorId 필터)
    fun findSliceByUserAndDeletedAtIsNullAndPublishStatus(
        user: User,
        publishStatus: PublishStatus,
        pageable: Pageable,
    ): Slice<Post>

    // 홈페이지 전체 조회 - 무한 스크롤 (PUBLIC만)
    fun findAllByDeletedAtIsNullAndPublishStatus(
        publishStatus: PublishStatus,
        pageable: Pageable,
    ): Slice<Post>

    // 시리즈의 게시글 조회
    fun findBySeriesIdAndDeletedAtIsNull(seriesId: Long): List<Post>

    // 키워드 검색 (제목·본문, PUBLIC만)
    @Query(
        "SELECT p FROM Post p WHERE p.deletedAt IS NULL AND p.publishStatus = :status " +
            "AND (p.title LIKE %:keyword% OR p.body LIKE %:keyword%)",
    )
    fun searchByKeyword(
        @Param("keyword") keyword: String,
        @Param("status") status: PublishStatus,
        pageable: Pageable,
    ): Page<Post>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.bookmarkCount = p.bookmarkCount + 1 WHERE p.id = :postId")
    fun increaseBookmarkCount(
        @Param("postId") postId: Long,
    )

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE Post p SET p.bookmarkCount = CASE WHEN p.bookmarkCount > 0 THEN p.bookmarkCount - 1 ELSE 0 END " +
            "WHERE p.id = :postId",
    )
    fun decreaseBookmarkCount(
        @Param("postId") postId: Long,
    )

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
    fun increaseLikeCount(
        @Param("postId") postId: Long,
    )

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE Post p SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END " +
            "WHERE p.id = :postId",
    )
    fun decreaseLikeCount(
        @Param("postId") postId: Long,
    )

    // 대시보드 통계용 쿼리
    fun countByUserIdAndDeletedAtIsNull(userId: Long): Long

    fun countByUserIdAndCreatedAtGreaterThanEqualAndDeletedAtIsNull(
        userId: Long,
        createdAt: LocalDateTime,
    ): Long

    fun countByUserIdAndAccessLevelAndDeletedAtIsNull(
        userId: Long,
        accessLevel: PostAccessLevel,
    ): Long

    @Query("SELECT SUM(p.viewCount) FROM Post p WHERE p.user.id = :userId AND p.deletedAt IS NULL")
    fun sumViewCountByUserId(
        @Param("userId") userId: Long,
    ): Long?

    @Query(
        "SELECT SUM(p.viewCount) FROM Post p WHERE p.user.id = :userId " +
            "AND p.createdAt >= :createdAt AND p.deletedAt IS NULL",
    )
    fun sumViewCountByUserIdAndPeriod(
        @Param("userId") userId: Long,
        @Param("createdAt") createdAt: LocalDateTime,
    ): Long?

    @Query("SELECT SUM(p.likeCount) FROM Post p WHERE p.user.id = :userId AND p.deletedAt IS NULL")
    fun sumLikeCountByUserId(
        @Param("userId") userId: Long,
    ): Long?

    @Query(
        "SELECT SUM(p.likeCount) FROM Post p WHERE p.user.id = :userId " +
            "AND p.createdAt >= :createdAt AND p.deletedAt IS NULL",
    )
    fun sumLikeCountByUserIdAndPeriod(
        @Param("userId") userId: Long,
        @Param("createdAt") createdAt: LocalDateTime,
    ): Long?

    @Query("SELECT SUM(p.bookmarkCount) FROM Post p WHERE p.user.id = :userId AND p.deletedAt IS NULL")
    fun sumBookmarkCountByUserId(
        @Param("userId") userId: Long,
    ): Long?

    @Query(
        "SELECT SUM(p.bookmarkCount) FROM Post p WHERE p.user.id = :userId " +
            "AND p.createdAt >= :createdAt AND p.deletedAt IS NULL",
    )
    fun sumBookmarkCountByUserIdAndPeriod(
        @Param("userId") userId: Long,
        @Param("createdAt") createdAt: LocalDateTime,
    ): Long?

    @Query(
        "SELECT p FROM Post p WHERE p.user.id = :userId AND p.createdAt >= :createdAt " +
            "AND p.deletedAt IS NULL ORDER BY p.viewCount DESC",
    )
    fun findTop5ByUserIdAndPeriod(
        @Param("userId") userId: Long,
        @Param("createdAt") createdAt: LocalDateTime,
        pageable: Pageable,
    ): List<Post>

    @Query("SELECT p FROM Post p WHERE p.user.id = :userId AND p.createdAt >= :createdAt AND p.deletedAt IS NULL")
    fun findActivityHeatmapByUserId(
        @Param("userId") userId: Long,
        @Param("createdAt") createdAt: LocalDateTime,
    ): List<Post>

    @Query("SELECT p FROM Post p WHERE p.createdAt >= :createdAt AND p.deletedAt IS NULL ORDER BY p.viewCount DESC")
    fun findTop5PostsByPeriod(
        @Param("createdAt") createdAt: LocalDateTime,
        pageable: Pageable,
    ): List<Post>

    // 전체 통계 (userId=null 대신 사용)
    fun countByDeletedAtIsNull(): Long

    fun countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(createdAt: LocalDateTime): Long

    fun countByAccessLevelAndDeletedAtIsNull(accessLevel: PostAccessLevel): Long

    @Query("SELECT SUM(p.viewCount) FROM Post p WHERE p.deletedAt IS NULL")
    fun sumAllViewCount(): Long?

    @Query("SELECT SUM(p.viewCount) FROM Post p WHERE p.createdAt >= :createdAt AND p.deletedAt IS NULL")
    fun sumAllViewCountByPeriod(
        @Param("createdAt") createdAt: LocalDateTime,
    ): Long?

    @Query(
        "SELECT p FROM Post p WHERE p.accessLevel = :accessLevel AND p.createdAt >= :createdAt " +
            "AND p.deletedAt IS NULL ORDER BY p.viewCount DESC",
    )
    fun findTop5PostsByAccessLevelAndPeriod(
        @Param("createdAt") createdAt: LocalDateTime,
        @Param("accessLevel") accessLevel: PostAccessLevel,
        pageable: Pageable,
    ): List<Post>
}
