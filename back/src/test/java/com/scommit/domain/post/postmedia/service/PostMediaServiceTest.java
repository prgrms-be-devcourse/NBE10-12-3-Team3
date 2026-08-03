package com.scommit.domain.post.postmedia.service;

import com.scommit.domain.media.media.entity.Media;
import com.scommit.domain.media.media.entity.MediaType;
import com.scommit.domain.media.media.service.MediaService;
import com.scommit.domain.post.post.entity.Post;
import com.scommit.domain.post.post.repository.PostRepository;
import com.scommit.domain.post.postmedia.dto.PostMediaResponse;
import com.scommit.domain.post.postmedia.entity.PostMedia;
import com.scommit.domain.post.postmedia.entity.PostMediaType;
import com.scommit.domain.post.postmedia.repository.PostMediaRepository;
import com.scommit.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostMediaServiceTest {

    private final MockMultipartFile file =
            new MockMultipartFile("file", "test.png", "image/png", "content".getBytes());
    @Mock
    private MediaService mediaService;
    @Mock
    private PostMediaRepository postMediaRepository;
    @Mock
    private PostRepository postRepository;
    @InjectMocks
    private PostMediaService postMediaService;

    @Nested
    @DisplayName("uploadMedia")
    class UploadMedia {

        @Test
        @DisplayName("성공: BODY 타입은 중복 체크 없이 추가된다")
        void uploadMedia_Body_Success() {
            Long postId = 1L;
            Post post = mock(Post.class);
            Media media = mock(Media.class);
            PostMedia postMedia = mock(PostMedia.class);

            given(postRepository.findById(postId)).willReturn(Optional.of(post));
            given(post.getDeletedAt()).willReturn(null);
            given(mediaService.uploadMedia(file, "post")).willReturn(media);
            given(postMediaRepository.save(any(PostMedia.class))).willReturn(postMedia);
            given(postMedia.getPost()).willReturn(post);
            given(postMedia.getMedia()).willReturn(media);
            given(postMedia.getType()).willReturn(PostMediaType.BODY);
            given(media.getUrl()).willReturn("post/uuid_test.png");
            given(media.getType()).willReturn(MediaType.IMAGE);

            PostMediaResponse result = postMediaService.uploadMedia(postId, file, PostMediaType.BODY);

            assertThat(result).isNotNull();
            verify(postMediaRepository, never()).findByPostAndType(any(), any());
        }

        @Test
        @DisplayName("성공: THUMBNAIL 첫 업로드 시 기존 삭제 없이 저장된다")
        void uploadMedia_Thumbnail_First_Success() {
            Long postId = 1L;
            Post post = mock(Post.class);
            Media media = mock(Media.class);
            PostMedia postMedia = mock(PostMedia.class);

            given(postRepository.findById(postId)).willReturn(Optional.of(post));
            given(post.getDeletedAt()).willReturn(null);
            given(postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)).willReturn(null);
            given(mediaService.uploadMedia(file, "post")).willReturn(media);
            given(postMediaRepository.save(any(PostMedia.class))).willReturn(postMedia);
            given(postMedia.getPost()).willReturn(post);
            given(postMedia.getMedia()).willReturn(media);
            given(postMedia.getType()).willReturn(PostMediaType.THUMBNAIL);
            given(media.getUrl()).willReturn("post/uuid_test.png");
            given(media.getType()).willReturn(MediaType.IMAGE);

            postMediaService.uploadMedia(postId, file, PostMediaType.THUMBNAIL);

            verify(postMediaRepository, never()).delete(any(PostMedia.class));
            verify(mediaService, never()).deleteMedia(anyLong());
        }

        @Test
        @DisplayName("성공: THUMBNAIL 중복 업로드 시 기존 썸네일을 교체한다")
        void uploadMedia_Thumbnail_Replace_Success() {
            Long postId = 1L;
            Post post = mock(Post.class);

            Media existingMedia = mock(Media.class);
            given(existingMedia.getId()).willReturn(10L);
            given(existingMedia.getUrl()).willReturn("post/uuid_old.png");
            given(existingMedia.getType()).willReturn(MediaType.IMAGE);
            PostMedia existingPostMedia = mock(PostMedia.class);
            given(existingPostMedia.getMedia()).willReturn(existingMedia);
            given(existingPostMedia.getPost()).willReturn(post);
            given(existingPostMedia.getType()).willReturn(PostMediaType.THUMBNAIL);

            Media newMedia = mock(Media.class);

            given(postRepository.findById(postId)).willReturn(Optional.of(post));
            given(post.getDeletedAt()).willReturn(null);
            given(postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)).willReturn(existingPostMedia);
            given(mediaService.uploadMedia(file, "post")).willReturn(newMedia);

            postMediaService.uploadMedia(postId, file, PostMediaType.THUMBNAIL);

            verify(existingPostMedia).setMedia(newMedia);
            verify(mediaService).deleteMedia(10L);
            verify(postMediaRepository, never()).delete(any());
            verify(postMediaRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 postId로 업로드 시 예외를 던진다")
        void uploadMedia_PostNotFound_Fail() {
            given(postRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> postMediaService.uploadMedia(999L, file, PostMediaType.BODY))
                    .isInstanceOf(BusinessException.class);

            verify(mediaService, never()).uploadMedia(any(), any());
        }

        @Test
        @DisplayName("실패: 소프트삭제된 포스트에 업로드 시도 시 예외를 던진다")
        void uploadMedia_DeletedPost_Fail() {
            Long postId = 1L;
            Post post = mock(Post.class);

            given(postRepository.findById(postId)).willReturn(Optional.of(post));
            given(post.getDeletedAt()).willReturn(LocalDateTime.now());

            assertThatThrownBy(() -> postMediaService.uploadMedia(postId, file, PostMediaType.BODY))
                    .isInstanceOf(BusinessException.class);

            verify(mediaService, never()).uploadMedia(any(), any());
        }
    }

    @Nested
    @DisplayName("getMediaList")
    class GetMediaList {

        @Test
        @DisplayName("성공: 포스트의 모든 미디어 목록을 반환한다")
        void getMediaList_Success() {
            Long postId = 1L;
            Post post = mock(Post.class);
            Media media1 = mock(Media.class);
            Media media2 = mock(Media.class);
            PostMedia postMedia1 = mock(PostMedia.class);
            PostMedia postMedia2 = mock(PostMedia.class);

            given(postRepository.findById(postId)).willReturn(Optional.of(post));
            given(post.getDeletedAt()).willReturn(null);
            given(postMediaRepository.findAllByPost(post)).willReturn(List.of(postMedia1, postMedia2));
            given(postMedia1.getPost()).willReturn(post);
            given(postMedia1.getMedia()).willReturn(media1);
            given(postMedia1.getType()).willReturn(PostMediaType.THUMBNAIL);
            given(media1.getUrl()).willReturn("post/uuid_thumb.png");
            given(media1.getType()).willReturn(MediaType.IMAGE);
            given(postMedia2.getPost()).willReturn(post);
            given(postMedia2.getMedia()).willReturn(media2);
            given(postMedia2.getType()).willReturn(PostMediaType.BODY);
            given(media2.getUrl()).willReturn("post/uuid_body.png");
            given(media2.getType()).willReturn(MediaType.IMAGE);

            List<PostMediaResponse> result = postMediaService.getMediaList(postId);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("성공: 미디어 없는 포스트 조회 시 빈 목록을 반환한다")
        void getMediaList_Empty_Success() {
            Long postId = 1L;
            Post post = mock(Post.class);

            given(postRepository.findById(postId)).willReturn(Optional.of(post));
            given(post.getDeletedAt()).willReturn(null);
            given(postMediaRepository.findAllByPost(post)).willReturn(List.of());

            List<PostMediaResponse> result = postMediaService.getMediaList(postId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("실패: 존재하지 않는 postId로 조회 시 예외를 던진다")
        void getMediaList_PostNotFound_Fail() {
            given(postRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> postMediaService.getMediaList(999L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("실패: 소프트삭제된 포스트 조회 시 예외를 던진다")
        void getMediaList_DeletedPost_Fail() {
            Long postId = 1L;
            Post post = mock(Post.class);

            given(postRepository.findById(postId)).willReturn(Optional.of(post));
            given(post.getDeletedAt()).willReturn(LocalDateTime.now());

            assertThatThrownBy(() -> postMediaService.getMediaList(postId))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("getThumbnail")
    class GetThumbnail {

        @Test
        @DisplayName("성공: 포스트 썸네일을 반환한다")
        void getThumbnail_Success() {
            Long postId = 1L;
            Post post = mock(Post.class);
            Media media = mock(Media.class);
            PostMedia postMedia = mock(PostMedia.class);

            given(postRepository.findById(postId)).willReturn(Optional.of(post));
            given(post.getDeletedAt()).willReturn(null);
            given(postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)).willReturn(postMedia);
            given(postMedia.getPost()).willReturn(post);
            given(postMedia.getMedia()).willReturn(media);
            given(postMedia.getType()).willReturn(PostMediaType.THUMBNAIL);
            given(media.getUrl()).willReturn("post/uuid_thumb.png");
            given(media.getType()).willReturn(MediaType.IMAGE);

            PostMediaResponse result = postMediaService.getThumbnail(postId);

            assertThat(result).isNotNull();
            assertThat(result.getUrl()).isEqualTo("post/uuid_thumb.png");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 postId로 조회 시 예외를 던진다")
        void getThumbnail_PostNotFound_Fail() {
            given(postRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> postMediaService.getThumbnail(999L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("성공: 썸네일 없는 포스트 조회 시 null을 반환한다")
        void getThumbnail_NoThumbnail_Success() {
            Long postId = 1L;
            Post post = mock(Post.class);

            given(postRepository.findById(postId)).willReturn(Optional.of(post));
            given(post.getDeletedAt()).willReturn(null);
            given(postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)).willReturn(null);

            PostMediaResponse result = postMediaService.getThumbnail(postId);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("실패: 소프트삭제된 포스트 조회 시 예외를 던진다")
        void getThumbnail_DeletedPost_Fail() {
            Long postId = 1L;
            Post post = mock(Post.class);

            given(postRepository.findById(postId)).willReturn(Optional.of(post));
            given(post.getDeletedAt()).willReturn(LocalDateTime.now());

            assertThatThrownBy(() -> postMediaService.getThumbnail(postId))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("deleteMedia")
    class DeleteMedia {

        @Test
        @DisplayName("성공: PostMedia 삭제 후 Media가 삭제된다 (순서 보장)")
        void deleteMedia_Success_Order() {
            Long postId = 1L;
            Long postMediaId = 5L;

            Post post = mock(Post.class);
            given(post.getId()).willReturn(postId);
            given(post.getDeletedAt()).willReturn(null);

            Media media = mock(Media.class);
            given(media.getId()).willReturn(10L);

            PostMedia postMedia = mock(PostMedia.class);
            given(postMedia.getPost()).willReturn(post);
            given(postMedia.getMedia()).willReturn(media);
            given(postMediaRepository.findById(postMediaId)).willReturn(Optional.of(postMedia));

            postMediaService.deleteMedia(postId, postMediaId);

            InOrder inOrder = inOrder(postMediaRepository, mediaService);
            inOrder.verify(postMediaRepository).delete(postMedia);
            inOrder.verify(mediaService).deleteMedia(10L);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 postMediaId로 삭제 시 예외를 던진다")
        void deleteMedia_NotFound_Fail() {
            given(postMediaRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> postMediaService.deleteMedia(1L, 999L))
                    .isInstanceOf(BusinessException.class);

            verify(postMediaRepository, never()).delete(any());
        }

        @Test
        @DisplayName("실패: 다른 포스트의 미디어 삭제 시도 시 예외를 던진다")
        void deleteMedia_WrongPost_Fail() {
            Long postId = 1L;
            Long postMediaId = 5L;

            Post anotherPost = mock(Post.class);
            given(anotherPost.getId()).willReturn(999L);

            PostMedia postMedia = mock(PostMedia.class);
            given(postMedia.getPost()).willReturn(anotherPost);
            given(postMediaRepository.findById(postMediaId)).willReturn(Optional.of(postMedia));

            assertThatThrownBy(() -> postMediaService.deleteMedia(postId, postMediaId))
                    .isInstanceOf(BusinessException.class);

            verify(postMediaRepository, never()).delete(any());
            verify(mediaService, never()).deleteMedia(anyLong());
        }

        @Test
        @DisplayName("실패: 소프트삭제된 포스트의 미디어 삭제 시도 시 예외를 던진다")
        void deleteMedia_DeletedPost_Fail() {
            Long postId = 1L;
            Long postMediaId = 5L;

            Post post = mock(Post.class);
            given(post.getId()).willReturn(postId);
            given(post.getDeletedAt()).willReturn(LocalDateTime.now());

            PostMedia postMedia = mock(PostMedia.class);
            given(postMedia.getPost()).willReturn(post);
            given(postMediaRepository.findById(postMediaId)).willReturn(Optional.of(postMedia));

            assertThatThrownBy(() -> postMediaService.deleteMedia(postId, postMediaId))
                    .isInstanceOf(BusinessException.class);

            verify(postMediaRepository, never()).delete(any());
            verify(mediaService, never()).deleteMedia(anyLong());
        }
    }
}
