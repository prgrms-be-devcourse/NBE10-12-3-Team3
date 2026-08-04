package com.scommit.domain.post.postmedia.service

import com.scommit.domain.media.media.entity.Media
import com.scommit.domain.media.media.entity.MediaType
import com.scommit.domain.media.media.service.MediaService
import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.post.postmedia.entity.PostMedia
import com.scommit.domain.post.postmedia.entity.PostMediaType
import com.scommit.domain.post.postmedia.repository.PostMediaRepository
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
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.util.Optional

// Mockito ArgumentMatchers.any()는 null을 반환하는데, Kotlin에서 선언된 non-null 파라미터에
// 직접 전달하면 플랫폼 타입 널 체크가 삽입되어 NPE가 발생한다. mockito-kotlin 미사용 환경의 표준 우회책.
@Suppress("UNCHECKED_CAST", "IgnoredReturnValue") // 매처 등록이 목적이므로 반환값은 의도적으로 버린다
private fun <T> any(): T {
    Mockito.any<T>()
    return null as T
}

@ExtendWith(MockitoExtension::class)
class PostMediaServiceTest {
    private val file = MockMultipartFile("file", "test.png", "image/png", "content".toByteArray())

    @Mock
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var mediaService: MediaService

    @Mock
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var postMediaRepository: PostMediaRepository

    @Mock
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var postRepository: PostRepository

    @InjectMocks
    @Suppress("VarCouldBeVal") // Mockito가 리플렉션으로 주입하므로 lateinit var가 필요하다
    private lateinit var postMediaService: PostMediaService

    @Nested
    @DisplayName("uploadMedia")
    inner class UploadMedia {
        @Test
        @DisplayName("성공: BODY 타입은 중복 체크 없이 추가된다")
        fun uploadMedia_Body_Success() {
            val post = mock(Post::class.java)
            val media = mock(Media::class.java)
            val postMedia = mock(PostMedia::class.java)

            given(postRepository.findById(1L)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(null)
            given(mediaService.uploadMedia(file, "post")).willReturn(media)
            given(postMediaRepository.save(any<PostMedia>())).willReturn(postMedia)
            given(postMedia.post).willReturn(post)
            given(postMedia.media).willReturn(media)
            given(postMedia.type).willReturn(PostMediaType.BODY)
            given(media.url).willReturn("post/uuid_test.png")
            given(media.type).willReturn(MediaType.IMAGE)

            val result = postMediaService.uploadMedia(1L, file, PostMediaType.BODY)

            assertThat(result).isNotNull()
            verify(postMediaRepository, never()).findByPostAndType(any<Post>(), any<PostMediaType>())
        }

        @Test
        @DisplayName("성공: THUMBNAIL 첫 업로드 시 기존 삭제 없이 저장된다")
        fun uploadMedia_Thumbnail_First_Success() {
            val post = mock(Post::class.java)
            val media = mock(Media::class.java)
            val postMedia = mock(PostMedia::class.java)

            given(postRepository.findById(1L)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(null)
            given(postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)).willReturn(null)
            given(mediaService.uploadMedia(file, "post")).willReturn(media)
            given(postMediaRepository.save(any<PostMedia>())).willReturn(postMedia)
            given(postMedia.post).willReturn(post)
            given(postMedia.media).willReturn(media)
            given(postMedia.type).willReturn(PostMediaType.THUMBNAIL)
            given(media.url).willReturn("post/uuid_test.png")
            given(media.type).willReturn(MediaType.IMAGE)

            postMediaService.uploadMedia(1L, file, PostMediaType.THUMBNAIL)

            verify(postMediaRepository, never()).delete(any<PostMedia>())
            verify(mediaService, never()).deleteMedia(anyLong())
        }

        @Test
        @DisplayName("성공: THUMBNAIL 중복 업로드 시 기존 썸네일을 교체한다")
        fun uploadMedia_Thumbnail_Replace_Success() {
            val post = mock(Post::class.java)

            val existingMedia =
                mock(Media::class.java).apply {
                    given(id).willReturn(10L)
                    given(url).willReturn("post/uuid_old.png")
                    given(type).willReturn(MediaType.IMAGE)
                }
            val existingPostMedia = mock(PostMedia::class.java)
            given(existingPostMedia.media).willReturn(existingMedia)
            given(existingPostMedia.post).willReturn(post)
            given(existingPostMedia.type).willReturn(PostMediaType.THUMBNAIL)

            val newMedia = mock(Media::class.java)

            given(postRepository.findById(1L)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(null)
            given(postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)).willReturn(existingPostMedia)
            given(mediaService.uploadMedia(file, "post")).willReturn(newMedia)

            postMediaService.uploadMedia(1L, file, PostMediaType.THUMBNAIL)

            verify(existingPostMedia).media = newMedia
            verify(mediaService).deleteMedia(10L)
            verify(postMediaRepository, never()).delete(any<PostMedia>())
            verify(postMediaRepository, never()).save(any<PostMedia>())
        }

        @Test
        @DisplayName("실패: 존재하지 않는 postId로 업로드 시 예외를 던진다")
        fun uploadMedia_PostNotFound_Fail() {
            given(postRepository.findById(999L)).willReturn(Optional.empty())

            assertThatThrownBy { postMediaService.uploadMedia(999L, file, PostMediaType.BODY) }
                .isInstanceOf(BusinessException::class.java)

            verify(mediaService, never()).uploadMedia(any<MultipartFile?>(), any<String>())
        }

        @Test
        @DisplayName("실패: 소프트삭제된 포스트에 업로드 시도 시 예외를 던진다")
        fun uploadMedia_DeletedPost_Fail() {
            val post = mock(Post::class.java)

            given(postRepository.findById(1L)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(LocalDateTime.now())

            assertThatThrownBy { postMediaService.uploadMedia(1L, file, PostMediaType.BODY) }
                .isInstanceOf(BusinessException::class.java)

            verify(mediaService, never()).uploadMedia(any<MultipartFile?>(), any<String>())
        }
    }

    @Nested
    @DisplayName("getMediaList")
    inner class GetMediaList {
        @Test
        @DisplayName("성공: 포스트의 모든 미디어 목록을 반환한다")
        fun getMediaList_Success() {
            val post = mock(Post::class.java)
            val media1 = mock(Media::class.java)
            val media2 = mock(Media::class.java)
            val postMedia1 = mock(PostMedia::class.java)
            val postMedia2 = mock(PostMedia::class.java)

            given(postRepository.findById(1L)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(null)
            given(postMediaRepository.findAllByPost(post)).willReturn(listOf(postMedia1, postMedia2))
            given(postMedia1.post).willReturn(post)
            given(postMedia1.media).willReturn(media1)
            given(postMedia1.type).willReturn(PostMediaType.THUMBNAIL)
            given(media1.url).willReturn("post/uuid_thumb.png")
            given(media1.type).willReturn(MediaType.IMAGE)
            given(postMedia2.post).willReturn(post)
            given(postMedia2.media).willReturn(media2)
            given(postMedia2.type).willReturn(PostMediaType.BODY)
            given(media2.url).willReturn("post/uuid_body.png")
            given(media2.type).willReturn(MediaType.IMAGE)

            val result = postMediaService.getMediaList(1L)

            assertThat(result).hasSize(2)
        }

        @Test
        @DisplayName("성공: 미디어 없는 포스트 조회 시 빈 목록을 반환한다")
        fun getMediaList_Empty_Success() {
            val post = mock(Post::class.java)

            given(postRepository.findById(1L)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(null)
            given(postMediaRepository.findAllByPost(post)).willReturn(emptyList())

            val result = postMediaService.getMediaList(1L)

            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("실패: 존재하지 않는 postId로 조회 시 예외를 던진다")
        fun getMediaList_PostNotFound_Fail() {
            given(postRepository.findById(999L)).willReturn(Optional.empty())

            assertThatThrownBy { postMediaService.getMediaList(999L) }
                .isInstanceOf(BusinessException::class.java)
        }

        @Test
        @DisplayName("실패: 소프트삭제된 포스트 조회 시 예외를 던진다")
        fun getMediaList_DeletedPost_Fail() {
            val post = mock(Post::class.java)

            given(postRepository.findById(1L)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(LocalDateTime.now())

            assertThatThrownBy { postMediaService.getMediaList(1L) }
                .isInstanceOf(BusinessException::class.java)
        }
    }

    @Nested
    @DisplayName("getThumbnail")
    inner class GetThumbnail {
        @Test
        @DisplayName("성공: 포스트 썸네일을 반환한다")
        fun getThumbnail_Success() {
            val post = mock(Post::class.java)
            val media = mock(Media::class.java)
            val postMedia = mock(PostMedia::class.java)

            given(postRepository.findById(1L)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(null)
            given(postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)).willReturn(postMedia)
            given(postMedia.post).willReturn(post)
            given(postMedia.media).willReturn(media)
            given(postMedia.type).willReturn(PostMediaType.THUMBNAIL)
            given(media.url).willReturn("post/uuid_thumb.png")
            given(media.type).willReturn(MediaType.IMAGE)

            val result = postMediaService.getThumbnail(1L)

            assertThat(result).isNotNull()
            assertThat(result?.url).isEqualTo("post/uuid_thumb.png")
        }

        @Test
        @DisplayName("실패: 존재하지 않는 postId로 조회 시 예외를 던진다")
        fun getThumbnail_PostNotFound_Fail() {
            given(postRepository.findById(999L)).willReturn(Optional.empty())

            assertThatThrownBy { postMediaService.getThumbnail(999L) }
                .isInstanceOf(BusinessException::class.java)
        }

        @Test
        @DisplayName("성공: 썸네일 없는 포스트 조회 시 null을 반환한다")
        fun getThumbnail_NoThumbnail_Success() {
            val post = mock(Post::class.java)

            given(postRepository.findById(1L)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(null)
            given(postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)).willReturn(null)

            val result = postMediaService.getThumbnail(1L)
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("실패: 소프트삭제된 포스트 조회 시 예외를 던진다")
        fun getThumbnail_DeletedPost_Fail() {
            val post = mock(Post::class.java)

            given(postRepository.findById(1L)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(LocalDateTime.now())

            assertThatThrownBy { postMediaService.getThumbnail(1L) }
                .isInstanceOf(BusinessException::class.java)
        }
    }

    @Nested
    @DisplayName("deleteMedia")
    inner class DeleteMedia {
        @Test
        @DisplayName("성공: PostMedia 삭제 후 Media가 삭제된다 (순서 보장)")
        fun deleteMedia_Success_Order() {
            val postId = 1L
            val postMediaId = 5L

            val post =
                mock(Post::class.java).apply {
                    given(id).willReturn(postId)
                    given(deletedAt).willReturn(null)
                }

            val media = mock(Media::class.java)
            given(media.id).willReturn(10L)

            val postMedia = mock(PostMedia::class.java)
            given(postMedia.post).willReturn(post)
            given(postMedia.media).willReturn(media)
            given(postMediaRepository.findById(postMediaId)).willReturn(Optional.of(postMedia))

            postMediaService.deleteMedia(postId, postMediaId)

            val order = inOrder(postMediaRepository, mediaService)
            order.verify(postMediaRepository).delete(postMedia)
            order.verify(mediaService).deleteMedia(10L)
        }

        @Test
        @DisplayName("실패: 존재하지 않는 postMediaId로 삭제 시 예외를 던진다")
        fun deleteMedia_NotFound_Fail() {
            given(postMediaRepository.findById(999L)).willReturn(Optional.empty())

            assertThatThrownBy { postMediaService.deleteMedia(1L, 999L) }
                .isInstanceOf(BusinessException::class.java)

            verify(postMediaRepository, never()).delete(any<PostMedia>())
        }

        @Test
        @DisplayName("실패: 다른 포스트의 미디어 삭제 시도 시 예외를 던진다")
        fun deleteMedia_WrongPost_Fail() {
            val postId = 1L
            val postMediaId = 5L

            val anotherPost = mock(Post::class.java)
            given(anotherPost.id).willReturn(999L)

            val postMedia = mock(PostMedia::class.java)
            given(postMedia.post).willReturn(anotherPost)
            given(postMediaRepository.findById(postMediaId)).willReturn(Optional.of(postMedia))

            assertThatThrownBy { postMediaService.deleteMedia(postId, postMediaId) }
                .isInstanceOf(BusinessException::class.java)

            verify(postMediaRepository, never()).delete(any<PostMedia>())
            verify(mediaService, never()).deleteMedia(anyLong())
        }

        @Test
        @DisplayName("실패: 소프트삭제된 포스트의 미디어 삭제 시도 시 예외를 던진다")
        fun deleteMedia_DeletedPost_Fail() {
            val postId = 1L
            val postMediaId = 5L

            val post =
                mock(Post::class.java).apply {
                    given(id).willReturn(postId)
                    given(deletedAt).willReturn(LocalDateTime.now())
                }

            val postMedia = mock(PostMedia::class.java)
            given(postMedia.post).willReturn(post)
            given(postMediaRepository.findById(postMediaId)).willReturn(Optional.of(postMedia))

            assertThatThrownBy { postMediaService.deleteMedia(postId, postMediaId) }
                .isInstanceOf(BusinessException::class.java)

            verify(postMediaRepository, never()).delete(any<PostMedia>())
            verify(mediaService, never()).deleteMedia(anyLong())
        }
    }
}
