package com.scommit.domain.media.media.service

import com.scommit.domain.media.media.entity.Media
import com.scommit.domain.media.media.entity.MediaType
import com.scommit.domain.media.media.repository.MediaRepository
import com.scommit.global.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.util.ReflectionTestUtils
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class LocalMediaServiceTest {
    @TempDir
    lateinit var tempDir: Path

    @Mock
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var mediaRepository: MediaRepository

    @InjectMocks
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var localMediaService: LocalMediaService

    @BeforeEach
    fun setUp() {
        ReflectionTestUtils.setField(localMediaService, "mediaPath", "$tempDir/")
    }

    @Nested
    @DisplayName("uploadMedia")
    inner class UploadMedia {
        @Test
        @DisplayName("성공: 이미지 파일 업로드 시 MediaType.IMAGE로 저장하고 반환한다")
        fun uploadMedia_Image_Success() {
            val file = MockMultipartFile("file", "test.png", "image/png", "content".toByteArray())
            val savedMedia = Media(url = "post/uuid_test.png", type = MediaType.IMAGE)
            given(mediaRepository.save(any<Media>())).willReturn(savedMedia)

            val result = localMediaService.uploadMedia(file, "post")

            assertThat(result.type).isEqualTo(MediaType.IMAGE)
            assertThat(result.url).contains("post/")
            verify(mediaRepository).save(any<Media>())
        }

        @Test
        @DisplayName("성공: 비디오 파일 업로드 시 MediaType.VIDEO로 저장한다")
        fun uploadMedia_Video_Success() {
            val file = MockMultipartFile("file", "test.mp4", "video/mp4", "content".toByteArray())
            val savedMedia = Media(url = "post/uuid_test.mp4", type = MediaType.VIDEO)
            given(mediaRepository.save(any<Media>())).willReturn(savedMedia)

            val result = localMediaService.uploadMedia(file, "post")

            assertThat(result.type).isEqualTo(MediaType.VIDEO)
        }

        @Test
        @DisplayName("성공: category가 url 경로에 포함되어 저장된다")
        fun uploadMedia_CategoryIncludedInUrl() {
            val file = MockMultipartFile("file", "test.png", "image/png", "content".toByteArray())
            given(mediaRepository.save(any<Media>())).willAnswer { it.getArgument<Media>(0) }

            val result = localMediaService.uploadMedia(file, "users")

            assertThat(result.url).startsWith("users/")
        }

        @Test
        @DisplayName("실패: 빈 파일 업로드 시 예외를 던진다")
        fun uploadMedia_EmptyFile_Fail() {
            val emptyFile = MockMultipartFile("file", "empty.png", "image/png", byteArrayOf())

            assertThatThrownBy { localMediaService.uploadMedia(emptyFile, "post") }
                .isInstanceOf(BusinessException::class.java)

            verify(mediaRepository, never()).save(any<Media>())
        }

        @Test
        @DisplayName("실패: null 파일 업로드 시 예외를 던진다")
        fun uploadMedia_NullFile_Fail() {
            assertThatThrownBy { localMediaService.uploadMedia(null, "post") }
                .isInstanceOf(BusinessException::class.java)

            verify(mediaRepository, never()).save(any<Media>())
        }

        @Test
        @DisplayName("실패: 지원하지 않는 파일 형식(pdf) 업로드 시 예외를 던진다")
        fun uploadMedia_InvalidType_Fail() {
            val file = MockMultipartFile("file", "test.pdf", "application/pdf", "content".toByteArray())

            assertThatThrownBy { localMediaService.uploadMedia(file, "post") }
                .isInstanceOf(BusinessException::class.java)

            verify(mediaRepository, never()).save(any<Media>())
        }

        @Test
        @DisplayName("실패: contentType이 null인 경우 예외를 던진다")
        fun uploadMedia_NullContentType_Fail() {
            val file = MockMultipartFile("file", "test.png", null, "content".toByteArray())

            assertThatThrownBy { localMediaService.uploadMedia(file, "post") }
                .isInstanceOf(BusinessException::class.java)
        }
    }

    @Nested
    @DisplayName("deleteMedia")
    inner class DeleteMedia {
        @Test
        @DisplayName("성공: DB에서 삭제되고 실제 파일도 삭제된다")
        fun deleteMedia_Success() {
            val mediaId = 1L
            val fileName = "post/delete-test.png"
            val filePath =
                tempDir.resolve(fileName).also {
                    Files.createDirectories(it.parent)
                    Files.write(it, "dummy".toByteArray())
                }
            assertThat(Files.exists(filePath)).isTrue()

            val media = Media(url = fileName, type = MediaType.IMAGE)
            given(mediaRepository.findById(mediaId)).willReturn(Optional.of(media))

            localMediaService.deleteMedia(mediaId)

            verify(mediaRepository).delete(media)
            assertThat(Files.exists(filePath)).isFalse()
        }

        @Test
        @DisplayName("성공: 파일이 이미 없어도 예외 없이 정상 처리된다")
        fun deleteMedia_FileAlreadyGone_Success() {
            val mediaId = 1L
            val media = Media(url = "post/already-gone.png", type = MediaType.IMAGE)
            given(mediaRepository.findById(mediaId)).willReturn(Optional.of(media))

            localMediaService.deleteMedia(mediaId)

            verify(mediaRepository).delete(media)
        }

        @Test
        @DisplayName("실패: 존재하지 않는 mediaId로 삭제 시 예외를 던진다")
        fun deleteMedia_NotFound_Fail() {
            given(mediaRepository.findById(999L)).willReturn(Optional.empty())

            assertThatThrownBy { localMediaService.deleteMedia(999L) }
                .isInstanceOf(BusinessException::class.java)

            verify(mediaRepository, never()).delete(any<Media>())
        }
    }
}
