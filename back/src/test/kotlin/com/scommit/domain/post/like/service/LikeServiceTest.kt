package com.scommit.domain.post.like.service

import com.scommit.domain.post.like.entity.Like
import com.scommit.domain.post.like.repository.LikeRepository
import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class LikeServiceTest {
    @Mock
    private lateinit var likeRepository: LikeRepository

    @Mock
    private lateinit var postRepository: PostRepository

    @InjectMocks
    private lateinit var likeService: LikeService

    private lateinit var actor: User
    private lateinit var post: Post

    @BeforeEach
    fun setUp() {
        actor =
            User(
                email = "actor@test.com",
                nickname = "액터",
                role = UserRole.USER,
            ).also { ReflectionTestUtils.setField(it, "id", 1L) }

        post =
            Post
                .builder()
                .title("테스트 게시글")
                .body("내용")
                .publishStatus(PublishStatus.PUBLIC)
                .accessLevel(PostAccessLevel.FREE)
                .build()
                .also { ReflectionTestUtils.setField(it, "id", 10L) }
    }

    @Nested
    @DisplayName("좋아요 추가 테스트")
    inner class CreateLikeTest {
        @Test
        @DisplayName("성공: 좋아요가 추가되고 likeCount가 증가한다")
        fun createLike_success() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post))

            // when
            likeService.createLike(10L, actor)

            // then
            verify(likeRepository).save(any(Like::class.java))
            verify(postRepository).increaseLikeCount(10L)
        }

        @Test
        @DisplayName("실패: 이미 좋아요한 경우 DataIntegrityViolationException이 발생하면 ALREADY_LIKED 예외를 던진다")
        fun createLike_alreadyLiked() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post))
            given(likeRepository.save(any(Like::class.java)))
                .willThrow(DataIntegrityViolationException("Duplicate entry"))

            // when & then
            assertThatThrownBy { likeService.createLike(10L, actor) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_LIKED)
        }

        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다")
        fun createLike_postNotFound() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty())

            // when & then
            assertThatThrownBy { likeService.createLike(999L, actor) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND)

            verify(likeRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("좋아요 취소 테스트")
    inner class DeleteLikeTest {
        @Test
        @DisplayName("성공: 좋아요가 취소되고 likeCount가 감소한다")
        fun deleteLike_success() {
            // given
            ReflectionTestUtils.setField(post, "likeCount", 1L)
            val like = Like(post = post, user = actor)
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post))
            given(likeRepository.findByPostIdAndUserId(10L, 1L)).willReturn(like)

            // when
            likeService.deleteLike(10L, actor)

            // then
            verify(likeRepository).delete(like)
            verify(postRepository).decreaseLikeCount(10L)
        }

        @Test
        @DisplayName("실패: 좋아요가 없는 경우 LIKE_NOT_FOUND 예외를 던진다")
        fun deleteLike_likeNotFound() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post))
            given(likeRepository.findByPostIdAndUserId(10L, 1L)).willReturn(null)

            // when & then
            assertThatThrownBy { likeService.deleteLike(10L, actor) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LIKE_NOT_FOUND)

            verify(likeRepository, never()).delete(any())
        }

        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다")
        fun deleteLike_postNotFound() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty())

            // when & then
            assertThatThrownBy { likeService.deleteLike(999L, actor) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND)

            verify(likeRepository, never()).delete(any())
        }
    }
}
