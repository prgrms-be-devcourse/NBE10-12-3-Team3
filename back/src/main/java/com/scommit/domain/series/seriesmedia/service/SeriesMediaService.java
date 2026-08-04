package com.scommit.domain.series.seriesmedia.service;

import com.scommit.domain.media.media.entity.Media;
import com.scommit.domain.media.media.service.MediaService;
import com.scommit.domain.series.series.entity.Series;
import com.scommit.domain.series.series.repository.SeriesRepository;
import com.scommit.domain.series.seriesmedia.dto.SeriesMediaResponse;
import com.scommit.domain.series.seriesmedia.entity.SeriesMedia;
import com.scommit.domain.series.seriesmedia.repository.SeriesMediaRepository;
import com.scommit.domain.user.user.entity.UserRole;
import com.scommit.global.exception.BusinessException;
import com.scommit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SeriesMediaService {

    private final MediaService mediaService;
    private final SeriesMediaRepository seriesMediaRepository;
    private final SeriesRepository seriesRepository;

    @Transactional
    public SeriesMediaResponse uploadMedia(Long seriesId, MultipartFile file, Long actorId, UserRole actorRole) {
        Series series = seriesRepository.findByIdAndDeletedAtIsNull(seriesId);
        if (series == null) {
            throw new BusinessException(ErrorCode.SERIES_NOT_FOUND);
        }

        if (actorRole != UserRole.ADMIN && !series.getUser().getId().equals(actorId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        SeriesMedia seriesMedia = seriesMediaRepository.findBySeries(series).orElse(null);
        if (seriesMedia != null) {
            Long oldMediaId = seriesMedia.getMedia().getId();
            Media newMedia = mediaService.uploadMedia(file, "series");
            seriesMedia.updateMedia(newMedia);
            mediaService.deleteMedia(oldMediaId);
            return new SeriesMediaResponse(seriesMedia);
        }

        Media media = mediaService.uploadMedia(file, "series");

        return new SeriesMediaResponse(seriesMediaRepository.save(SeriesMedia.builder()
                .series(series)
                .media(media)
                .build()));
    }

    @Transactional(readOnly = true)
    public SeriesMediaResponse getMedia(Long seriesId) {
        Series series = seriesRepository.findByIdAndDeletedAtIsNull(seriesId);
        if (series == null) {
            throw new BusinessException(ErrorCode.SERIES_NOT_FOUND);
        }

        SeriesMedia seriesMedia = seriesMediaRepository.findBySeries(series).orElse(null);
        if (seriesMedia == null) {
            return null;
        }

        return new SeriesMediaResponse(seriesMedia);
    }

    @Transactional
    public void deleteMedia(Long seriesId, Long actorId, UserRole actorRole) {
        Series series = seriesRepository.findByIdAndDeletedAtIsNull(seriesId);
        if (series == null) {
            throw new BusinessException(ErrorCode.SERIES_NOT_FOUND);
        }

        if (actorRole != UserRole.ADMIN && !series.getUser().getId().equals(actorId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        SeriesMedia seriesMedia = seriesMediaRepository.findBySeries(series)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_NOT_FOUND));

        Long mediaId = seriesMedia.getMedia().getId();
        seriesMediaRepository.delete(seriesMedia);
        mediaService.deleteMedia(mediaId);
    }
}
