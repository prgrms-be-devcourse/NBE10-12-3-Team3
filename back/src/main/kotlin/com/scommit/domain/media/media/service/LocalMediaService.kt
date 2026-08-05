package com.scommit.domain.media.media.service

import com.scommit.domain.media.media.entity.Media
import com.scommit.domain.media.media.entity.MediaType
import com.scommit.domain.media.media.repository.MediaRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@Service
@Profile("!prod")
class LocalMediaService(
    private val mediaRepository: MediaRepository,
) : MediaService {
    @Value("\${file.path}")
    private lateinit var mediaPath: String

    // 이름 중복 방지용으로 미디어 이름 앞에 UUID를 붙이는 메서드
    private fun addUUID(originalFilename: String?): String =
        "${UUID.randomUUID().toString().replace("-", "")}_$originalFilename"

    // 미디어를 생성하는 메서드
    @Transactional
    override fun uploadMedia(
        file: MultipartFile?,
        category: String,
    ): Media {
        if (file == null || file.isEmpty) {
            throw BusinessException(ErrorCode.EMPTY_FILE)
        }

        val mediaUrl = "$category/${addUUID(file.originalFilename)}"
        val mediaType = getMediaType(file.contentType)

        uploadFile(file, mediaUrl)

        return saveMedia(mediaUrl, mediaType)
    }

    // 미디어를 삭제하는 메서드
    @Transactional
    override fun deleteMedia(mediaId: Long) {
        val media =
            mediaRepository.findByIdOrNull(mediaId)
                ?: throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)

        mediaRepository.delete(media)
        deleteFile(media.url)
    }

    // 미디어가 올바른 형식인지 검사하는 메서드
    private fun getMediaType(mediaType: String?): MediaType =
        when {
            mediaType == null -> throw BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE)
            mediaType.startsWith("image/") -> MediaType.IMAGE
            mediaType.startsWith("video/") -> MediaType.VIDEO
            else -> throw BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE)
        }

    // 미디어 파일을 삭제하는 메서드
    private fun deleteFile(mediaName: String?) {
        try {
            mediaName?.let { Files.deleteIfExists(Paths.get(mediaPath + it)) }
        } catch (e: IOException) {
            throw BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, e)
        }
    }

    // 미디어 파일을 업로드하는 메서드
    private fun uploadFile(
        file: MultipartFile,
        mediaName: String,
    ) {
        try {
            val path = Paths.get(mediaPath + mediaName)
            Files.createDirectories(path.parent)
            file.transferTo(path.toAbsolutePath())
        } catch (e: IOException) {
            throw BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, e)
        }
    }

    // 미디어를 db에 저장하는 메서드
    private fun saveMedia(
        mediaName: String,
        mediaType: MediaType,
    ): Media = mediaRepository.save(Media(url = mediaName, type = mediaType))
}
