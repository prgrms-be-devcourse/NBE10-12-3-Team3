package com.scommit.domain.post.bookmark.repository

import com.scommit.domain.post.bookmark.entity.Bookmark
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

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
}
