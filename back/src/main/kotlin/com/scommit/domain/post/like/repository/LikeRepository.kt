package com.scommit.domain.post.like.repository

import com.scommit.domain.post.like.entity.Like
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

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
}
