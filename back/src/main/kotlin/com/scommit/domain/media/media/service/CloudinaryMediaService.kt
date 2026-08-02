package com.scommit.domain.media.media.service

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import com.scommit.domain.media.media.entity.Media
import com.scommit.domain.media.media.entity.MediaType
import com.scommit.domain.media.media.repository.MediaRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.IOException

// prod 프로파일에서만 활성화. dev/test는 LocalMediaService가 대신 사용됨
@Service
@Profile("prod")
class CloudinaryMediaService(
    private val cloudinary: Cloudinary,
    private val mediaRepository: MediaRepository,
) : MediaService {
    /**
     * 파일을 Cloudinary에 업로드하고 DB에 URL을 저장한다.
     * 업로드 방식: 서버 사이드 (브라우저 → 백엔드 → Cloudinary)
     * API Key/Secret이 서버에서만 사용되므로 클라이언트 사이드 업로드보다 보안상 안전하다.
     *
     * @param category Cloudinary 내 폴더명 (예: "series", "users")
     * @return DB에 저장된 Media 엔티티. url 필드에 Cloudinary의 절대 URL이 담겨있음
     */
    @Transactional
    @Suppress("SwallowedException") // BusinessException에 cause를 실어줄 생성자가 없어 원인 체이닝 불가
    override fun uploadMedia(
        file: MultipartFile?,
        category: String,
    ): Media {
        if (file == null || file.isEmpty) {
            throw BusinessException(ErrorCode.EMPTY_FILE)
        }

        val mediaType = resolveMediaType(file.contentType)

        val secureUrl =
            try {
                // resource_type "auto": Cloudinary가 이미지/동영상/파일을 자동 감지
                val uploadResult =
                    cloudinary.uploader().upload(
                        file.bytes,
                        ObjectUtils.asMap("folder", category, "resource_type", "auto"),
                    )
                // "url"(http)이 아닌 "secure_url"(https)을 저장 — 브라우저 혼합 콘텐츠 차단 방지
                uploadResult["secure_url"] as? String
            } catch (e: IOException) {
                throw BusinessException(ErrorCode.INTERNAL_SERVER_ERROR)
            }

        return mediaRepository.save(Media(url = secureUrl, type = mediaType))
    }

    /**
     * DB에서 Media 레코드를 삭제하고, Cloudinary에서도 원본 파일을 제거한다.
     * destroy() 호출 시 resource_type을 명시해야 함 — 생략하면 기본값 "image"로 처리되어
     * 동영상 파일 삭제 시 404 오류 발생.
     */
    @Transactional
    @Suppress("SwallowedException") // BusinessException에 cause를 실어줄 생성자가 없어 원인 체이닝 불가
    override fun deleteMedia(mediaId: Long) {
        val media =
            mediaRepository.findByIdOrNull(mediaId)
                ?: throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)
        mediaRepository.delete(media)

        try {
            val url = requireNotNull(media.url) { "media url must not be null" }
            // Cloudinary URL 경로에서 resource_type 판별
            // 이미지: .../image/upload/... | 동영상: .../video/upload/...
            val resourceType = if (url.contains("/video/upload/")) "video" else "image"
            cloudinary.uploader().destroy(extractPublicId(url), ObjectUtils.asMap("resource_type", resourceType))
        } catch (e: IOException) {
            throw BusinessException(ErrorCode.INTERNAL_SERVER_ERROR)
        }
    }

    // MIME 타입(multipart Content-Type)으로 MediaType 열거형 변환
    private fun resolveMediaType(contentType: String?): MediaType =
        when {
            contentType == null -> throw BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE)
            contentType.startsWith("image/") -> MediaType.IMAGE
            contentType.startsWith("video/") -> MediaType.VIDEO
            else -> throw BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE)
        }

    /**
     * Cloudinary secure_url에서 public_id를 추출한다.
     * destroy() API는 URL이 아닌 public_id를 파라미터로 받기 때문에 변환이 필요하다.
     *
     * URL 형식: https://res.cloudinary.com/{cloud}/{type}/upload/v{version}/{folder}/{name}.{ext}
     * 예시: .../upload/v1234567/series/abc.jpg → public_id: series/abc
     */
    private fun extractPublicId(secureUrl: String): String =
        secureUrl
            .split("/upload/")[1]
            .replaceFirst(Regex("v\\d+/"), "") // 버전 번호(v1234567/) 제거
            .let { afterUpload ->
                val lastDot = afterUpload.lastIndexOf('.')
                if (lastDot > 0) afterUpload.substring(0, lastDot) else afterUpload // 확장자 제거
            }
}
