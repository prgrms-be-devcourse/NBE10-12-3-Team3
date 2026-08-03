package com.scommit.domain.post.postmedia.service

import com.scommit.domain.media.media.service.MediaService
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.post.postmedia.dto.PostMediaResponse
import com.scommit.domain.post.postmedia.entity.PostMedia
import com.scommit.domain.post.postmedia.entity.PostMediaType
import com.scommit.domain.post.postmedia.repository.PostMediaRepository
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
) {
    @Transactional
    fun uploadMedia(
        postId: Long,
        file: MultipartFile,
        type: PostMediaType,
    ): PostMediaResponse {
        val post = postRepository.findByIdOrNull(postId) ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)
        if (post.deletedAt != null) {
            throw BusinessException(ErrorCode.POST_NOT_FOUND)
        }

        if (type == PostMediaType.THUMBNAIL) {
            val existing = postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)
            if (existing != null) {
                val oldMediaId = existing.media.id
                val newMedia = mediaService.uploadMedia(file, "post")
                existing.media = newMedia
                mediaService.deleteMedia(oldMediaId)
                return PostMediaResponse(existing)
            }
        }

        val media = mediaService.uploadMedia(file, "post")
        val saved = postMediaRepository.save(PostMedia(post = post, media = media, type = type))
        return PostMediaResponse(saved)
    }

    @Transactional(readOnly = true)
    fun getMediaList(postId: Long): List<PostMediaResponse> {
        val post = postRepository.findByIdOrNull(postId) ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)
        if (post.deletedAt != null) {
            throw BusinessException(ErrorCode.POST_NOT_FOUND)
        }

        return postMediaRepository.findAllByPost(post).map { PostMediaResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getThumbnail(postId: Long): PostMediaResponse? {
        val post = postRepository.findByIdOrNull(postId) ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)
        if (post.deletedAt != null) {
            throw BusinessException(ErrorCode.POST_NOT_FOUND)
        }

        val postMedia = postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL) ?: return null
        return PostMediaResponse(postMedia)
    }

    @Transactional
    @Suppress("ThrowsCount") // 서로 다른 검증 실패(미디어 없음/포스트 불일치/삭제된 포스트)마다 별개의 에러코드가 필요해 분리
    fun deleteMedia(
        postId: Long,
        postMediaId: Long,
    ) {
        val postMedia =
            postMediaRepository.findByIdOrNull(postMediaId) ?: throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)

        if (postMedia.post.id != postId) {
            throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)
        }

        if (postMedia.post.deletedAt != null) {
            throw BusinessException(ErrorCode.POST_NOT_FOUND)
        }

        val mediaId = postMedia.media.id
        postMediaRepository.delete(postMedia)
        mediaService.deleteMedia(mediaId)
    }
}
