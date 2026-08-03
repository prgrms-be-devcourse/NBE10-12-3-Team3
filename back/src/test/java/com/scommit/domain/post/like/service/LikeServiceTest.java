package com.scommit.domain.post.like.service;

import com.scommit.domain.post.like.entity.Like;
import com.scommit.domain.post.like.repository.LikeRepository;
import com.scommit.domain.post.post.entity.Post;
import com.scommit.domain.post.post.entity.PostAccessLevel;
import com.scommit.domain.post.post.entity.PublishStatus;
import com.scommit.domain.post.post.repository.PostRepository;
import com.scommit.domain.user.user.entity.User;
import com.scommit.domain.user.user.entity.UserRole;
import com.scommit.global.exception.BusinessException;
import com.scommit.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private LikeService likeService;

    private User actor;
    private Post post;

    @BeforeEach
    void setUp() {
        actor = User.builder()
                .email("actor@test.com")
                .nickname("액터")
                .role(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(actor, "id", 1L);

        post = new Post(actor, null, "테스트 게시글", "내용", PublishStatus.PUBLIC, PostAccessLevel.FREE);
        ReflectionTestUtils.setField(post, "id", 10L);
    }

    @Nested
    @DisplayName("좋아요 추가 테스트")
    class CreateLikeTest {

        @Test
        @DisplayName("성공: 좋아요가 추가되고 likeCount가 증가한다")
        void createLike_success() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post));

            // when
            likeService.createLike(10L, actor);

            // then
            verify(likeRepository).save(any(Like.class));
            verify(postRepository).increaseLikeCount(10L);
        }

        @Test
        @DisplayName("실패: 이미 좋아요한 경우 DataIntegrityViolationException이 발생하면 ALREADY_LIKED 예외를 던진다")
        void createLike_alreadyLiked() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post));
            given(likeRepository.save(any(Like.class)))
                    .willThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate entry"));

            // when & then
            assertThatThrownBy(() -> likeService.createLike(10L, actor))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_LIKED);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다")
        void createLike_postNotFound() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> likeService.createLike(999L, actor))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

            verify(likeRepository, never()).save(any());
        }

    }

    @Nested
    @DisplayName("좋아요 취소 테스트")
    class DeleteLikeTest {

        @Test
        @DisplayName("성공: 좋아요가 취소되고 likeCount가 감소한다")
        void deleteLike_success() {
            // given
            ReflectionTestUtils.setField(post, "likeCount", 1L);
            Like like = new Like(post, actor);
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post));
            given(likeRepository.findByPostIdAndUserId(10L, 1L)).willReturn(Optional.of(like));

            // when
            likeService.deleteLike(10L, actor);

            // then
            verify(likeRepository).delete(like);
            verify(postRepository).decreaseLikeCount(10L);
        }

        @Test
        @DisplayName("실패: 좋아요가 없는 경우 LIKE_NOT_FOUND 예외를 던진다")
        void deleteLike_likeNotFound() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post));
            given(likeRepository.findByPostIdAndUserId(10L, 1L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> likeService.deleteLike(10L, actor))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LIKE_NOT_FOUND);

            verify(likeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다")
        void deleteLike_postNotFound() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> likeService.deleteLike(999L, actor))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

            verify(likeRepository, never()).delete(any());
        }

    }
}
