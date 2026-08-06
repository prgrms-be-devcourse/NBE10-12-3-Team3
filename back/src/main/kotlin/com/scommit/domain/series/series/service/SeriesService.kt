package com.scommit.domain.series.series.service

import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.series.series.dto.SeriesListResponse
import com.scommit.domain.series.series.dto.SeriesResponse
import com.scommit.domain.series.series.entity.Series
import com.scommit.domain.series.series.repository.SeriesRepository
import com.scommit.domain.series.seriesmedia.service.SeriesMediaService
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SeriesService(
    private val seriesRepository: SeriesRepository,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val seriesMediaService: SeriesMediaService,
) {
    @Transactional
    fun createSeries(
        title: String,
        body: String?,
        userId: Long,
    ): SeriesResponse {
        val user =
            userRepository.findByIdOrNull(userId)
                ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val series =
            Series(
                user = user,
                title = title,
                body = body,
            )

        return SeriesResponse(seriesRepository.save(series))
    }

    @Transactional(readOnly = true)
    fun getSeriesSlice(pageable: Pageable): Slice<SeriesListResponse> = seriesRepository.findAllWithPostCount(pageable)

    @Transactional(readOnly = true)
    fun searchSeries(
        keyword: String,
        pageable: Pageable,
    ): Page<SeriesListResponse> {
        if (keyword.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_INPUT_VALUE)
        }
        return seriesRepository.findByTitleContainingWithPostCount(keyword, pageable)
    }

    @Transactional(readOnly = true)
    fun getSeriesList(
        creatorId: Long,
        pageable: Pageable,
    ): Page<SeriesListResponse> = seriesRepository.findByUserIdWithPostCount(creatorId, pageable)

    @Transactional(readOnly = true)
    fun getSeries(id: Long): SeriesResponse {
        val series =
            seriesRepository.findByIdAndDeletedAtIsNull(id)
                ?: throw BusinessException(ErrorCode.SERIES_NOT_FOUND)

        return SeriesResponse(series)
    }

    @Transactional
    fun updateSeries(
        id: Long,
        title: String,
        body: String?,
        actorId: Long,
        actorRole: UserRole?,
    ): SeriesResponse {
        val series =
            seriesRepository.findByIdAndDeletedAtIsNull(id)
                ?: throw BusinessException(ErrorCode.SERIES_NOT_FOUND)

        if (actorRole != UserRole.ADMIN && series.user.id != actorId) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        series.update(title, body)

        return SeriesResponse(series)
    }

    @Transactional
    fun deleteSeries(
        id: Long,
        actorId: Long,
        actorRole: UserRole?,
    ) {
        val series =
            seriesRepository.findByIdAndDeletedAtIsNull(id)
                ?: throw BusinessException(ErrorCode.SERIES_NOT_FOUND)

        if (actorRole != UserRole.ADMIN && series.user.id != actorId) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        postRepository
            .findBySeriesIdAndDeletedAtIsNull(id)
            .forEach { post -> post.updateSeries(null) }

        seriesMediaService.deleteMediaIfExists(series)
        series.softDelete()
    }
}
