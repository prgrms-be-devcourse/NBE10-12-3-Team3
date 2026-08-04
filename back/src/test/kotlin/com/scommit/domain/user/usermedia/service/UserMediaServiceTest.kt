package com.scommit.domain.user.usermedia.service

import com.scommit.domain.media.media.entity.Media
import com.scommit.domain.media.media.entity.MediaType
import com.scommit.domain.media.media.service.MediaService
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.domain.user.usermedia.entity.UserMedia
import com.scommit.domain.user.usermedia.repository.UserMediaRepository
import com.scommit.global.exception.BusinessException
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
import java.util.Optional

// Mockito ArgumentMatchers.any()는 null을 반환하는데, Kotlin에서 선언된 non-null 파라미터에
// 직접 전달하면 플랫폼 타입 널 체크가 삽입되어 NPE가 발생한다. mockito-kotlin 미사용 환경의 표준 우회책.
@Suppress("UNCHECKED_CAST", "IgnoredReturnValue") // 매처 등록이 목적이므로 반환값은 의도적으로 버린다
private fun <T> any(): T {
    Mockito.any<T>()
    return null as T
}

@ExtendWith(MockitoExtension::class)
class UserMediaServiceTest {
    private val file = MockMultipartFile("file", "profile.png", "image/png", "content".toByteArray())

    @Mock
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var mediaService: MediaService

    @Mock
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var userMediaRepository: UserMediaRepository

    @Mock
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var userRepository: UserRepository

    @InjectMocks
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var userMediaService: UserMediaService

    @Nested
    @DisplayName("uploadMedia")
    inner class UploadMedia {
        @Test
        @DisplayName("성공: 프로필 이미지 없는 유저의 첫 업로드")
        fun uploadMedia_First_Success() {
            val user = mock(User::class.java)
            val media = mock(Media::class.java)
            val userMedia = mock(UserMedia::class.java)

            given(userRepository.findById(1L)).willReturn(Optional.of(user))
            given(userMediaRepository.findByUser(user)).willReturn(null)
            given(mediaService.uploadMedia(file, "user")).willReturn(media)
            given(userMediaRepository.save(any<UserMedia>())).willReturn(userMedia)
            given(userMedia.user).willReturn(user)
            given(userMedia.media).willReturn(media)
            given(media.url).willReturn("user/uuid_profile.png")
            given(media.type).willReturn(MediaType.IMAGE)

            val result = userMediaService.uploadMedia(1L, file)

            assertThat(result).isNotNull()
            verify(userMediaRepository, never()).delete(any<UserMedia>())
            verify(mediaService, never()).deleteMedia(anyLong())
        }

        @Test
        @DisplayName("성공: 기존 프로필 이미지 있을 때 교체 시 기존 것이 삭제된다")
        fun uploadMedia_Replace_Success() {
            val user = mock(User::class.java)

            val existingMedia =
                mock(Media::class.java).apply {
                    given(id).willReturn(10L)
                    given(url).willReturn("user/uuid_old.png")
                    given(type).willReturn(MediaType.IMAGE)
                }
            val existingUserMedia = mock(UserMedia::class.java)
            given(existingUserMedia.media).willReturn(existingMedia)
            given(existingUserMedia.user).willReturn(user)

            val newMedia = mock(Media::class.java)

            given(userRepository.findById(1L)).willReturn(Optional.of(user))
            given(userMediaRepository.findByUser(user)).willReturn(existingUserMedia)
            given(mediaService.uploadMedia(file, "user")).willReturn(newMedia)

            userMediaService.uploadMedia(1L, file)

            verify(existingUserMedia).media = newMedia
            verify(mediaService).deleteMedia(10L)
            verify(userMediaRepository, never()).delete(any<UserMedia>())
            verify(userMediaRepository, never()).save(any<UserMedia>())
        }

        @Test
        @DisplayName("실패: 존재하지 않는 userId로 업로드 시 예외를 던진다")
        fun uploadMedia_UserNotFound_Fail() {
            given(userRepository.findById(999L)).willReturn(Optional.empty())

            assertThatThrownBy { userMediaService.uploadMedia(999L, file) }
                .isInstanceOf(BusinessException::class.java)

            verify(mediaService, never()).uploadMedia(any<MultipartFile?>(), any<String>())
        }
    }

    @Nested
    @DisplayName("getMedia")
    inner class GetMedia {
        @Test
        @DisplayName("성공: 프로필 이미지를 조회한다")
        fun getMedia_Success() {
            val user = mock(User::class.java)
            val media = mock(Media::class.java)
            val userMedia = mock(UserMedia::class.java)

            given(userRepository.findById(1L)).willReturn(Optional.of(user))
            given(userMediaRepository.findByUser(user)).willReturn(userMedia)
            given(userMedia.user).willReturn(user)
            given(userMedia.media).willReturn(media)
            given(media.url).willReturn("user/uuid_profile.png")
            given(media.type).willReturn(MediaType.IMAGE)

            val result = userMediaService.getMedia(1L)

            assertThat(result).isNotNull()
            assertThat(result?.url).isEqualTo("user/uuid_profile.png")
        }

        @Test
        @DisplayName("실패: 존재하지 않는 userId로 조회 시 예외를 던진다")
        fun getMedia_UserNotFound_Fail() {
            given(userRepository.findById(999L)).willReturn(Optional.empty())

            assertThatThrownBy { userMediaService.getMedia(999L) }
                .isInstanceOf(BusinessException::class.java)
        }

        @Test
        @DisplayName("성공: 프로필 이미지가 없는 유저 조회 시 null을 반환한다")
        fun getMedia_NoMedia_Success() {
            val user = mock(User::class.java)

            given(userRepository.findById(1L)).willReturn(Optional.of(user))
            given(userMediaRepository.findByUser(user)).willReturn(null)

            val result = userMediaService.getMedia(1L)
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("deleteMedia")
    inner class DeleteMedia {
        @Test
        @DisplayName("성공: UserMedia와 Media가 삭제된다")
        fun deleteMedia_Success() {
            val user = mock(User::class.java)

            val media = mock(Media::class.java)
            given(media.id).willReturn(10L)
            val userMedia = mock(UserMedia::class.java)
            given(userMedia.media).willReturn(media)

            given(userRepository.findById(1L)).willReturn(Optional.of(user))
            given(userMediaRepository.findByUser(user)).willReturn(userMedia)

            userMediaService.deleteMedia(1L)

            verify(userMediaRepository).delete(userMedia)
            verify(mediaService).deleteMedia(10L)
        }

        @Test
        @DisplayName("실패: 존재하지 않는 userId로 삭제 시 예외를 던진다")
        fun deleteMedia_UserNotFound_Fail() {
            given(userRepository.findById(999L)).willReturn(Optional.empty())

            assertThatThrownBy { userMediaService.deleteMedia(999L) }
                .isInstanceOf(BusinessException::class.java)

            verify(userMediaRepository, never()).delete(any<UserMedia>())
        }

        @Test
        @DisplayName("실패: 프로필 이미지가 없는 유저 삭제 시도 시 예외를 던진다")
        fun deleteMedia_NoMedia_Fail() {
            val user = mock(User::class.java)

            given(userRepository.findById(1L)).willReturn(Optional.of(user))
            given(userMediaRepository.findByUser(user)).willReturn(null)

            assertThatThrownBy { userMediaService.deleteMedia(1L) }
                .isInstanceOf(BusinessException::class.java)

            verify(userMediaRepository, never()).delete(any<UserMedia>())
        }
    }
}
