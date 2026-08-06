package com.scommit.domain.post.postmedia.service

import com.scommit.domain.media.media.service.MediaService
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.post.post.service.PostAccessGuard
import com.scommit.domain.post.postmedia.dto.PostMediaResponse
import com.scommit.domain.post.postmedia.entity.PostMedia
import com.scommit.domain.post.postmedia.entity.PostMediaType
import com.scommit.domain.post.postmedia.repository.PostMediaRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
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
    private val postAccessGuard: PostAccessGuard,
) {
    @Suppress("ThrowsCount") // 서로 다른 검증 실패(포스트 없음/삭제됨/권한 없음)마다 별개의 에러코드가 필요해 분리
    @Transactional
    fun uploadMedia(
        postId: Long,
        file: MultipartFile,
        type: PostMediaType,
        actorId: Long,
        actorRole: UserRole?,
    ): PostMediaResponse {
        val post =
            postRepository.findByIdOrNull(postId)
                ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        if (post.deletedAt != null) {
            throw BusinessException(ErrorCode.POST_NOT_FOUND)
        }

        if (actorRole != UserRole.ADMIN && post.user.id != actorId) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
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

        postAccessGuard.enforceFullAccess(post, actor)

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

        postAccessGuard.enforceFullAccess(post, actor)

        val postMedia = postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL) ?: return null

        return PostMediaResponse(postMedia)
    }

    @Suppress("ThrowsCount")
    @Transactional
    fun deleteMedia(
        postId: Long,
        postMediaId: Long,
        actorId: Long,
        actorRole: UserRole?,
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

        if (actorRole != UserRole.ADMIN && postMedia.post.user.id != actorId) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        val mediaId = checkNotNull(postMedia.media.id)
        postMediaRepository.delete(postMedia)
        mediaService.deleteMedia(mediaId)
    }
}
