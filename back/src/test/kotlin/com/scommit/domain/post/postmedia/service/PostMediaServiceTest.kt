package com.scommit.domain.post.postmedia.service

import com.scommit.domain.media.media.entity.Media
import com.scommit.domain.media.media.entity.MediaType
import com.scommit.domain.media.media.service.MediaService
import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.post.postmedia.dto.PostMediaResponse
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
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockMultipartFile
import java.time.LocalDateTime
import java.util.Optional

// any() returns null at runtime, which fails Kotlin's non-null check on Kotlin-declared repository params.
private fun <T> anyOfType(): T {
    any<T>()
    @Suppress("UNCHECKED_CAST")
    return null as T
}

@ExtendWith(MockitoExtension::class)
class PostMediaServiceTest {
    private val file = MockMultipartFile("file", "test.png", "image/png", "content".toByteArray())

    @Mock
    private lateinit var mediaService: MediaService

    @Mock
    private lateinit var postMediaRepository: PostMediaRepository

    @Mock
    private lateinit var postRepository: PostRepository

    @InjectMocks
    private lateinit var postMediaService: PostMediaService

    @Nested
    @DisplayName("uploadMedia")
    inner class UploadMedia {
        @Test
        @DisplayName("성공: BODY 타입은 중복 체크 없이 추가된다")
        fun uploadMedia_Body_Success() {
            val postId = 1L
            val post = mock(Post::class.java)
            val media = mock(Media::class.java)
            val postMedia = mock(PostMedia::class.java)

            given(postRepository.findById(postId)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(null)
            given(mediaService.uploadMedia(file, "post")).willReturn(media)
            given(postMediaRepository.save(any(PostMedia::class.java))).willReturn(postMedia)
            given(postMedia.post).willReturn(post)
            given(postMedia.media).willReturn(media)
            given(postMedia.type).willReturn(PostMediaType.BODY)
            given(media.url).willReturn("post/uuid_test.png")
            given(media.type).willReturn(MediaType.IMAGE)

            val result = postMediaService.uploadMedia(postId, file, PostMediaType.BODY)

            assertThat(result).isNotNull()
            verify(postMediaRepository, never()).findByPostAndType(anyOfType(), anyOfType())
        }

        @Test
        @DisplayName("성공: THUMBNAIL 첫 업로드 시 기존 삭제 없이 저장된다")
        fun uploadMedia_Thumbnail_First_Success() {
            val postId = 1L
            val post = mock(Post::class.java)
            val media = mock(Media::class.java)
            val postMedia = mock(PostMedia::class.java)

            given(postRepository.findById(postId)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(null)
            given(postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)).willReturn(null)
            given(mediaService.uploadMedia(file, "post")).willReturn(media)
            given(postMediaRepository.save(any(PostMedia::class.java))).willReturn(postMedia)
            given(postMedia.post).willReturn(post)
            given(postMedia.media).willReturn(media)
            given(postMedia.type).willReturn(PostMediaType.THUMBNAIL)
            given(media.url).willReturn("post/uuid_test.png")
            given(media.type).willReturn(MediaType.IMAGE)

            postMediaService.uploadMedia(postId, file, PostMediaType.THUMBNAIL)

            verify(postMediaRepository, never()).delete(any(PostMedia::class.java))
            verify(mediaService, never()).deleteMedia(anyLong())
        }

        @Test
        @DisplayName("성공: THUMBNAIL 중복 업로드 시 기존 썸네일을 교체한다")
        fun uploadMedia_Thumbnail_Replace_Success() {
            val postId = 1L
            val post = mock(Post::class.java)

            val existingMedia = mock(Media::class.java)
            given(existingMedia.id).willReturn(10L)
            given(existingMedia.url).willReturn("post/uuid_old.png")
            given(existingMedia.type).willReturn(MediaType.IMAGE)
            val existingPostMedia = mock(PostMedia::class.java)
            given(existingPostMedia.media).willReturn(existingMedia)
            given(existingPostMedia.post).willReturn(post)
            given(existingPostMedia.type).willReturn(PostMediaType.THUMBNAIL)

            val newMedia = mock(Media::class.java)

            given(postRepository.findById(postId)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(null)
            given(postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)).willReturn(existingPostMedia)
            given(mediaService.uploadMedia(file, "post")).willReturn(newMedia)

            postMediaService.uploadMedia(postId, file, PostMediaType.THUMBNAIL)

            verify(existingPostMedia).updateMedia(newMedia)
            verify(mediaService).deleteMedia(10L)
            verify(postMediaRepository, never()).delete(any())
            verify(postMediaRepository, never()).save(any())
        }

        @Test
        @DisplayName("실패: 존재하지 않는 postId로 업로드 시 예외를 던진다")
        fun uploadMedia_PostNotFound_Fail() {
            given(postRepository.findById(999L)).willReturn(Optional.empty())

            assertThatThrownBy { postMediaService.uploadMedia(999L, file, PostMediaType.BODY) }
                .isInstanceOf(BusinessException::class.java)

            verify(mediaService, never()).uploadMedia(any(), anyOfType<String>())
        }

        @Test
        @DisplayName("실패: 소프트삭제된 포스트에 업로드 시도 시 예외를 던진다")
        fun uploadMedia_DeletedPost_Fail() {
            val postId = 1L
            val post = mock(Post::class.java)

            given(postRepository.findById(postId)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(LocalDateTime.now())

            assertThatThrownBy { postMediaService.uploadMedia(postId, file, PostMediaType.BODY) }
                .isInstanceOf(BusinessException::class.java)

            verify(mediaService, never()).uploadMedia(any(), anyOfType<String>())
        }
    }

    @Nested
    @DisplayName("getMediaList")
    inner class GetMediaList {
        @Test
        @DisplayName("성공: 포스트의 모든 미디어 목록을 반환한다")
        fun getMediaList_Success() {
            val postId = 1L
            val post = mock(Post::class.java)
            val media1 = mock(Media::class.java)
            val media2 = mock(Media::class.java)
            val postMedia1 = mock(PostMedia::class.java)
            val postMedia2 = mock(PostMedia::class.java)

            given(postRepository.findById(postId)).willReturn(Optional.of(post))
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

            val result = postMediaService.getMediaList(postId)

            assertThat(result).hasSize(2)
        }

        @Test
        @DisplayName("성공: 미디어 없는 포스트 조회 시 빈 목록을 반환한다")
        fun getMediaList_Empty_Success() {
            val postId = 1L
            val post = mock(Post::class.java)

            given(postRepository.findById(postId)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(null)
            given(postMediaRepository.findAllByPost(post)).willReturn(emptyList())

            val result = postMediaService.getMediaList(postId)

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
            val postId = 1L
            val post = mock(Post::class.java)

            given(postRepository.findById(postId)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(LocalDateTime.now())

            assertThatThrownBy { postMediaService.getMediaList(postId) }
                .isInstanceOf(BusinessException::class.java)
        }
    }

    @Nested
    @DisplayName("getThumbnail")
    inner class GetThumbnail {
        @Test
        @DisplayName("성공: 포스트 썸네일을 반환한다")
        fun getThumbnail_Success() {
            val postId = 1L
            val post = mock(Post::class.java)
            val media = mock(Media::class.java)
            val postMedia = mock(PostMedia::class.java)

            given(postRepository.findById(postId)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(null)
            given(postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)).willReturn(postMedia)
            given(postMedia.post).willReturn(post)
            given(postMedia.media).willReturn(media)
            given(postMedia.type).willReturn(PostMediaType.THUMBNAIL)
            given(media.url).willReturn("post/uuid_thumb.png")
            given(media.type).willReturn(MediaType.IMAGE)

            val result = postMediaService.getThumbnail(postId)

            assertThat(result).isNotNull()
            assertThat(result!!.url).isEqualTo("post/uuid_thumb.png")
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
            val postId = 1L
            val post = mock(Post::class.java)

            given(postRepository.findById(postId)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(null)
            given(postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)).willReturn(null)

            val result: PostMediaResponse? = postMediaService.getThumbnail(postId)
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("실패: 소프트삭제된 포스트 조회 시 예외를 던진다")
        fun getThumbnail_DeletedPost_Fail() {
            val postId = 1L
            val post = mock(Post::class.java)

            given(postRepository.findById(postId)).willReturn(Optional.of(post))
            given(post.deletedAt).willReturn(LocalDateTime.now())

            assertThatThrownBy { postMediaService.getThumbnail(postId) }
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

            val post = mock(Post::class.java)
            given(post.id).willReturn(postId)
            given(post.deletedAt).willReturn(null)

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

            verify(postMediaRepository, never()).delete(any())
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

            verify(postMediaRepository, never()).delete(any())
            verify(mediaService, never()).deleteMedia(anyLong())
        }

        @Test
        @DisplayName("실패: 소프트삭제된 포스트의 미디어 삭제 시도 시 예외를 던진다")
        fun deleteMedia_DeletedPost_Fail() {
            val postId = 1L
            val postMediaId = 5L

            val post = mock(Post::class.java)
            given(post.id).willReturn(postId)
            given(post.deletedAt).willReturn(LocalDateTime.now())

            val postMedia = mock(PostMedia::class.java)
            given(postMedia.post).willReturn(post)
            given(postMediaRepository.findById(postMediaId)).willReturn(Optional.of(postMedia))

            assertThatThrownBy { postMediaService.deleteMedia(postId, postMediaId) }
                .isInstanceOf(BusinessException::class.java)

            verify(postMediaRepository, never()).delete(any())
            verify(mediaService, never()).deleteMedia(anyLong())
        }
    }
}
