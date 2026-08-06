package com.scommit.domain.post.comment.service

import com.scommit.domain.notification.notification.service.NotificationService
import com.scommit.domain.post.comment.dto.CommentResponse
import com.scommit.domain.post.comment.entity.Comment
import com.scommit.domain.post.comment.repository.CommentRepository
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CommentService(
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository,
    private val notificationService: NotificationService,
) {
    // 댓글 작성
    @Transactional
    fun createComment(
        actor: User,
        postId: Long,
        body: String?,
    ): CommentResponse {
        val post =
            postRepository.findByIdOrNull(postId)?.takeIf { it.deletedAt == null }
                ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        val comment = Comment(post = post, user = actor, body = body)
        val response = CommentResponse(commentRepository.save(comment))

        if (actor.id != post.user.id) {
            notificationService.notifyComment(checkNotNull(post.user.id), actor.nickname, checkNotNull(post.id))
        }

        return response
    }

    // 특정 게시글 댓글 페이지 조회
    fun getComments(
        postId: Long,
        pageable: Pageable,
    ): Page<CommentResponse> {
        postRepository.findByIdOrNull(postId)?.takeIf { it.deletedAt == null }
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        return commentRepository
            .findAllByPostIdAndDeletedAtIsNull(postId, pageable)
            .map { CommentResponse(it) }
    }

    // 댓글 수정
    @Transactional
    fun updateComment(
        actor: User,
        id: Long,
        body: String?,
    ): CommentResponse {
        val comment =
            commentRepository.findByIdOrNull(id)?.takeIf { it.deletedAt == null }
                ?: throw BusinessException(ErrorCode.COMMENT_NOT_FOUND)

        if (comment.user.id != actor.id) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        comment.update(body)
        return CommentResponse(comment)
    }

    // 댓글 삭제
    @Transactional
    fun deleteComment(
        actor: User,
        id: Long,
    ) {
        val comment =
            commentRepository.findByIdOrNull(id)?.takeIf { it.deletedAt == null }
                ?: throw BusinessException(ErrorCode.COMMENT_NOT_FOUND)

        if (comment.user.id != actor.id) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        comment.softDelete()
    }
}
