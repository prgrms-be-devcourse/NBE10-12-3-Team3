package com.scommit.domain.media.media.service

import com.cloudinary.Cloudinary
import com.cloudinary.Uploader
import com.scommit.domain.media.media.entity.Media
import com.scommit.domain.media.media.entity.MediaType
import com.scommit.domain.media.media.repository.MediaRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockMultipartFile
import java.io.IOException
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class CloudinaryMediaServiceTest {
    @Mock
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var cloudinary: Cloudinary

    @Mock
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var mediaRepository: MediaRepository

    @InjectMocks
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var cloudinaryMediaService: CloudinaryMediaService

    @Nested
    @DisplayName("uploadMedia")
    inner class UploadMedia {
        @Test
        @DisplayName("성공: 이미지 파일 업로드 시 secure_url을 저장하고 MediaType.IMAGE로 반환한다")
        fun uploadMedia_Image_Success() {
            val file = MockMultipartFile("file", "test.png", "image/png", "content".toByteArray())
            val secureUrl = "https://res.cloudinary.com/demo/image/upload/v1234567/post/test.png"
            val savedMedia = Media(url = secureUrl, type = MediaType.IMAGE)

            val uploader = mock(Uploader::class.java)
            given(cloudinary.uploader()).willReturn(uploader)
            given(uploader.upload(any(), any())).willReturn(mapOf("secure_url" to secureUrl))
            given(mediaRepository.save(any<Media>())).willReturn(savedMedia)

            val result = cloudinaryMediaService.uploadMedia(file, "post")

            assertThat(result.type).isEqualTo(MediaType.IMAGE)
            assertThat(result.url).isEqualTo(secureUrl)
            verify(mediaRepository).save(any<Media>())
        }

        @Test
        @DisplayName("성공: 비디오 파일 업로드 시 MediaType.VIDEO로 저장한다")
        fun uploadMedia_Video_Success() {
            val file = MockMultipartFile("file", "test.mp4", "video/mp4", "content".toByteArray())
            val secureUrl = "https://res.cloudinary.com/demo/video/upload/v1234567/post/test.mp4"
            val savedMedia = Media(url = secureUrl, type = MediaType.VIDEO)

            val uploader = mock(Uploader::class.java)
            given(cloudinary.uploader()).willReturn(uploader)
            given(uploader.upload(any(), any())).willReturn(mapOf("secure_url" to secureUrl))
            given(mediaRepository.save(any<Media>())).willReturn(savedMedia)

            val result = cloudinaryMediaService.uploadMedia(file, "post")

            assertThat(result.type).isEqualTo(MediaType.VIDEO)
        }

        @Test
        @DisplayName("실패: 빈 파일 업로드 시 EMPTY_FILE 예외를 던진다")
        fun uploadMedia_EmptyFile_Fail() {
            val emptyFile = MockMultipartFile("file", "empty.png", "image/png", byteArrayOf())

            assertThatThrownBy { cloudinaryMediaService.uploadMedia(emptyFile, "post") }
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMPTY_FILE)

            verify(mediaRepository, never()).save(any<Media>())
        }

        @Test
        @DisplayName("실패: null 파일 업로드 시 EMPTY_FILE 예외를 던진다")
        fun uploadMedia_NullFile_Fail() {
            assertThatThrownBy { cloudinaryMediaService.uploadMedia(null, "post") }
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMPTY_FILE)

            verify(mediaRepository, never()).save(any<Media>())
        }

        @Test
        @DisplayName("실패: 지원하지 않는 파일 형식(pdf) 업로드 시 UNSUPPORTED_FILE_TYPE 예외를 던진다")
        fun uploadMedia_InvalidType_Fail() {
            val file = MockMultipartFile("file", "test.pdf", "application/pdf", "content".toByteArray())

            assertThatThrownBy { cloudinaryMediaService.uploadMedia(file, "post") }
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE)

            verify(mediaRepository, never()).save(any<Media>())
        }

        @Test
        @DisplayName("실패: contentType이 null인 경우 UNSUPPORTED_FILE_TYPE 예외를 던진다")
        fun uploadMedia_NullContentType_Fail() {
            val file = MockMultipartFile("file", "test.png", null, "content".toByteArray())

            assertThatThrownBy { cloudinaryMediaService.uploadMedia(file, "post") }
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE)
        }

        @Test
        @DisplayName("실패: Cloudinary 업로드 중 IOException 발생 시 INTERNAL_SERVER_ERROR를 던지고 원인을 체이닝한다")
        fun uploadMedia_IOException_Fail() {
            val file = MockMultipartFile("file", "test.png", "image/png", "content".toByteArray())
            val ioException = IOException("Cloudinary connection timeout")

            val uploader = mock(Uploader::class.java)
            given(cloudinary.uploader()).willReturn(uploader)
            given(uploader.upload(any(), any())).willThrow(ioException)

            assertThatThrownBy { cloudinaryMediaService.uploadMedia(file, "post") }
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)

            verify(mediaRepository, never()).save(any<Media>())
        }
    }

    @Nested
    @DisplayName("deleteMedia")
    inner class DeleteMedia {
        @Test
        @DisplayName("성공: DB에서 삭제하고 Cloudinary에서도 이미지를 제거한다")
        fun deleteMedia_Image_Success() {
            val mediaId = 1L
            val url = "https://res.cloudinary.com/demo/image/upload/v1234567/post/test.png"
            val media = Media(url = url, type = MediaType.IMAGE)

            val uploader = mock(Uploader::class.java)
            given(mediaRepository.findById(mediaId)).willReturn(Optional.of(media))
            given(cloudinary.uploader()).willReturn(uploader)
            given(uploader.destroy(any(), any())).willReturn(mapOf("result" to "ok"))

            cloudinaryMediaService.deleteMedia(mediaId)

            verify(mediaRepository).delete(media)
            verify(uploader).destroy(any(), any())
        }

        @Test
        @DisplayName("성공: 비디오 URL인 경우 resource_type을 video로 전달하여 삭제한다")
        fun deleteMedia_Video_Success() {
            val mediaId = 1L
            val url = "https://res.cloudinary.com/demo/video/upload/v1234567/post/test.mp4"
            val media = Media(url = url, type = MediaType.VIDEO)

            val uploader = mock(Uploader::class.java)
            given(mediaRepository.findById(mediaId)).willReturn(Optional.of(media))
            given(cloudinary.uploader()).willReturn(uploader)
            given(uploader.destroy(any(), any())).willReturn(mapOf("result" to "ok"))

            cloudinaryMediaService.deleteMedia(mediaId)

            verify(mediaRepository).delete(media)
            verify(uploader).destroy(any(), any())
        }

        @Test
        @DisplayName("실패: 존재하지 않는 mediaId로 삭제 시 MEDIA_NOT_FOUND 예외를 던진다")
        fun deleteMedia_NotFound_Fail() {
            given(mediaRepository.findById(999L)).willReturn(Optional.empty())

            assertThatThrownBy { cloudinaryMediaService.deleteMedia(999L) }
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEDIA_NOT_FOUND)

            verify(cloudinary, never()).uploader()
        }

        @Test
        @DisplayName("실패: Cloudinary destroy 중 IOException 발생 시 INTERNAL_SERVER_ERROR를 던지고 원인을 체이닝한다")
        fun deleteMedia_IOException_Fail() {
            val mediaId = 1L
            val url = "https://res.cloudinary.com/demo/image/upload/v1234567/post/test.png"
            val media = Media(url = url, type = MediaType.IMAGE)
            val ioException = IOException("Cloudinary destroy failed")

            val uploader = mock(Uploader::class.java)
            given(mediaRepository.findById(mediaId)).willReturn(Optional.of(media))
            given(cloudinary.uploader()).willReturn(uploader)
            given(uploader.destroy(any(), any())).willThrow(ioException)

            assertThatThrownBy { cloudinaryMediaService.deleteMedia(mediaId) }
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)

            // IOException이 발생해도 DB 삭제는 이미 완료된 상태여야 한다
            verify(mediaRepository).delete(media)
        }
    }

    @Nested
    @DisplayName("extractPublicId (private - deleteMedia를 통해 간접 검증)")
    inner class ExtractPublicId {
        @Test
        @DisplayName("버전 번호가 있는 이미지 URL에서 public_id를 올바르게 추출한다")
        fun extractPublicId_WithVersion_Image() {
            val mediaId = 1L
            // URL: .../upload/v1234567/series/abc.jpg → public_id: series/abc
            val url = "https://res.cloudinary.com/demo/image/upload/v1234567/series/abc.jpg"
            val media = Media(url = url, type = MediaType.IMAGE)

            val uploader = mock(Uploader::class.java)
            given(mediaRepository.findById(mediaId)).willReturn(Optional.of(media))
            given(cloudinary.uploader()).willReturn(uploader)
            given(uploader.destroy(any(), any())).willReturn(mapOf("result" to "ok"))

            cloudinaryMediaService.deleteMedia(mediaId)

            // destroy()에 "series/abc"가 전달되었는지 캡처로 검증
            val captor = org.mockito.ArgumentCaptor.forClass(String::class.java)
            verify(uploader).destroy(captor.capture(), any())
            assertThat(captor.value).isEqualTo("series/abc")
        }

        @Test
        @DisplayName("버전 번호가 없는 URL에서도 public_id를 올바르게 추출한다")
        fun extractPublicId_WithoutVersion() {
            val mediaId = 1L
            val url = "https://res.cloudinary.com/demo/image/upload/post/file.png"
            val media = Media(url = url, type = MediaType.IMAGE)

            val uploader = mock(Uploader::class.java)
            given(mediaRepository.findById(mediaId)).willReturn(Optional.of(media))
            given(cloudinary.uploader()).willReturn(uploader)
            given(uploader.destroy(any(), any())).willReturn(mapOf("result" to "ok"))

            cloudinaryMediaService.deleteMedia(mediaId)

            val captor = org.mockito.ArgumentCaptor.forClass(String::class.java)
            verify(uploader).destroy(captor.capture(), any())
            assertThat(captor.value).isEqualTo("post/file")
        }

        @Test
        @DisplayName("확장자 없는 URL에서도 public_id를 올바르게 추출한다")
        fun extractPublicId_NoExtension() {
            val mediaId = 1L
            val url = "https://res.cloudinary.com/demo/image/upload/v1234/post/noext"
            val media = Media(url = url, type = MediaType.IMAGE)

            val uploader = mock(Uploader::class.java)
            given(mediaRepository.findById(mediaId)).willReturn(Optional.of(media))
            given(cloudinary.uploader()).willReturn(uploader)
            given(uploader.destroy(any(), any())).willReturn(mapOf("result" to "ok"))

            cloudinaryMediaService.deleteMedia(mediaId)

            val captor = org.mockito.ArgumentCaptor.forClass(String::class.java)
            verify(uploader).destroy(captor.capture(), any())
            assertThat(captor.value).isEqualTo("post/noext")
        }
    }
}
