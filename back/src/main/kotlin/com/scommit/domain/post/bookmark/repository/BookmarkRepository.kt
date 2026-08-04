package com.scommit.domain.post.bookmark.repository

import com.scommit.domain.post.bookmark.entity.Bookmark
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

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
}
