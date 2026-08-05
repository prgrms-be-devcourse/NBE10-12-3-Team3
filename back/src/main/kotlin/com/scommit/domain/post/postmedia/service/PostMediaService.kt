package com.scommit.domain.post.postmedia.service

import com.scommit.domain.media.media.service.MediaService
import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.post.postmedia.dto.PostMediaResponse
import com.scommit.domain.post.postmedia.entity.PostMedia
import com.scommit.domain.post.postmedia.entity.PostMediaType
import com.scommit.domain.post.postmedia.repository.PostMediaRepository
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class PostMediaService(
    private val mediaService: MediaService,
    private val postMediaRepository: PostMediaRepository,
    private val postRepository: PostRepository,
    private val subscriptionRepository: SubscriptionRepository,
) {
    @Transactional
    fun uploadMedia(
        postId: Long,
        file: MultipartFile,
        type: PostMediaType,
    ): PostMediaResponse {
        val post =
            postRepository.findByIdOrNull(postId)
                ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        if (post.deletedAt != null) {
            throw BusinessException(ErrorCode.POST_NOT_FOUND)
        }

        if (type == PostMediaType.THUMBNAIL) {
            val postMedia = postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)
            if (postMedia != null) {
                val oldMediaId = checkNotNull(postMedia.media.id)
                val newMedia = mediaService.uploadMedia(file, "post")
                postMedia.updateMedia(newMedia)
                mediaService.deleteMedia(oldMediaId)
                return PostMediaResponse(postMedia)
            }
        }

        val media = mediaService.uploadMedia(file, "post")

        return PostMediaResponse(postMediaRepository.save(PostMedia(post, media, type)))
    }

    @Transactional(readOnly = true)
    fun getMediaList(
        postId: Long,
        actor: User?,
    ): List<PostMediaResponse> {
        val post =
            postRepository.findByIdOrNull(postId)
                ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        if (post.deletedAt != null) {
            throw BusinessException(ErrorCode.POST_NOT_FOUND)
        }

        checkAccess(post, actor)

        return postMediaRepository.findAllByPost(post).map { PostMediaResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getThumbnail(
        postId: Long,
        actor: User?,
    ): PostMediaResponse? {
        val post =
            postRepository.findByIdOrNull(postId)
                ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        if (post.deletedAt != null) {
            throw BusinessException(ErrorCode.POST_NOT_FOUND)
        }

        checkAccess(post, actor)

        val postMedia = postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL) ?: return null

        return PostMediaResponse(postMedia)
    }

    // PostService.getPost()의 PRIVATE/PAID 접근 제어를 그대로 따르되, 미디어는 본문처럼
    // "블러 처리"할 부분 노출 개념이 없어 PAID도 PRIVATE과 동일하게 완전 차단한다.
    private fun checkAccess(
        post: Post,
        actor: User?,
    ) {
        val isOwner = actor != null && post.user.id == actor.id

        if (post.publishStatus == PublishStatus.PRIVATE && !isOwner) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        if (post.accessLevel == PostAccessLevel.PAID && !isOwner) {
            val isMember =
                actor != null &&
                    subscriptionRepository
                        .findByUserIdAndCreatorId(checkNotNull(actor.id), checkNotNull(post.user.id))
                        ?.tier == SubscriptionTier.MEMBERSHIP
            if (!isMember) {
                throw BusinessException(ErrorCode.ACCESS_DENIED)
            }
        }
    }

    @Suppress("ThrowsCount")
    @Transactional
    fun deleteMedia(
        postId: Long,
        postMediaId: Long,
    ) {
        val postMedia =
            postMediaRepository.findByIdOrNull(postMediaId)
                ?: throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)

        if (postMedia.post.id != postId) {
            throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)
        }

        if (postMedia.post.deletedAt != null) {
            throw BusinessException(ErrorCode.POST_NOT_FOUND)
        }

        val mediaId = checkNotNull(postMedia.media.id)
        postMediaRepository.delete(postMedia)
        mediaService.deleteMedia(mediaId)
    }
}
