package com.scommit.domain.series.seriesmedia.service

import com.scommit.domain.media.media.service.MediaService
import com.scommit.domain.series.series.repository.SeriesRepository
import com.scommit.domain.series.seriesmedia.dto.SeriesMediaResponse
import com.scommit.domain.series.seriesmedia.entity.SeriesMedia
import com.scommit.domain.series.seriesmedia.repository.SeriesMediaRepository
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import kotlin.jvm.optionals.getOrNull

@Service
class SeriesMediaService(
    private val mediaService: MediaService,
    private val seriesMediaRepository: SeriesMediaRepository,
    private val seriesRepository: SeriesRepository,
) {
    @Transactional
    fun uploadMedia(
        seriesId: Long,
        file: MultipartFile,
        actorId: Long,
        actorRole: UserRole?,
    ): SeriesMediaResponse {
        val series =
            seriesRepository.findByIdAndDeletedAtIsNull(seriesId).getOrNull()
                ?: throw BusinessException(ErrorCode.SERIES_NOT_FOUND)

        if (actorRole != UserRole.ADMIN && series.user.id != actorId) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        val existing = seriesMediaRepository.findBySeries(series)
        if (existing != null) {
            val oldMediaId = checkNotNull(existing.media.id)
            val newMedia = mediaService.uploadMedia(file, "series")
            existing.media = newMedia
            mediaService.deleteMedia(oldMediaId)
            return SeriesMediaResponse(existing)
        }

        val media = mediaService.uploadMedia(file, "series")
        val saved = seriesMediaRepository.save(SeriesMedia(series = series, media = media))
        return SeriesMediaResponse(saved)
    }

    @Transactional(readOnly = true)
    fun getMedia(seriesId: Long): SeriesMediaResponse? {
        val series =
            seriesRepository.findByIdAndDeletedAtIsNull(seriesId).getOrNull()
                ?: throw BusinessException(ErrorCode.SERIES_NOT_FOUND)

        val seriesMedia = seriesMediaRepository.findBySeries(series) ?: return null
        return SeriesMediaResponse(seriesMedia)
    }

    @Transactional
    @Suppress("ThrowsCount") // 서로 다른 검증 실패(시리즈 없음/권한 없음/미디어 없음)마다 별개의 에러코드가 필요해 분리
    fun deleteMedia(
        seriesId: Long,
        actorId: Long,
        actorRole: UserRole?,
    ) {
        val series =
            seriesRepository.findByIdAndDeletedAtIsNull(seriesId).getOrNull()
                ?: throw BusinessException(ErrorCode.SERIES_NOT_FOUND)

        if (actorRole != UserRole.ADMIN && series.user.id != actorId) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        val seriesMedia =
            seriesMediaRepository.findBySeries(series) ?: throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)

        val mediaId = checkNotNull(seriesMedia.media.id)
        seriesMediaRepository.delete(seriesMedia)
        mediaService.deleteMedia(mediaId)
    }
}
