package com.scommit.domain.media.media.service;

import com.scommit.domain.media.media.entity.Media;
import com.scommit.domain.media.media.entity.MediaType;
import com.scommit.domain.media.media.repository.MediaRepository;
import com.scommit.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LocalMediaServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private MediaRepository mediaRepository;
    @InjectMocks
    private LocalMediaService localMediaService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(localMediaService, "mediaPath", tempDir.toString() + "/");
    }

    @Nested
    @DisplayName("uploadMedia")
    class UploadMedia {

        @Test
        @DisplayName("성공: 이미지 파일 업로드 시 MediaType.IMAGE로 저장하고 반환한다")
        void uploadMedia_Image_Success() {
            MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "content".getBytes());
            Media savedMedia = new Media("post/uuid_test.png", MediaType.IMAGE);
            given(mediaRepository.save(any(Media.class))).willReturn(savedMedia);

            Media result = localMediaService.uploadMedia(file, "post");

            assertThat(result.getType()).isEqualTo(MediaType.IMAGE);
            assertThat(result.getUrl()).contains("post/");
            verify(mediaRepository).save(any(Media.class));
        }

        @Test
        @DisplayName("성공: 비디오 파일 업로드 시 MediaType.VIDEO로 저장한다")
        void uploadMedia_Video_Success() {
            MockMultipartFile file = new MockMultipartFile("file", "test.mp4", "video/mp4", "content".getBytes());
            Media savedMedia = new Media("post/uuid_test.mp4", MediaType.VIDEO);
            given(mediaRepository.save(any(Media.class))).willReturn(savedMedia);

            Media result = localMediaService.uploadMedia(file, "post");

            assertThat(result.getType()).isEqualTo(MediaType.VIDEO);
        }

        @Test
        @DisplayName("성공: category가 url 경로에 포함되어 저장된다")
        void uploadMedia_CategoryIncludedInUrl() {
            MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "content".getBytes());
            given(mediaRepository.save(any(Media.class))).willAnswer(invocation -> invocation.getArgument(0));

            Media result = localMediaService.uploadMedia(file, "users");

            assertThat(result.getUrl()).startsWith("users/");
        }

        @Test
        @DisplayName("실패: 빈 파일 업로드 시 예외를 던진다")
        void uploadMedia_EmptyFile_Fail() {
            MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

            assertThatThrownBy(() -> localMediaService.uploadMedia(emptyFile, "post"))
                    .isInstanceOf(BusinessException.class);

            verify(mediaRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패: null 파일 업로드 시 예외를 던진다")
        void uploadMedia_NullFile_Fail() {
            assertThatThrownBy(() -> localMediaService.uploadMedia(null, "post"))
                    .isInstanceOf(BusinessException.class);

            verify(mediaRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패: 지원하지 않는 파일 형식(pdf) 업로드 시 예외를 던진다")
        void uploadMedia_InvalidType_Fail() {
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());

            assertThatThrownBy(() -> localMediaService.uploadMedia(file, "post"))
                    .isInstanceOf(BusinessException.class);

            verify(mediaRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패: contentType이 null인 경우 예외를 던진다")
        void uploadMedia_NullContentType_Fail() {
            MockMultipartFile file = new MockMultipartFile("file", "test.png", null, "content".getBytes());

            assertThatThrownBy(() -> localMediaService.uploadMedia(file, "post"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("deleteMedia")
    class DeleteMedia {

        @Test
        @DisplayName("성공: DB에서 삭제되고 실제 파일도 삭제된다")
        void deleteMedia_Success() throws IOException {
            Long mediaId = 1L;
            String fileName = "post/delete-test.png";
            Path filePath = tempDir.resolve(fileName);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, "dummy".getBytes());
            assertThat(Files.exists(filePath)).isTrue();

            Media media = new Media(fileName, MediaType.IMAGE);
            given(mediaRepository.findById(mediaId)).willReturn(Optional.of(media));

            localMediaService.deleteMedia(mediaId);

            verify(mediaRepository).delete(media);
            assertThat(Files.exists(filePath)).isFalse();
        }

        @Test
        @DisplayName("성공: 파일이 이미 없어도 예외 없이 정상 처리된다")
        void deleteMedia_FileAlreadyGone_Success() {
            Long mediaId = 1L;
            Media media = new Media("post/already-gone.png", MediaType.IMAGE);
            given(mediaRepository.findById(mediaId)).willReturn(Optional.of(media));

            localMediaService.deleteMedia(mediaId);

            verify(mediaRepository).delete(media);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 mediaId로 삭제 시 예외를 던진다")
        void deleteMedia_NotFound_Fail() {
            given(mediaRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> localMediaService.deleteMedia(999L))
                    .isInstanceOf(BusinessException.class);

            verify(mediaRepository, never()).delete(any());
        }
    }
}
