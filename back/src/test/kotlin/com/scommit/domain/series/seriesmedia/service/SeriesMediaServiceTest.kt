package com.scommit.domain.series.seriesmedia.service

import com.scommit.domain.media.media.entity.Media
import com.scommit.domain.media.media.entity.MediaType
import com.scommit.domain.media.media.service.MediaService
import com.scommit.domain.series.series.entity.Series
import com.scommit.domain.series.series.repository.SeriesRepository
import com.scommit.domain.series.seriesmedia.entity.SeriesMedia
import com.scommit.domain.series.seriesmedia.repository.SeriesMediaRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile

// Mockito ArgumentMatchers.any()는 null을 반환하는데, Kotlin에서 선언된 non-null 파라미터에
// 직접 전달하면 플랫폼 타입 널 체크가 삽입되어 NPE가 발생한다. mockito-kotlin 미사용 환경의 표준 우회책.
@Suppress("UNCHECKED_CAST", "IgnoredReturnValue") // 매처 등록이 목적이므로 반환값은 의도적으로 버린다
private fun <T> any(): T {
    Mockito.any<T>()
    return null as T
}

@ExtendWith(MockitoExtension::class)
class SeriesMediaServiceTest {
    private val file = MockMultipartFile("file", "thumbnail.png", "image/png", "content".toByteArray())

    @Mock
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var mediaService: MediaService

    @Mock
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var seriesMediaRepository: SeriesMediaRepository

    @Mock
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var seriesRepository: SeriesRepository

    @InjectMocks
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var seriesMediaService: SeriesMediaService

    @Nested
    @DisplayName("uploadMedia")
    inner class UploadMedia {
        @Test
        @DisplayName("성공: 썸네일 없는 시리즈의 첫 업로드")
        fun uploadMedia_First_Success() {
            val series = mock(Series::class.java)
            val owner = mock(User::class.java)
            val media = mock(Media::class.java)
            val seriesMedia = mock(SeriesMedia::class.java)

            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(series)
            given(series.user).willReturn(owner)
            given(owner.id).willReturn(1L)
            given(seriesMediaRepository.findBySeries(series)).willReturn(null)
            given(mediaService.uploadMedia(file, "series")).willReturn(media)
            given(seriesMediaRepository.save(any<SeriesMedia>())).willReturn(seriesMedia)
            given(seriesMedia.series).willReturn(series)
            given(seriesMedia.media).willReturn(media)
            given(media.url).willReturn("series/uuid_thumbnail.png")
            given(media.type).willReturn(MediaType.IMAGE)

            val result = seriesMediaService.uploadMedia(1L, file, 1L, UserRole.USER)

            assertThat(result).isNotNull()
            verify(seriesMediaRepository, never()).delete(any<SeriesMedia>())
            verify(mediaService, never()).deleteMedia(anyLong())
        }

        @Test
        @DisplayName("성공: 기존 썸네일 있을 때 교체 시 기존 것이 삭제된다")
        fun uploadMedia_Replace_Success() {
            val series = mock(Series::class.java)
            val owner = mock(User::class.java)

            val existingMedia =
                mock(Media::class.java).apply {
                    given(id).willReturn(10L)
                    given(url).willReturn("series/uuid_old.png")
                    given(type).willReturn(MediaType.IMAGE)
                }
            val existingSeriesMedia = mock(SeriesMedia::class.java)
            given(existingSeriesMedia.media).willReturn(existingMedia)
            given(existingSeriesMedia.series).willReturn(series)

            val newMedia = mock(Media::class.java)

            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(series)
            given(series.user).willReturn(owner)
            given(owner.id).willReturn(1L)
            given(seriesMediaRepository.findBySeries(series)).willReturn(existingSeriesMedia)
            given(mediaService.uploadMedia(file, "series")).willReturn(newMedia)

            seriesMediaService.uploadMedia(1L, file, 1L, UserRole.USER)

            verify(existingSeriesMedia).media = newMedia
            verify(mediaService).deleteMedia(10L)
            verify(seriesMediaRepository, never()).delete(any<SeriesMedia>())
            verify(seriesMediaRepository, never()).save(any<SeriesMedia>())
        }

        @Test
        @DisplayName("실패: 존재하지 않는 seriesId로 업로드 시 예외를 던진다")
        fun uploadMedia_SeriesNotFound_Fail() {
            given(seriesRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null)

            assertThatThrownBy { seriesMediaService.uploadMedia(999L, file, 1L, UserRole.USER) }
                .isInstanceOf(BusinessException::class.java)

            verify(mediaService, never()).uploadMedia(any<MultipartFile?>(), any<String>())
        }

        @Test
        @DisplayName("실패: 소프트삭제된 시리즈에 업로드 시도 시 예외를 던진다")
        fun uploadMedia_DeletedSeries_Fail() {
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(null)

            assertThatThrownBy { seriesMediaService.uploadMedia(1L, file, 1L, UserRole.USER) }
                .isInstanceOf(BusinessException::class.java)

            verify(mediaService, never()).uploadMedia(any<MultipartFile?>(), any<String>())
        }

        @Test
        @DisplayName("실패: 타인의 시리즈에 썸네일 업로드 시도 시 ACCESS_DENIED 예외를 던진다")
        fun uploadMedia_Forbidden_Fail() {
            val series = mock(Series::class.java)
            val owner = mock(User::class.java)

            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(series)
            given(series.user).willReturn(owner)
            given(owner.id).willReturn(1L)

            assertThatThrownBy { seriesMediaService.uploadMedia(1L, file, 99L, UserRole.USER) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)

            verify(mediaService, never()).uploadMedia(any<MultipartFile?>(), any<String>())
        }

        @Test
        @DisplayName("실패: 소유자 정보가 유실된 시리즈(user.id가 null)는 업로드 시 ACCESS_DENIED 예외를 던진다")
        fun uploadMedia_OwnerIdNull_Forbidden() {
            val series = mock(Series::class.java)
            val owner = mock(User::class.java)

            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(series)
            given(series.user).willReturn(owner)
            given(owner.id).willReturn(null)

            assertThatThrownBy { seriesMediaService.uploadMedia(1L, file, 1L, UserRole.USER) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)

            verify(mediaService, never()).uploadMedia(any<MultipartFile?>(), any<String>())
        }

        @Test
        @DisplayName("실패: 기존 썸네일의 media.id가 null이면 교체 시 예외를 던진다")
        fun uploadMedia_ExistingMediaIdNull_Fail() {
            val series = mock(Series::class.java)
            val owner = mock(User::class.java)

            val existingMedia = mock(Media::class.java)
            given(existingMedia.id).willReturn(null)
            val existingSeriesMedia = mock(SeriesMedia::class.java)
            given(existingSeriesMedia.media).willReturn(existingMedia)

            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(series)
            given(series.user).willReturn(owner)
            given(owner.id).willReturn(1L)
            given(seriesMediaRepository.findBySeries(series)).willReturn(existingSeriesMedia)

            assertThatThrownBy { seriesMediaService.uploadMedia(1L, file, 1L, UserRole.USER) }
                .isInstanceOf(IllegalStateException::class.java)

            verify(mediaService, never()).uploadMedia(any<MultipartFile?>(), any<String>())
        }

        @Test
        @DisplayName("성공: 어드민은 타인 시리즈에도 썸네일을 업로드할 수 있다")
        fun uploadMedia_AdminCanUploadOthers() {
            val series = mock(Series::class.java)
            val media = mock(Media::class.java)
            val seriesMedia = mock(SeriesMedia::class.java)

            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(series)
            given(seriesMediaRepository.findBySeries(series)).willReturn(null)
            given(mediaService.uploadMedia(file, "series")).willReturn(media)
            given(seriesMediaRepository.save(any<SeriesMedia>())).willReturn(seriesMedia)
            given(seriesMedia.series).willReturn(series)
            given(seriesMedia.media).willReturn(media)
            given(media.url).willReturn("series/uuid_thumbnail.png")
            given(media.type).willReturn(MediaType.IMAGE)

            val result = seriesMediaService.uploadMedia(1L, file, 99L, UserRole.ADMIN)

            assertThat(result).isNotNull()
        }
    }

    @Nested
    @DisplayName("getMedia")
    inner class GetMedia {
        @Test
        @DisplayName("성공: 시리즈 썸네일을 조회한다")
        fun getMedia_Success() {
            val series = mock(Series::class.java)
            val media = mock(Media::class.java)
            val seriesMedia = mock(SeriesMedia::class.java)

            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(series)
            given(seriesMediaRepository.findBySeries(series)).willReturn(seriesMedia)
            given(seriesMedia.series).willReturn(series)
            given(seriesMedia.media).willReturn(media)
            given(media.url).willReturn("series/uuid_thumbnail.png")
            given(media.type).willReturn(MediaType.IMAGE)

            val result = seriesMediaService.getMedia(1L)

            assertThat(result).isNotNull()
            assertThat(result?.url).isEqualTo("series/uuid_thumbnail.png")
        }

        @Test
        @DisplayName("실패: 존재하지 않는 seriesId로 조회 시 예외를 던진다")
        fun getMedia_SeriesNotFound_Fail() {
            given(seriesRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null)

            assertThatThrownBy { seriesMediaService.getMedia(999L) }
                .isInstanceOf(BusinessException::class.java)
        }

        @Test
        @DisplayName("실패: 썸네일 없는 시리즈 조회 시 MEDIA_NOT_FOUND 예외를 던진다")
        fun getMedia_NoMedia_Fail() {
            val series = mock(Series::class.java)

            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(series)
            given(seriesMediaRepository.findBySeries(series)).willReturn(null)

            assertThatThrownBy { seriesMediaService.getMedia(1L) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("deleteMedia")
    inner class DeleteMedia {
        @Test
        @DisplayName("성공: SeriesMedia와 Media가 삭제된다")
        fun deleteMedia_Success() {
            val series = mock(Series::class.java)
            val owner = mock(User::class.java)

            val media = mock(Media::class.java)
            given(media.id).willReturn(10L)
            val seriesMedia = mock(SeriesMedia::class.java)
            given(seriesMedia.media).willReturn(media)

            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(series)
            given(series.user).willReturn(owner)
            given(owner.id).willReturn(1L)
            given(seriesMediaRepository.findBySeries(series)).willReturn(seriesMedia)

            seriesMediaService.deleteMedia(1L, 1L, UserRole.USER)

            verify(seriesMediaRepository).delete(seriesMedia)
            verify(mediaService).deleteMedia(10L)
        }

        @Test
        @DisplayName("실패: 존재하지 않는 seriesId로 삭제 시 예외를 던진다")
        fun deleteMedia_SeriesNotFound_Fail() {
            given(seriesRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null)

            assertThatThrownBy { seriesMediaService.deleteMedia(999L, 1L, UserRole.USER) }
                .isInstanceOf(BusinessException::class.java)

            verify(seriesMediaRepository, never()).delete(any<SeriesMedia>())
        }

        @Test
        @DisplayName("실패: 타인의 시리즈 썸네일 삭제 시도 시 ACCESS_DENIED 예외를 던진다")
        fun deleteMedia_Forbidden_Fail() {
            val series = mock(Series::class.java)
            val owner = mock(User::class.java)

            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(series)
            given(series.user).willReturn(owner)
            given(owner.id).willReturn(1L)

            assertThatThrownBy { seriesMediaService.deleteMedia(1L, 99L, UserRole.USER) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)

            verify(seriesMediaRepository, never()).delete(any<SeriesMedia>())
        }

        @Test
        @DisplayName("실패: 썸네일 없는 시리즈 삭제 시도 시 예외를 던진다")
        fun deleteMedia_NoMedia_Fail() {
            val series = mock(Series::class.java)
            val owner = mock(User::class.java)

            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(series)
            given(series.user).willReturn(owner)
            given(owner.id).willReturn(1L)
            given(seriesMediaRepository.findBySeries(series)).willReturn(null)

            assertThatThrownBy { seriesMediaService.deleteMedia(1L, 1L, UserRole.USER) }
                .isInstanceOf(BusinessException::class.java)

            verify(seriesMediaRepository, never()).delete(any<SeriesMedia>())
        }

        @Test
        @DisplayName("실패: 소프트삭제된 시리즈 삭제 시도 시 예외를 던진다")
        fun deleteMedia_DeletedSeries_Fail() {
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(null)

            assertThatThrownBy { seriesMediaService.deleteMedia(1L, 1L, UserRole.USER) }
                .isInstanceOf(BusinessException::class.java)

            verify(seriesMediaRepository, never()).delete(any<SeriesMedia>())
        }

        @Test
        @DisplayName("실패: 소유자 정보가 유실된 시리즈(user.id가 null)는 삭제 시 ACCESS_DENIED 예외를 던진다")
        fun deleteMedia_OwnerIdNull_Forbidden() {
            val series = mock(Series::class.java)
            val owner = mock(User::class.java)

            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(series)
            given(series.user).willReturn(owner)
            given(owner.id).willReturn(null)

            assertThatThrownBy { seriesMediaService.deleteMedia(1L, 1L, UserRole.USER) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)

            verify(seriesMediaRepository, never()).delete(any<SeriesMedia>())
        }

        @Test
        @DisplayName("실패: 썸네일의 media.id가 null이면 삭제 시 예외를 던진다")
        fun deleteMedia_MediaIdNull_Fail() {
            val series = mock(Series::class.java)
            val owner = mock(User::class.java)

            val media = mock(Media::class.java)
            given(media.id).willReturn(null)
            val seriesMedia = mock(SeriesMedia::class.java)
            given(seriesMedia.media).willReturn(media)

            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(series)
            given(series.user).willReturn(owner)
            given(owner.id).willReturn(1L)
            given(seriesMediaRepository.findBySeries(series)).willReturn(seriesMedia)

            assertThatThrownBy { seriesMediaService.deleteMedia(1L, 1L, UserRole.USER) }
                .isInstanceOf(IllegalStateException::class.java)

            verify(seriesMediaRepository, never()).delete(any<SeriesMedia>())
        }

        @Test
        @DisplayName("성공: 어드민은 타인 시리즈의 썸네일도 삭제할 수 있다")
        fun deleteMedia_AdminCanDeleteOthers() {
            val series = mock(Series::class.java)
            val media = mock(Media::class.java)
            val seriesMedia = mock(SeriesMedia::class.java)

            given(media.id).willReturn(10L)
            given(seriesMedia.media).willReturn(media)
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(series)
            given(seriesMediaRepository.findBySeries(series)).willReturn(seriesMedia)

            seriesMediaService.deleteMedia(1L, 99L, UserRole.ADMIN)

            verify(seriesMediaRepository).delete(seriesMedia)
            verify(mediaService).deleteMedia(10L)
        }
    }

    @Nested
    @DisplayName("deleteMediaIfExists")
    inner class DeleteMediaIfExists {
        @Test
        @DisplayName("성공: 썸네일이 있으면 SeriesMedia와 Media를 정리한다")
        fun deleteMediaIfExists_Success() {
            val series = mock(Series::class.java)
            val media = mock(Media::class.java)
            given(media.id).willReturn(10L)
            val seriesMedia = mock(SeriesMedia::class.java)
            given(seriesMedia.media).willReturn(media)

            given(seriesMediaRepository.findBySeries(series)).willReturn(seriesMedia)

            seriesMediaService.deleteMediaIfExists(series)

            verify(seriesMediaRepository).delete(seriesMedia)
            verify(mediaService).deleteMedia(10L)
        }

        @Test
        @DisplayName("성공: 썸네일이 없으면 아무 것도 하지 않는다")
        fun deleteMediaIfExists_NoMedia_NoOp() {
            val series = mock(Series::class.java)
            given(seriesMediaRepository.findBySeries(series)).willReturn(null)

            seriesMediaService.deleteMediaIfExists(series)

            verify(seriesMediaRepository, never()).delete(any<SeriesMedia>())
            verify(mediaService, never()).deleteMedia(anyLong())
        }
    }
}
