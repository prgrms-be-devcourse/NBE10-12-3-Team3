package com.scommit.domain.user.usermedia.service

import com.scommit.domain.media.media.service.MediaService
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.domain.user.usermedia.dto.UserMediaResponse
import com.scommit.domain.user.usermedia.entity.UserMedia
import com.scommit.domain.user.usermedia.repository.UserMediaRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class UserMediaService(
    private val mediaService: MediaService,
    private val userMediaRepository: UserMediaRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun uploadMedia(
        userId: Long,
        file: MultipartFile,
    ): UserMediaResponse {
        val user = userRepository.findByIdOrNull(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val existing = userMediaRepository.findByUser(user)
        if (existing != null) {
            val oldMediaId = checkNotNull(existing.media.id)
            val newMedia = mediaService.uploadMedia(file, "user")
            existing.media = newMedia
            mediaService.deleteMedia(oldMediaId)
            return UserMediaResponse(existing)
        }

        val media = mediaService.uploadMedia(file, "user")
        val saved = userMediaRepository.save(UserMedia(user = user, media = media))
        return UserMediaResponse(saved)
    }

    @Transactional(readOnly = true)
    fun getMedia(userId: Long): UserMediaResponse? {
        val user = userRepository.findByIdOrNull(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val userMedia = userMediaRepository.findByUser(user) ?: return null
        return UserMediaResponse(userMedia)
    }

    @Transactional
    fun deleteMedia(userId: Long) {
        val user = userRepository.findByIdOrNull(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val userMedia =
            userMediaRepository.findByUser(user) ?: throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)

        val mediaId = checkNotNull(userMedia.media.id)
        userMediaRepository.delete(userMedia)
        mediaService.deleteMedia(mediaId)
    }
}
