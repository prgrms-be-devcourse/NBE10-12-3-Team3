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
    @Suppress("ThrowsCount")
    @Transactional
    fun createLike(
        postId: Long,
        actor: User,
    ) {
        // post row를 먼저 잠가 post_likes 접근 순서를 고정한다 (데드락 방지, 클래스 상단 주석 참고)
        val post =
            postRepository.findByIdAndDeletedAtIsNullForUpdate(postId)
                ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        val isOwner = post.user.id == actor.id
        if (post.publishStatus != PublishStatus.PUBLIC && !isOwner) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        val actorId = requireNotNull(actor.id)
        if (likeRepository.existsByPostIdAndUserId(postId, actorId)) {
            throw BusinessException(ErrorCode.ALREADY_LIKED)
        }

        // post row를 위에서 이미 잠갔으므로 사실상 도달하지 않지만, 유니크 제약이 최후 방어선으로 남아있어 대비한다
        try {
            likeRepository.save(Like(post, actor))
        } catch (ignored: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.ALREADY_LIKED)
        }
        postRepository.increaseLikeCount(postId)
    }

    @Transactional
    fun deleteLike(
        postId: Long,
        actor: User,
    ) {
        postRepository.findByIdAndDeletedAtIsNullForUpdate(postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        val actorId = requireNotNull(actor.id)
        val deletedCount = likeRepository.deleteByPostIdAndUserId(postId, actorId)
        if (deletedCount == 0) {
            throw BusinessException(ErrorCode.LIKE_NOT_FOUND)
        }
        postRepository.decreaseLikeCount(postId)
    }
}
