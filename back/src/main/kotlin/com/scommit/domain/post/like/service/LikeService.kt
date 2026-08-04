package com.scommit.domain.post.like.service

import com.scommit.domain.post.like.entity.Like
import com.scommit.domain.post.like.repository.LikeRepository
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeService(
    private val likeRepository: LikeRepository,
    private val postRepository: PostRepository,
) {
    @Transactional
    fun createLike(
        postId: Long,
        actor: User,
    ) {
        val post =
            postRepository.findByIdAndDeletedAtIsNull(postId)
                ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        val isOwner = post.user.id == actor.id
        if (post.publishStatus != PublishStatus.PUBLIC && !isOwner) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        try {
            likeRepository.save(Like(post, actor))
            postRepository.increaseLikeCount(postId)
        } catch (ignored: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.ALREADY_LIKED)
        }
    }

    @Transactional
    fun deleteLike(
        postId: Long,
        actor: User,
    ) {
        postRepository.findByIdAndDeletedAtIsNull(postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        val actorId = requireNotNull(actor.id)
        val deletedCount = likeRepository.deleteByPostIdAndUserId(postId, actorId)
        if (deletedCount == 0) {
            throw BusinessException(ErrorCode.LIKE_NOT_FOUND)
        }
        postRepository.decreaseLikeCount(postId)
    }
}
