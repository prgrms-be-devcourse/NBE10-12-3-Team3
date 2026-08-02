package com.scommit.domain.post.like.repository

import com.scommit.domain.post.like.entity.Like
import org.springframework.data.jpa.repository.JpaRepository

interface LikeRepository : JpaRepository<Like, Long> {
    fun existsByPostIdAndUserId(
        postId: Long,
        userId: Long,
    ): Boolean

    fun findByPostIdAndUserId(
        postId: Long,
        userId: Long,
    ): Like?
}
