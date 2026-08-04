package com.scommit.domain.post.bookmark.service

import com.scommit.domain.post.bookmark.entity.Bookmark
import com.scommit.domain.post.bookmark.repository.BookmarkRepository
import com.scommit.domain.post.like.repository.LikeRepository
import com.scommit.domain.post.post.dto.PostListResponse
import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
class BookmarkServiceTest {
    @Mock
    private lateinit var bookmarkRepository: BookmarkRepository

    @Mock
    private lateinit var postRepository: PostRepository

    @Mock
    private lateinit var likeRepository: LikeRepository

    @InjectMocks
    private lateinit var bookmarkService: BookmarkService

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
            Post(
                user = actor,
                series = null,
                title = "테스트 게시글",
                body = "내용",
                publishStatus = PublishStatus.PUBLIC,
                accessLevel = PostAccessLevel.FREE,
            ).also { ReflectionTestUtils.setField(it, "id", 10L) }
    }

    @Nested
    @DisplayName("북마크 추가 테스트")
    inner class CreateBookmarkTest {
        @Test
        @DisplayName("성공: 북마크가 추가되고 bookmarkCount가 증가한다")
        fun createBookmark_success() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(post)

            // when
            bookmarkService.createBookmark(10L, actor)

            // then
            verify(bookmarkRepository).save(any(Bookmark::class.java))
            verify(postRepository).increaseBookmarkCount(10L)
        }

        @Test
        @DisplayName("실패: 이미 북마크한 경우 DataIntegrityViolationException이 발생하면 ALREADY_BOOKMARKED 예외를 던진다")
        fun createBookmark_alreadyBookmarked() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(post)
            given(bookmarkRepository.save(any(Bookmark::class.java)))
                .willThrow(DataIntegrityViolationException("Duplicate entry"))

            // when & then
            assertThatThrownBy { bookmarkService.createBookmark(10L, actor) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_BOOKMARKED)
        }

        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다")
        fun createBookmark_postNotFound() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null)

            // when & then
            assertThatThrownBy { bookmarkService.createBookmark(999L, actor) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND)

            verify(bookmarkRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("북마크 취소 테스트")
    inner class DeleteBookmarkTest {
        @Test
        @DisplayName("성공: 북마크가 취소되고 bookmarkCount가 감소한다")
        fun deleteBookmark_success() {
            // given
            ReflectionTestUtils.setField(post, "bookmarkCount", 1L)
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(post)
            given(bookmarkRepository.deleteByPostIdAndUserId(10L, 1L)).willReturn(1)

            // when
            bookmarkService.deleteBookmark(10L, actor)

            // then
            verify(bookmarkRepository).deleteByPostIdAndUserId(10L, 1L)
            verify(postRepository).decreaseBookmarkCount(10L)
        }

        @Test
        @DisplayName("실패: 북마크가 없는 경우 BOOKMARK_NOT_FOUND 예외를 던지고 bookmarkCount를 건드리지 않는다")
        fun deleteBookmark_bookmarkNotFound() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(post)
            given(bookmarkRepository.deleteByPostIdAndUserId(10L, 1L)).willReturn(0)

            // when & then
            assertThatThrownBy { bookmarkService.deleteBookmark(10L, actor) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOKMARK_NOT_FOUND)

            verify(postRepository, never()).decreaseBookmarkCount(anyLong())
        }

        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다")
        fun deleteBookmark_postNotFound() {
            // given
            given(postRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null)

            // when & then
            assertThatThrownBy { bookmarkService.deleteBookmark(999L, actor) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND)

            verify(bookmarkRepository, never()).deleteByPostIdAndUserId(anyLong(), anyLong())
        }
    }

    @Nested
    @DisplayName("내 북마크 목록 조회 테스트")
    inner class GetMyBookmarksTest {
        @Test
        @DisplayName("성공: 삭제되지 않은 게시글의 북마크 목록을 반환한다")
        fun getMyBookmarks_success() {
            // given
            val pageable: Pageable = PageRequest.of(0, 10)
            val bookmark = Bookmark(post = post, user = actor)
            val page: Page<Bookmark> = PageImpl(listOf(bookmark))
            given(bookmarkRepository.findByUserIdAndPostDeletedAtIsNull(1L, pageable)).willReturn(page)

            // when
            val result: Page<PostListResponse> = bookmarkService.getMyBookmarks(actor, pageable)

            // then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].id).isEqualTo(10L)
        }

        @Test
        @DisplayName("성공: 북마크가 없으면 빈 페이지를 반환한다")
        fun getMyBookmarks_empty() {
            // given
            val pageable: Pageable = PageRequest.of(0, 10)
            val emptyPage: Page<Bookmark> = PageImpl(emptyList())
            given(bookmarkRepository.findByUserIdAndPostDeletedAtIsNull(1L, pageable)).willReturn(emptyPage)

            // when
            val result: Page<PostListResponse> = bookmarkService.getMyBookmarks(actor, pageable)

            // then
            assertThat(result.content).isEmpty()
        }

        @Test
        @DisplayName("성공: 북마크한 게시글의 isBookmarked는 항상 true를 반환한다")
        fun getMyBookmarks_isBookmarked_alwaysTrue() {
            // given
            val pageable: Pageable = PageRequest.of(0, 10)
            val bookmark = Bookmark(post = post, user = actor)
            val page: Page<Bookmark> = PageImpl(listOf(bookmark))
            given(bookmarkRepository.findByUserIdAndPostDeletedAtIsNull(1L, pageable)).willReturn(page)

            // when
            val result: Page<PostListResponse> = bookmarkService.getMyBookmarks(actor, pageable)

            // then
            assertThat(result.content[0].isBookmarked).isTrue()
        }

        @Test
        @DisplayName("성공: 북마크한 게시글을 좋아요도 했으면 isLiked=true를 반환한다")
        fun getMyBookmarks_isLiked_true() {
            // given
            val pageable: Pageable = PageRequest.of(0, 10)
            val bookmark = Bookmark(post = post, user = actor)
            val page: Page<Bookmark> = PageImpl(listOf(bookmark))
            given(bookmarkRepository.findByUserIdAndPostDeletedAtIsNull(1L, pageable)).willReturn(page)
            given(likeRepository.existsByPostIdAndUserId(10L, 1L)).willReturn(true)

            // when
            val result: Page<PostListResponse> = bookmarkService.getMyBookmarks(actor, pageable)

            // then
            assertThat(result.content[0].isLiked).isTrue()
        }

        @Test
        @DisplayName("성공: 북마크한 게시글을 좋아요하지 않았으면 isLiked=false를 반환한다")
        fun getMyBookmarks_isLiked_false() {
            // given
            val pageable: Pageable = PageRequest.of(0, 10)
            val bookmark = Bookmark(post = post, user = actor)
            val page: Page<Bookmark> = PageImpl(listOf(bookmark))
            given(bookmarkRepository.findByUserIdAndPostDeletedAtIsNull(1L, pageable)).willReturn(page)
            given(likeRepository.existsByPostIdAndUserId(10L, 1L)).willReturn(false)

            // when
            val result: Page<PostListResponse> = bookmarkService.getMyBookmarks(actor, pageable)

            // then
            assertThat(result.content[0].isLiked).isFalse()
        }
    }
}
