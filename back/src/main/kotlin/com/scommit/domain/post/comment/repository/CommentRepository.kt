package com.scommit.domain.post.comment.repository

import com.scommit.domain.post.comment.entity.Comment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CommentRepository : JpaRepository<Comment, Long> {
    // 특정 게시글의 삭제되지 않은 댓글 페이지 조회 (GET /posts/{postId}/comments)
    fun findAllByPostIdAndDeletedAtIsNull(
        postId: Long,
        pageable: Pageable,
    ): Page<Comment>
}
