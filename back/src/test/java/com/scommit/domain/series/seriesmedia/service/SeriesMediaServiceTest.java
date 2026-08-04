package com.scommit.domain.series.seriesmedia.service;

import com.scommit.domain.media.media.entity.Media;
import com.scommit.domain.media.media.entity.MediaType;
import com.scommit.domain.media.media.service.MediaService;
import com.scommit.domain.series.series.entity.Series;
import com.scommit.domain.series.series.repository.SeriesRepository;
import com.scommit.domain.series.seriesmedia.dto.SeriesMediaResponse;
import com.scommit.domain.series.seriesmedia.entity.SeriesMedia;
import com.scommit.domain.series.seriesmedia.repository.SeriesMediaRepository;
import com.scommit.domain.user.user.entity.User;
import com.scommit.domain.user.user.entity.UserRole;
import com.scommit.global.exception.BusinessException;
import com.scommit.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeriesMediaServiceTest {

    private final MockMultipartFile file =
            new MockMultipartFile("file", "thumbnail.png", "image/png", "content".getBytes());
    @Mock
    private MediaService mediaService;
    @Mock
    private SeriesMediaRepository seriesMediaRepository;
    @Mock
    private SeriesRepository seriesRepository;
    @InjectMocks
    private SeriesMediaService seriesMediaService;

    @Nested
    @DisplayName("uploadMedia")
    class UploadMedia {

        @Test
        @DisplayName("성공: 썸네일 없는 시리즈의 첫 업로드")
        void uploadMedia_First_Success() {
            Long seriesId = 1L;
            Series series = mock(Series.class);
            User owner = mock(User.class);
            Media media = mock(Media.class);
            SeriesMedia seriesMedia = mock(SeriesMedia.class);

            given(seriesRepository.findByIdAndDeletedAtIsNull(seriesId)).willReturn(series);
            given(series.getUser()).willReturn(owner);
            given(owner.getId()).willReturn(1L);
            given(seriesMediaRepository.findBySeries(series)).willReturn(Optional.empty());
            given(mediaService.uploadMedia(file, "series")).willReturn(media);
            given(seriesMediaRepository.save(any(SeriesMedia.class))).willReturn(seriesMedia);
            given(seriesMedia.getSeries()).willReturn(series);
            given(seriesMedia.getMedia()).willReturn(media);
            given(media.getUrl()).willReturn("series/uuid_thumbnail.png");
            given(media.getType()).willReturn(MediaType.IMAGE);

            SeriesMediaResponse result = seriesMediaService.uploadMedia(seriesId, file, 1L, UserRole.USER);

            assertThat(result).isNotNull();
            verify(seriesMediaRepository, never()).delete(any());
            verify(mediaService, never()).deleteMedia(any());
        }

        @Test
        @DisplayName("성공: 기존 썸네일 있을 때 교체 시 기존 것이 삭제된다")
        void uploadMedia_Replace_Success() {
            Long seriesId = 1L;
            Series series = mock(Series.class);
            User owner = mock(User.class);

            Media existingMedia = mock(Media.class);
            given(existingMedia.getId()).willReturn(10L);
            given(existingMedia.getUrl()).willReturn("series/uuid_old.png");
            given(existingMedia.getType()).willReturn(MediaType.IMAGE);
            SeriesMedia existingSeriesMedia = mock(SeriesMedia.class);
            given(existingSeriesMedia.getMedia()).willReturn(existingMedia);
            given(existingSeriesMedia.getSeries()).willReturn(series);

            Media newMedia = mock(Media.class);

            given(seriesRepository.findByIdAndDeletedAtIsNull(seriesId)).willReturn(series);
            given(series.getUser()).willReturn(owner);
            given(owner.getId()).willReturn(1L);
            given(seriesMediaRepository.findBySeries(series)).willReturn(Optional.of(existingSeriesMedia));
            given(mediaService.uploadMedia(file, "series")).willReturn(newMedia);

            seriesMediaService.uploadMedia(seriesId, file, 1L, UserRole.USER);

            verify(existingSeriesMedia).updateMedia(newMedia);
            verify(mediaService).deleteMedia(10L);
            verify(seriesMediaRepository, never()).delete(any());
            verify(seriesMediaRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 seriesId로 업로드 시 예외를 던진다")
        void uploadMedia_SeriesNotFound_Fail() {
            given(seriesRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null);

            assertThatThrownBy(() -> seriesMediaService.uploadMedia(999L, file, 1L, UserRole.USER))
                    .isInstanceOf(BusinessException.class);

            verify(mediaService, never()).uploadMedia(any(), any());
        }

        @Test
        @DisplayName("실패: 소프트삭제된 시리즈에 업로드 시도 시 예외를 던진다")
        void uploadMedia_DeletedSeries_Fail() {
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(null);

            assertThatThrownBy(() -> seriesMediaService.uploadMedia(1L, file, 1L, UserRole.USER))
                    .isInstanceOf(BusinessException.class);

            verify(mediaService, never()).uploadMedia(any(), any());
        }

        @Test
        @DisplayName("실패: 타인의 시리즈에 썸네일 업로드 시도 시 ACCESS_DENIED 예외를 던진다")
        void uploadMedia_Forbidden_Fail() {
            Long seriesId = 1L;
            Series series = mock(Series.class);
            User owner = mock(User.class);

            given(seriesRepository.findByIdAndDeletedAtIsNull(seriesId)).willReturn(series);
            given(series.getUser()).willReturn(owner);
            given(owner.getId()).willReturn(1L);

            assertThatThrownBy(() -> seriesMediaService.uploadMedia(seriesId, file, 99L, UserRole.USER))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

            verify(mediaService, never()).uploadMedia(any(), any());
        }

        @Test
        @DisplayName("성공: 어드민은 타인 시리즈에도 썸네일을 업로드할 수 있다")
        void uploadMedia_AdminCanUploadOthers() {
            Long seriesId = 1L;
            Series series = mock(Series.class);
            Media media = mock(Media.class);
            SeriesMedia seriesMedia = mock(SeriesMedia.class);

            given(seriesRepository.findByIdAndDeletedAtIsNull(seriesId)).willReturn(series);
            given(seriesMediaRepository.findBySeries(series)).willReturn(Optional.empty());
            given(mediaService.uploadMedia(file, "series")).willReturn(media);
            given(seriesMediaRepository.save(any(SeriesMedia.class))).willReturn(seriesMedia);
            given(seriesMedia.getSeries()).willReturn(series);
            given(seriesMedia.getMedia()).willReturn(media);
            given(media.getUrl()).willReturn("series/uuid_thumbnail.png");
            given(media.getType()).willReturn(MediaType.IMAGE);

            SeriesMediaResponse result = seriesMediaService.uploadMedia(seriesId, file, 99L, UserRole.ADMIN);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("getMedia")
    class GetMedia {

        @Test
        @DisplayName("성공: 시리즈 썸네일을 조회한다")
        void getMedia_Success() {
            Long seriesId = 1L;
            Series series = mock(Series.class);
            Media media = mock(Media.class);
            SeriesMedia seriesMedia = mock(SeriesMedia.class);

            given(seriesRepository.findByIdAndDeletedAtIsNull(seriesId)).willReturn(series);
            given(seriesMediaRepository.findBySeries(series)).willReturn(Optional.of(seriesMedia));
            given(seriesMedia.getSeries()).willReturn(series);
            given(seriesMedia.getMedia()).willReturn(media);
            given(media.getUrl()).willReturn("series/uuid_thumbnail.png");
            given(media.getType()).willReturn(MediaType.IMAGE);

            SeriesMediaResponse result = seriesMediaService.getMedia(seriesId);

            assertThat(result).isNotNull();
            assertThat(result.url()).isEqualTo("series/uuid_thumbnail.png");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 seriesId로 조회 시 예외를 던진다")
        void getMedia_SeriesNotFound_Fail() {
            given(seriesRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null);

            assertThatThrownBy(() -> seriesMediaService.getMedia(999L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("성공: 썸네일 없는 시리즈 조회 시 null을 반환한다")
        void getMedia_NoMedia_Success() {
            Long seriesId = 1L;
            Series series = mock(Series.class);

            given(seriesRepository.findByIdAndDeletedAtIsNull(seriesId)).willReturn(series);
            given(seriesMediaRepository.findBySeries(series)).willReturn(Optional.empty());

            SeriesMediaResponse result = seriesMediaService.getMedia(seriesId);
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("deleteMedia")
    class DeleteMedia {

        @Test
        @DisplayName("성공: SeriesMedia와 Media가 삭제된다")
        void deleteMedia_Success() {
            Long seriesId = 1L;
            Series series = mock(Series.class);
            User owner = mock(User.class);

            Media media = mock(Media.class);
            given(media.getId()).willReturn(10L);
            SeriesMedia seriesMedia = mock(SeriesMedia.class);
            given(seriesMedia.getMedia()).willReturn(media);

            given(seriesRepository.findByIdAndDeletedAtIsNull(seriesId)).willReturn(series);
            given(series.getUser()).willReturn(owner);
            given(owner.getId()).willReturn(1L);
            given(seriesMediaRepository.findBySeries(series)).willReturn(Optional.of(seriesMedia));

            seriesMediaService.deleteMedia(seriesId, 1L, UserRole.USER);

            verify(seriesMediaRepository).delete(seriesMedia);
            verify(mediaService).deleteMedia(10L);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 seriesId로 삭제 시 예외를 던진다")
        void deleteMedia_SeriesNotFound_Fail() {
            given(seriesRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null);

            assertThatThrownBy(() -> seriesMediaService.deleteMedia(999L, 1L, UserRole.USER))
                    .isInstanceOf(BusinessException.class);

            verify(seriesMediaRepository, never()).delete(any());
        }

        @Test
        @DisplayName("실패: 타인의 시리즈 썸네일 삭제 시도 시 ACCESS_DENIED 예외를 던진다")
        void deleteMedia_Forbidden_Fail() {
            Long seriesId = 1L;
            Series series = mock(Series.class);
            User owner = mock(User.class);

            given(seriesRepository.findByIdAndDeletedAtIsNull(seriesId)).willReturn(series);
            given(series.getUser()).willReturn(owner);
            given(owner.getId()).willReturn(1L);

            assertThatThrownBy(() -> seriesMediaService.deleteMedia(seriesId, 99L, UserRole.USER))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

            verify(seriesMediaRepository, never()).delete(any());
        }

        @Test
        @DisplayName("실패: 썸네일 없는 시리즈 삭제 시도 시 예외를 던진다")
        void deleteMedia_NoMedia_Fail() {
            Long seriesId = 1L;
            Series series = mock(Series.class);
            User owner = mock(User.class);

            given(seriesRepository.findByIdAndDeletedAtIsNull(seriesId)).willReturn(series);
            given(series.getUser()).willReturn(owner);
            given(owner.getId()).willReturn(1L);
            given(seriesMediaRepository.findBySeries(series)).willReturn(Optional.empty());

            assertThatThrownBy(() -> seriesMediaService.deleteMedia(seriesId, 1L, UserRole.USER))
                    .isInstanceOf(BusinessException.class);

            verify(seriesMediaRepository, never()).delete(any());
        }

        @Test
        @DisplayName("실패: 소프트삭제된 시리즈 삭제 시도 시 예외를 던진다")
        void deleteMedia_DeletedSeries_Fail() {
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(null);

            assertThatThrownBy(() -> seriesMediaService.deleteMedia(1L, 1L, UserRole.USER))
                    .isInstanceOf(BusinessException.class);

            verify(seriesMediaRepository, never()).delete(any());
        }

        @Test
        @DisplayName("성공: 어드민은 타인 시리즈의 썸네일도 삭제할 수 있다")
        void deleteMedia_AdminCanDeleteOthers() {
            Long seriesId = 1L;
            Series series = mock(Series.class);
            Media media = mock(Media.class);
            SeriesMedia seriesMedia = mock(SeriesMedia.class);

            given(media.getId()).willReturn(10L);
            given(seriesMedia.getMedia()).willReturn(media);
            given(seriesRepository.findByIdAndDeletedAtIsNull(seriesId)).willReturn(series);
            given(seriesMediaRepository.findBySeries(series)).willReturn(Optional.of(seriesMedia));

            seriesMediaService.deleteMedia(seriesId, 99L, UserRole.ADMIN);

            verify(seriesMediaRepository).delete(seriesMedia);
            verify(mediaService).deleteMedia(10L);
        }
    }
}
