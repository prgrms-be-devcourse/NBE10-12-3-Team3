package com.scommit.domain.post.bookmark.service;

import com.scommit.domain.post.bookmark.entity.Bookmark;
import com.scommit.domain.post.bookmark.repository.BookmarkRepository;
import com.scommit.domain.post.like.repository.LikeRepository;
import com.scommit.domain.post.post.dto.PostListResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private BookmarkService bookmarkService;

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
    @DisplayName("북마크 추가 테스트")
    class CreateBookmarkTest {

        @Test
        @DisplayName("성공: 북마크가 추가되고 bookmarkCount가 증가한다")
        void createBookmark_success() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(post);

            // when
            bookmarkService.createBookmark(10L, actor);

            // then
            verify(bookmarkRepository).save(any(Bookmark.class));
            verify(postRepository).increaseBookmarkCount(10L);
        }

        @Test
        @DisplayName("실패: 이미 북마크한 경우 DataIntegrityViolationException이 발생하면 ALREADY_BOOKMARKED 예외를 던진다")
        void createBookmark_alreadyBookmarked() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(post);
            given(bookmarkRepository.save(any(Bookmark.class)))
                    .willThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate entry"));

            // when & then
            assertThatThrownBy(() -> bookmarkService.createBookmark(10L, actor))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_BOOKMARKED);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다")
        void createBookmark_postNotFound() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> bookmarkService.createBookmark(999L, actor))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

            verify(bookmarkRepository, never()).save(any());
        }

    }

    @Nested
    @DisplayName("북마크 취소 테스트")
    class DeleteBookmarkTest {

        @Test
        @DisplayName("성공: 북마크가 취소되고 bookmarkCount가 감소한다")
        void deleteBookmark_success() {
            // given
            ReflectionTestUtils.setField(post, "bookmarkCount", 1L);
            Bookmark bookmark = new Bookmark(post, actor);
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(post);
            given(bookmarkRepository.findByPostIdAndUserId(10L, 1L)).willReturn(Optional.of(bookmark));

            // when
            bookmarkService.deleteBookmark(10L, actor);

            // then
            verify(bookmarkRepository).delete(bookmark);
            verify(postRepository).decreaseBookmarkCount(10L);
        }

        @Test
        @DisplayName("실패: 북마크가 없는 경우 BOOKMARK_NOT_FOUND 예외를 던진다")
        void deleteBookmark_bookmarkNotFound() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(post);
            given(bookmarkRepository.findByPostIdAndUserId(10L, 1L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> bookmarkService.deleteBookmark(10L, actor))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOKMARK_NOT_FOUND);

            verify(bookmarkRepository, never()).delete(any());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다")
        void deleteBookmark_postNotFound() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> bookmarkService.deleteBookmark(999L, actor))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

            verify(bookmarkRepository, never()).delete(any());
        }

    }

    @Nested
    @DisplayName("내 북마크 목록 조회 테스트")
    class GetMyBookmarksTest {

        @Test
        @DisplayName("성공: 삭제되지 않은 게시글의 북마크 목록을 반환한다")
        void getMyBookmarks_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Bookmark bookmark = new Bookmark(post, actor);
            Page<Bookmark> page = new PageImpl<>(List.of(bookmark));
            given(bookmarkRepository.findByUserIdAndPostDeletedAtIsNull(1L, pageable)).willReturn(page);

            // when
            Page<PostListResponse> result = bookmarkService.getMyBookmarks(actor, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).id()).isEqualTo(10L);
        }

        @Test
        @DisplayName("성공: 북마크가 없으면 빈 페이지를 반환한다")
        void getMyBookmarks_empty() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Bookmark> emptyPage = new PageImpl<>(List.of());
            given(bookmarkRepository.findByUserIdAndPostDeletedAtIsNull(1L, pageable)).willReturn(emptyPage);

            // when
            Page<PostListResponse> result = bookmarkService.getMyBookmarks(actor, pageable);

            // then
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("성공: 북마크한 게시글의 isBookmarked는 항상 true를 반환한다")
        void getMyBookmarks_isBookmarked_alwaysTrue() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Bookmark bookmark = new Bookmark(post, actor);
            Page<Bookmark> page = new PageImpl<>(List.of(bookmark));
            given(bookmarkRepository.findByUserIdAndPostDeletedAtIsNull(1L, pageable)).willReturn(page);

            // when
            Page<PostListResponse> result = bookmarkService.getMyBookmarks(actor, pageable);

            // then
            assertThat(result.getContent().get(0).isBookmarked()).isTrue();
        }

        @Test
        @DisplayName("성공: 북마크한 게시글을 좋아요도 했으면 isLiked=true를 반환한다")
        void getMyBookmarks_isLiked_true() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Bookmark bookmark = new Bookmark(post, actor);
            Page<Bookmark> page = new PageImpl<>(List.of(bookmark));
            given(bookmarkRepository.findByUserIdAndPostDeletedAtIsNull(1L, pageable)).willReturn(page);
            given(likeRepository.existsByPostIdAndUserId(10L, 1L)).willReturn(true);

            // when
            Page<PostListResponse> result = bookmarkService.getMyBookmarks(actor, pageable);

            // then
            assertThat(result.getContent().get(0).isLiked()).isTrue();
        }

        @Test
        @DisplayName("성공: 북마크한 게시글을 좋아요하지 않았으면 isLiked=false를 반환한다")
        void getMyBookmarks_isLiked_false() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Bookmark bookmark = new Bookmark(post, actor);
            Page<Bookmark> page = new PageImpl<>(List.of(bookmark));
            given(bookmarkRepository.findByUserIdAndPostDeletedAtIsNull(1L, pageable)).willReturn(page);
            given(likeRepository.existsByPostIdAndUserId(10L, 1L)).willReturn(false);

            // when
            Page<PostListResponse> result = bookmarkService.getMyBookmarks(actor, pageable);

            // then
            assertThat(result.getContent().get(0).isLiked()).isFalse();
        }
    }

}
