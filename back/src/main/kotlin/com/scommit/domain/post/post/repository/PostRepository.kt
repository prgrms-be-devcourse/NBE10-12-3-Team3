package com.scommit.domain.post.post.repository

import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.user.user.entity.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

// findByIdAndDeletedAtIsNull는 Like/Bookmark/Comment/Series 등 Post 바깥 도메인에서도 호출하는
// 공유 API라, 이번 마이그레이션 범위 밖의 파일들을 건드리지 않기 위해 일부러 Optional을 유지한다
// (다른 커스텀 finder는 Post 내부에서만 쓰여서 nullable로 바꿔도 리플이 없다).
@Suppress("TooManyFunctions")
interface PostRepository : JpaRepository<Post, Long> {
    // 삭제되지 않은 게시글 단건 조회
    fun findByIdAndDeletedAtIsNull(id: Long): Optional<Post>

    // 특정 유저 게시글 전체 조회 - 관리자용 (삭제된 게시글 포함)
    fun findByUser(user: User): List<Post>

    // 특정 유저의 삭제되지 않은 게시글 페이지 조회
    fun findByUserAndDeletedAtIsNull(
        user: User,
        pageable: Pageable,
    ): Page<Post>

    // 특정 유저의 삭제되지 않은 게시글 무한 스크롤 조회
    fun findSliceByUserAndDeletedAtIsNull(
        user: User,
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

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.bookmarkCount = p.bookmarkCount + 1 WHERE p.id = :postId")
    fun increaseBookmarkCount(
        @Param("postId") postId: Long,
    )

    @Modifying(clearAutomatically = true)
    @Query(
        "UPDATE Post p SET p.bookmarkCount = CASE WHEN p.bookmarkCount > 0 THEN p.bookmarkCount - 1 ELSE 0 END " +
            "WHERE p.id = :postId",
    )
    fun decreaseBookmarkCount(
        @Param("postId") postId: Long,
    )

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
    fun increaseLikeCount(
        @Param("postId") postId: Long,
    )

    @Modifying(clearAutomatically = true)
    @Query(
        "UPDATE Post p SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END " +
            "WHERE p.id = :postId",
    )
    fun decreaseLikeCount(
        @Param("postId") postId: Long,
    )
}
