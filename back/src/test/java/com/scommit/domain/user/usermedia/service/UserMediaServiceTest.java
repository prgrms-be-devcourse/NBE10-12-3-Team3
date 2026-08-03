package com.scommit.domain.user.usermedia.service;

import com.scommit.domain.media.media.entity.Media;
import com.scommit.domain.media.media.entity.MediaType;
import com.scommit.domain.media.media.service.MediaService;
import com.scommit.domain.user.user.entity.User;
import com.scommit.domain.user.user.repository.UserRepository;
import com.scommit.domain.user.usermedia.dto.UserMediaResponse;
import com.scommit.domain.user.usermedia.entity.UserMedia;
import com.scommit.domain.user.usermedia.repository.UserMediaRepository;
import com.scommit.global.exception.BusinessException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserMediaServiceTest {

    private final MockMultipartFile file =
            new MockMultipartFile("file", "profile.png", "image/png", "content".getBytes());
    @Mock
    private MediaService mediaService;
    @Mock
    private UserMediaRepository userMediaRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private UserMediaService userMediaService;

    @Nested
    @DisplayName("uploadMedia")
    class UploadMedia {

        @Test
        @DisplayName("성공: 프로필 이미지 없는 유저의 첫 업로드")
        void uploadMedia_First_Success() {
            Long userId = 1L;
            User user = mock(User.class);
            Media media = mock(Media.class);
            UserMedia userMedia = mock(UserMedia.class);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userMediaRepository.findByUser(user)).willReturn(null);
            given(mediaService.uploadMedia(file, "user")).willReturn(media);
            given(userMediaRepository.save(any(UserMedia.class))).willReturn(userMedia);
            given(userMedia.getUser()).willReturn(user);
            given(userMedia.getMedia()).willReturn(media);
            given(media.getUrl()).willReturn("user/uuid_profile.png");
            given(media.getType()).willReturn(MediaType.IMAGE);

            UserMediaResponse result = userMediaService.uploadMedia(userId, file);

            assertThat(result).isNotNull();
            verify(userMediaRepository, never()).delete(any());
            verify(mediaService, never()).deleteMedia(anyLong());
        }

        @Test
        @DisplayName("성공: 기존 프로필 이미지 있을 때 교체 시 기존 것이 삭제된다")
        void uploadMedia_Replace_Success() {
            Long userId = 1L;
            User user = mock(User.class);

            Media existingMedia = mock(Media.class);
            given(existingMedia.getId()).willReturn(10L);
            given(existingMedia.getUrl()).willReturn("user/uuid_old.png");
            given(existingMedia.getType()).willReturn(MediaType.IMAGE);
            UserMedia existingUserMedia = mock(UserMedia.class);
            given(existingUserMedia.getMedia()).willReturn(existingMedia);
            given(existingUserMedia.getUser()).willReturn(user);

            Media newMedia = mock(Media.class);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userMediaRepository.findByUser(user)).willReturn(existingUserMedia);
            given(mediaService.uploadMedia(file, "user")).willReturn(newMedia);

            userMediaService.uploadMedia(userId, file);

            verify(existingUserMedia).setMedia(newMedia);
            verify(mediaService).deleteMedia(10L);
            verify(userMediaRepository, never()).delete(any());
            verify(userMediaRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 userId로 업로드 시 예외를 던진다")
        void uploadMedia_UserNotFound_Fail() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userMediaService.uploadMedia(999L, file))
                    .isInstanceOf(BusinessException.class);

            verify(mediaService, never()).uploadMedia(any(), any());
        }
    }

    @Nested
    @DisplayName("getMedia")
    class GetMedia {

        @Test
        @DisplayName("성공: 프로필 이미지를 조회한다")
        void getMedia_Success() {
            Long userId = 1L;
            User user = mock(User.class);
            Media media = mock(Media.class);
            UserMedia userMedia = mock(UserMedia.class);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userMediaRepository.findByUser(user)).willReturn(userMedia);
            given(userMedia.getUser()).willReturn(user);
            given(userMedia.getMedia()).willReturn(media);
            given(media.getUrl()).willReturn("user/uuid_profile.png");
            given(media.getType()).willReturn(MediaType.IMAGE);

            UserMediaResponse result = userMediaService.getMedia(userId);

            assertThat(result).isNotNull();
            assertThat(result.getUrl()).isEqualTo("user/uuid_profile.png");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 userId로 조회 시 예외를 던진다")
        void getMedia_UserNotFound_Fail() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userMediaService.getMedia(999L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("성공: 프로필 이미지가 없는 유저 조회 시 null을 반환한다")
        void getMedia_NoMedia_Success() {
            Long userId = 1L;
            User user = mock(User.class);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userMediaRepository.findByUser(user)).willReturn(null);

            UserMediaResponse result = userMediaService.getMedia(userId);
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("deleteMedia")
    class DeleteMedia {

        @Test
        @DisplayName("성공: UserMedia와 Media가 삭제된다")
        void deleteMedia_Success() {
            Long userId = 1L;
            User user = mock(User.class);

            Media media = mock(Media.class);
            given(media.getId()).willReturn(10L);
            UserMedia userMedia = mock(UserMedia.class);
            given(userMedia.getMedia()).willReturn(media);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userMediaRepository.findByUser(user)).willReturn(userMedia);

            userMediaService.deleteMedia(userId);

            verify(userMediaRepository).delete(userMedia);
            verify(mediaService).deleteMedia(10L);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 userId로 삭제 시 예외를 던진다")
        void deleteMedia_UserNotFound_Fail() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userMediaService.deleteMedia(999L))
                    .isInstanceOf(BusinessException.class);

            verify(userMediaRepository, never()).delete(any());
        }

        @Test
        @DisplayName("실패: 프로필 이미지가 없는 유저 삭제 시도 시 예외를 던진다")
        void deleteMedia_NoMedia_Fail() {
            Long userId = 1L;
            User user = mock(User.class);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userMediaRepository.findByUser(user)).willReturn(null);

            assertThatThrownBy(() -> userMediaService.deleteMedia(userId))
                    .isInstanceOf(BusinessException.class);

            verify(userMediaRepository, never()).delete(any());
        }
    }
}
