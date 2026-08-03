package com.scommit.domain.post.post.service

import com.scommit.domain.notification.notification.repository.SseEmitterRepository
import com.scommit.domain.post.bookmark.repository.BookmarkRepository
import com.scommit.domain.post.like.repository.LikeRepository
import com.scommit.domain.post.post.dto.PostListResponse
import com.scommit.domain.post.post.dto.PostResponse
import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.series.series.entity.Series
import com.scommit.domain.series.series.repository.SeriesRepository
import com.scommit.domain.subscription.subscription.entity.Subscription
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.repository.UserRepository
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
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

/**
 * PostService 단위 테스트
 * - DB, Spring Context 없이 Mockito로 의존성을 가짜(Mock)로 대체
 * - postRepository, seriesRepository, userRepository의 반환값을 미리 지정(given/willReturn)하고
 *   실제 서비스 로직만 검증
 */
@ExtendWith(MockitoExtension::class)
class PostServiceTest {
    @Mock
    private lateinit var postRepository: PostRepository

    @Mock
    private lateinit var seriesRepository: SeriesRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var subscriptionRepository: SubscriptionRepository

    @Mock
    private lateinit var sseEmitterRepository: SseEmitterRepository

    @Mock
    private lateinit var likeRepository: LikeRepository

    @Mock
    private lateinit var bookmarkRepository: BookmarkRepository

    @InjectMocks
    private lateinit var postService: PostService

    // 테스트에 사용할 유저 2명 (본인 / 타인 구분용)
    private lateinit var mockUser: User
    private lateinit var otherUser: User

    @BeforeEach
    fun setUp() {
        // JPA가 없으므로 id는 ReflectionTestUtils로 직접 주입
        mockUser =
            User
                .builder()
                .email("test@example.com")
                .nickname("테스터")
                .role(UserRole.USER)
                .build()
                .also { ReflectionTestUtils.setField(it, "id", 1L) }

        otherUser =
            User
                .builder()
                .email("other@example.com")
                .nickname("다른유저")
                .role(UserRole.USER)
                .build()
                .also { ReflectionTestUtils.setField(it, "id", 2L) }
    }

    // 테스트용 Post 생성 헬퍼 - 매 테스트마다 반복 코드를 줄이기 위해 사용
    private fun buildPost(
        id: Long,
        user: User,
        series: Series?,
    ): Post =
        Post(user, series, "테스트 포스트", "내용", PublishStatus.PUBLIC, PostAccessLevel.FREE)
            .also { ReflectionTestUtils.setField(it, "id", id) }

    private fun buildSeries(
        id: Long,
        owner: User,
    ): Series =
        Series
            .builder()
            .user(owner)
            .title("시리즈")
            .body("설명")
            .build()
            .also { ReflectionTestUtils.setField(it, "id", id) }

    @Nested
    @DisplayName("게시글 검색 테스트")
    inner class SearchPosts {
        @Test
        @DisplayName("성공: 키워드가 제목에 포함된 PUBLIC 게시글을 반환한다.")
        fun searchPosts_Success() {
            val pageable: Pageable = PageRequest.of(0, 20)
            val post = buildPost(1L, mockUser, null)
            val postPage: Page<Post> = PageImpl(listOf(post), pageable, 1)

            given(postRepository.searchByKeyword("테스트", PublishStatus.PUBLIC, pageable)).willReturn(postPage)

            val result: Page<PostListResponse> = postService.searchPosts("테스트", pageable)

            assertThat(result.totalElements).isEqualTo(1)
            assertThat(result.content[0].title).isEqualTo("테스트 포스트")
        }

        @Test
        @DisplayName("성공: 검색 결과가 없으면 빈 페이지를 반환한다.")
        fun searchPosts_Empty() {
            val pageable: Pageable = PageRequest.of(0, 20)
            val emptyPage: Page<Post> = PageImpl(emptyList(), pageable, 0)

            given(postRepository.searchByKeyword("없는키워드", PublishStatus.PUBLIC, pageable)).willReturn(emptyPage)

            val result = postService.searchPosts("없는키워드", pageable)

            assertThat(result.totalElements).isEqualTo(0)
            assertThat(result.content).isEmpty()
        }
    }

    @Nested
    @DisplayName("게시글 목록 조회 테스트")
    inner class GetPosts {
        // creatorId 없이 전체 조회 시 PUBLIC 게시글만 반환해야 함
        @Test
        @DisplayName("성공: creatorId 없이 조회하면 PUBLIC 게시글 목록을 반환한다.")
        fun getPosts_All_OnlyPublic() {
            val pageable: Pageable = PageRequest.of(0, 8)
            val post = buildPost(1L, mockUser, null)
            val slice = SliceImpl(listOf(post), pageable, false)

            given(postRepository.findAllByDeletedAtIsNullAndPublishStatus(PublishStatus.PUBLIC, pageable))
                .willReturn(slice)

            val result = postService.getPosts(null, null, pageable)

            assertThat(result.content).hasSize(1)
            verify(postRepository).findAllByDeletedAtIsNullAndPublishStatus(PublishStatus.PUBLIC, pageable)
        }

        // creatorId 지정 시 해당 유저의 게시글만 조회
        @Test
        @DisplayName("성공: creatorId를 지정하면 해당 유저의 게시글 목록을 반환한다.")
        fun getPosts_ByCreator() {
            val pageable: Pageable = PageRequest.of(0, 8)
            val post = buildPost(1L, mockUser, null)
            val slice = SliceImpl(listOf(post), pageable, false)

            given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mockUser))
            given(postRepository.findSliceByUserAndDeletedAtIsNull(mockUser, pageable)).willReturn(slice)

            val result = postService.getPosts(1L, null, pageable)

            assertThat(result.content).hasSize(1)
        }

        // 존재하지 않는 creatorId로 조회 시 예외
        @Test
        @DisplayName("실패: 존재하지 않는 creatorId면 USER_NOT_FOUND 예외를 던진다.")
        fun getPosts_CreatorNotFound() {
            val pageable: Pageable = PageRequest.of(0, 8)
            given(userRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty())

            assertThatThrownBy { postService.getPosts(999L, null, pageable) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND)
        }

        @Test
        @DisplayName("성공: 로그인한 유저가 좋아요한 게시글이면 isLiked=true를 반환한다.")
        fun getPosts_withActor_isLiked() {
            val pageable: Pageable = PageRequest.of(0, 8)
            val post = buildPost(1L, otherUser, null)
            val slice = SliceImpl(listOf(post), pageable, false)

            given(postRepository.findAllByDeletedAtIsNullAndPublishStatus(PublishStatus.PUBLIC, pageable))
                .willReturn(slice)
            given(likeRepository.existsByPostIdAndUserId(1L, mockUser.id)).willReturn(true)

            val result = postService.getPosts(null, mockUser, pageable)

            assertThat(result.content[0].isLiked).isTrue()
        }

        @Test
        @DisplayName("성공: 로그인한 유저가 북마크한 게시글이면 isBookmarked=true를 반환한다.")
        fun getPosts_withActor_isBookmarked() {
            val pageable: Pageable = PageRequest.of(0, 8)
            val post = buildPost(1L, otherUser, null)
            val slice = SliceImpl(listOf(post), pageable, false)

            given(postRepository.findAllByDeletedAtIsNullAndPublishStatus(PublishStatus.PUBLIC, pageable))
                .willReturn(slice)
            given(bookmarkRepository.existsByPostIdAndUserId(1L, mockUser.id)).willReturn(true)

            val result = postService.getPosts(null, mockUser, pageable)

            assertThat(result.content[0].isBookmarked).isTrue()
        }

        @Test
        @DisplayName("성공: 비로그인 사용자는 isLiked=false, isBookmarked=false를 반환한다.")
        fun getPosts_anonymous_isLikedAndBookmarkedFalse() {
            val pageable: Pageable = PageRequest.of(0, 8)
            val post = buildPost(1L, otherUser, null)
            val slice = SliceImpl(listOf(post), pageable, false)

            given(postRepository.findAllByDeletedAtIsNullAndPublishStatus(PublishStatus.PUBLIC, pageable))
                .willReturn(slice)

            val result = postService.getPosts(null, null, pageable)

            assertThat(result.content[0].isLiked).isFalse()
            assertThat(result.content[0].isBookmarked).isFalse()
            verify(likeRepository, never()).existsByPostIdAndUserId(any(), any())
            verify(bookmarkRepository, never()).existsByPostIdAndUserId(any(), any())
        }
    }

    @Nested
    @DisplayName("유저 게시글 목록 조회 테스트 (번호 페이지네이션)")
    inner class GetUserPosts {
        // 프로필 화면에서 특정 유저의 게시글을 페이지 번호 방식으로 조회
        @Test
        @DisplayName("성공: 특정 유저의 게시글 목록을 페이지로 반환한다.")
        fun getUserPosts_Success() {
            val pageable: Pageable = PageRequest.of(0, 10)
            val post = buildPost(1L, mockUser, null)
            val postPage: Page<Post> = PageImpl(listOf(post), pageable, 1)

            given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mockUser))
            given(postRepository.findByUserAndDeletedAtIsNull(mockUser, pageable)).willReturn(postPage)

            val result: Page<PostListResponse> = postService.getUserPosts(1L, null, pageable)

            assertThat(result.totalElements).isEqualTo(1)
            assertThat(result.content[0].title).isEqualTo("테스트 포스트")
        }

        // 존재하지 않는 유저 조회 시 예외
        @Test
        @DisplayName("실패: 존재하지 않는 유저면 USER_NOT_FOUND 예외를 던진다.")
        fun getUserPosts_UserNotFound() {
            val pageable: Pageable = PageRequest.of(0, 10)
            given(userRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty())

            assertThatThrownBy { postService.getUserPosts(999L, null, pageable) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND)
        }

        // 게시글이 없는 유저도 빈 페이지를 반환해야 함
        @Test
        @DisplayName("성공: 게시글이 없는 유저면 빈 페이지를 반환한다.")
        fun getUserPosts_Empty() {
            val pageable: Pageable = PageRequest.of(0, 10)
            val emptyPage: Page<Post> = PageImpl(emptyList(), pageable, 0)

            given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mockUser))
            given(postRepository.findByUserAndDeletedAtIsNull(mockUser, pageable)).willReturn(emptyPage)

            val result = postService.getUserPosts(1L, null, pageable)

            assertThat(result.totalElements).isEqualTo(0)
            assertThat(result.content).isEmpty()
        }

        @Test
        @DisplayName("성공: 로그인한 유저가 좋아요한 게시글이면 isLiked=true를 반환한다.")
        fun getUserPosts_withActor_isLiked() {
            val pageable: Pageable = PageRequest.of(0, 10)
            val post = buildPost(1L, mockUser, null)
            val postPage: Page<Post> = PageImpl(listOf(post), pageable, 1)

            given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mockUser))
            given(postRepository.findByUserAndDeletedAtIsNull(mockUser, pageable)).willReturn(postPage)
            given(likeRepository.existsByPostIdAndUserId(1L, otherUser.id)).willReturn(true)

            val result = postService.getUserPosts(1L, otherUser, pageable)

            assertThat(result.content[0].isLiked).isTrue()
        }
    }

    @Nested
    @DisplayName("게시글 생성 테스트")
    inner class CreatePost {
        // seriesId가 null이면 시리즈 조회를 아예 하지 않아야 함
        @Test
        @DisplayName("성공: 시리즈 없이 게시글을 생성한다.")
        fun create_Success_NoSeries() {
            val saved = buildPost(1L, mockUser, null)
            given(postRepository.save(any(Post::class.java))).willReturn(saved)

            val response =
                postService.createPost(mockUser, "제목", "내용", PublishStatus.PUBLIC, PostAccessLevel.FREE, null)

            assertThat(response.userId).isEqualTo(mockUser.id)
            // seriesId가 null이면 seriesRepository를 호출하지 않는지 검증
            verify(seriesRepository, never()).findById(any())
        }

        // seriesId가 있으면 시리즈를 조회해서 게시글에 연결해야 함
        @Test
        @DisplayName("성공: 존재하는 시리즈와 함께 게시글을 생성한다.")
        fun create_Success_WithSeries() {
            val series = buildSeries(5L, mockUser)
            val saved = buildPost(1L, mockUser, series)
            given(seriesRepository.findById(5L)).willReturn(Optional.of(series))
            given(postRepository.save(any(Post::class.java))).willReturn(saved)

            val response =
                postService.createPost(mockUser, "제목", "내용", PublishStatus.PUBLIC, PostAccessLevel.FREE, 5L)

            assertThat(response.seriesId).isEqualTo(5L)
        }

        @Test
        @DisplayName("성공: PUBLIC 게시글 생성 시 구독자에게 SSE 알림을 전송한다.")
        fun create_Public_SendsSse() {
            val sub =
                Subscription
                    .builder()
                    .user(otherUser)
                    .creator(mockUser)
                    .tier(SubscriptionTier.FOLLOW)
                    .build()
            given(subscriptionRepository.findByCreatorIdAndDeletedAtIsNull(1L)).willReturn(listOf(sub))

            postService.createPost(mockUser, "제목", "내용", PublishStatus.PUBLIC, PostAccessLevel.FREE, null)

            verify(sseEmitterRepository).sendToUser(eq(2L), any())
        }

        @Test
        @DisplayName("성공: PAID 게시글 생성 시 멤버십 구독자에게만 SSE 알림을 전송한다.")
        fun create_Paid_SendsSseOnlyToMembers() {
            val member =
                Subscription
                    .builder()
                    .user(otherUser)
                    .creator(mockUser)
                    .tier(SubscriptionTier.MEMBERSHIP)
                    .build()
            given(subscriptionRepository.findByCreatorIdAndTierAndDeletedAtIsNull(1L, SubscriptionTier.MEMBERSHIP))
                .willReturn(listOf(member))

            postService.createPost(mockUser, "제목", "내용", PublishStatus.PUBLIC, PostAccessLevel.PAID, null)

            verify(sseEmitterRepository).sendToUser(eq(2L), any())
            verify(subscriptionRepository, never()).findByCreatorIdAndDeletedAtIsNull(any())
        }

        @Test
        @DisplayName("성공: DRAFT 게시글 생성 시 SSE 알림을 전송하지 않는다.")
        fun create_Draft_NoSse() {
            postService.createPost(mockUser, "제목", "내용", PublishStatus.DRAFT, PostAccessLevel.FREE, null)

            verify(sseEmitterRepository, never()).sendToUser(any(), any())
        }

        // 없는 시리즈 ID를 넘기면 저장 전에 예외가 발생해야 함
        @Test
        @DisplayName("실패: 존재하지 않는 시리즈 ID면 SERIES_NOT_FOUND 예외를 던진다.")
        fun create_SeriesNotFound() {
            given(seriesRepository.findById(999L)).willReturn(Optional.empty())

            assertThatThrownBy {
                postService.createPost(mockUser, "제목", "내용", PublishStatus.PUBLIC, PostAccessLevel.FREE, 999L)
            }.isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("게시글 수정 테스트")
    inner class UpdatePost {
        // 본인 게시글 수정 → 제목/내용이 실제로 바뀌는지 확인
        @Test
        @DisplayName("성공: 본인 게시글을 수정한다.")
        fun update_Success() {
            val post = buildPost(1L, mockUser, null)
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post))

            val response =
                postService.updatePost(mockUser, 1L, "수정제목", "수정내용", PublishStatus.DRAFT, PostAccessLevel.FREE, null)

            assertThat(response.title).isEqualTo("수정제목")
        }

        @Test
        @DisplayName("성공: DRAFT→PUBLIC 전환 시 구독자에게 SSE 알림을 전송한다.")
        fun update_DraftToPublic_SendsSse() {
            val post = buildPost(1L, mockUser, null)
            ReflectionTestUtils.setField(post, "publishStatus", PublishStatus.DRAFT)
            val sub =
                Subscription
                    .builder()
                    .user(otherUser)
                    .creator(mockUser)
                    .tier(SubscriptionTier.FOLLOW)
                    .build()
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post))
            given(subscriptionRepository.findByCreatorIdAndDeletedAtIsNull(1L)).willReturn(listOf(sub))

            postService.updatePost(mockUser, 1L, "제목", "내용", PublishStatus.PUBLIC, PostAccessLevel.FREE, null)

            verify(sseEmitterRepository).sendToUser(eq(2L), any())
        }

        @Test
        @DisplayName("성공: 이미 PUBLIC인 게시글 수정 시 SSE 알림을 다시 전송하지 않는다.")
        fun update_AlreadyPublic_NoSse() {
            val post = buildPost(1L, mockUser, null)
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post))

            postService.updatePost(mockUser, 1L, "수정제목", "수정내용", PublishStatus.PUBLIC, PostAccessLevel.FREE, null)

            verify(sseEmitterRepository, never()).sendToUser(any(), any())
        }

        // 없는 게시글 ID → 조회 시점에 예외 발생
        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다.")
        fun update_PostNotFound() {
            given(postRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty())

            assertThatThrownBy {
                postService.updatePost(mockUser, 999L, "제목", "내용", PublishStatus.PUBLIC, PostAccessLevel.FREE, null)
            }.isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND)
        }

        // 타인의 게시글 수정 시도 → 본인 확인 로직에서 차단
        @Test
        @DisplayName("실패: 다른 유저의 게시글을 수정하면 ACCESS_DENIED 예외를 던진다.")
        fun update_NotOwner() {
            val post = buildPost(1L, otherUser, null)
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post))

            assertThatThrownBy {
                postService.updatePost(mockUser, 1L, "제목", "내용", PublishStatus.PUBLIC, PostAccessLevel.FREE, null)
            }.isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }
    }

    @Nested
    @DisplayName("게시글 삭제 테스트")
    inner class DeletePost {
        // softDelete 방식이므로 실제로 행이 지워지는 게 아니라 deletedAt이 채워져야 함
        @Test
        @DisplayName("성공: 본인 게시글을 삭제한다.")
        fun delete_Success() {
            val post = buildPost(1L, mockUser, null)
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post))

            postService.deletePost(mockUser, 1L)

            assertThat(post.deletedAt).isNotNull()
        }

        // 없는 게시글 삭제 시도
        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다.")
        fun delete_PostNotFound() {
            given(postRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty())

            assertThatThrownBy { postService.deletePost(mockUser, 999L) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND)
        }

        // 타인 게시글 삭제 시도 → 본인 확인 로직에서 차단
        @Test
        @DisplayName("실패: 다른 유저의 게시글을 삭제하면 ACCESS_DENIED 예외를 던진다.")
        fun delete_NotOwner() {
            val post = buildPost(1L, otherUser, null)
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post))

            assertThatThrownBy { postService.deletePost(mockUser, 1L) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }
    }

    @Nested
    @DisplayName("게시글 상세 조회 테스트")
    inner class GetPost {
        // 조회할 때마다 viewCount가 1씩 올라야 함 (더티체킹 방식)
        @Test
        @DisplayName("성공: 게시글 조회 시 조회수가 1 증가한다.")
        fun getPost_Success_ViewCountIncreased() {
            val post = buildPost(1L, mockUser, null)
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post))

            postService.getPost(1L, mockUser)

            assertThat(post.viewCount).isEqualTo(1L)
        }

        // 없는 게시글 조회
        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다.")
        fun getPost_NotFound() {
            given(postRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty())

            assertThatThrownBy { postService.getPost(999L, null) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND)
        }

        // PRIVATE 게시글은 작성자 본인만 조회 가능
        @Test
        @DisplayName("실패: PRIVATE 게시글을 타인이 조회하면 ACCESS_DENIED 예외를 던진다.")
        fun getPost_Private_NotOwner() {
            val post = buildPost(1L, mockUser, null)
            ReflectionTestUtils.setField(post, "publishStatus", PublishStatus.PRIVATE)
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post))

            assertThatThrownBy { postService.getPost(1L, otherUser) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }

        // PRIVATE 게시글은 비로그인 사용자도 조회 불가
        @Test
        @DisplayName("실패: PRIVATE 게시글을 비로그인 사용자가 조회하면 ACCESS_DENIED 예외를 던진다.")
        fun getPost_Private_Anonymous() {
            val post = buildPost(1L, mockUser, null)
            ReflectionTestUtils.setField(post, "publishStatus", PublishStatus.PRIVATE)
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post))

            assertThatThrownBy { postService.getPost(1L, null) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }

        // PAID 게시글을 멤버십 비구독자가 조회하면 본문이 잠긴 상태로 반환
        @Test
        @DisplayName("성공: PAID 게시글을 비구독자가 조회하면 isLocked=true로 반환한다.")
        fun getPost_Paid_NotMember_IsLocked() {
            val post = buildPost(1L, mockUser, null)
            ReflectionTestUtils.setField(post, "accessLevel", PostAccessLevel.PAID)
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post))
            given(subscriptionRepository.findByUserIdAndCreatorId(otherUser.id, mockUser.id))
                .willReturn(Optional.empty())

            val response: PostResponse = postService.getPost(1L, otherUser)

            assertThat(response.isLocked).isTrue()
            assertThat(response.body).isNull()
        }

        @Test
        @DisplayName("성공: 로그인한 유저가 좋아요한 게시글 조회 시 isLiked=true를 반환한다.")
        fun getPost_withActor_isLiked() {
            val post = buildPost(1L, otherUser, null)
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post))
            given(likeRepository.existsByPostIdAndUserId(1L, mockUser.id)).willReturn(true)

            val response = postService.getPost(1L, mockUser)

            assertThat(response.isLiked).isTrue()
            assertThat(response.isBookmarked).isFalse()
        }

        @Test
        @DisplayName("성공: 로그인한 유저가 북마크한 게시글 조회 시 isBookmarked=true를 반환한다.")
        fun getPost_withActor_isBookmarked() {
            val post = buildPost(1L, otherUser, null)
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post))
            given(bookmarkRepository.existsByPostIdAndUserId(1L, mockUser.id)).willReturn(true)

            val response = postService.getPost(1L, mockUser)

            assertThat(response.isBookmarked).isTrue()
            assertThat(response.isLiked).isFalse()
        }

        @Test
        @DisplayName("성공: 비로그인 사용자가 조회하면 isLiked=false, isBookmarked=false를 반환한다.")
        fun getPost_anonymous_isLikedAndBookmarkedFalse() {
            val post = buildPost(1L, mockUser, null)
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post))

            val response = postService.getPost(1L, null)

            assertThat(response.isLiked).isFalse()
            assertThat(response.isBookmarked).isFalse()
            verify(likeRepository, never()).existsByPostIdAndUserId(any(), any())
            verify(bookmarkRepository, never()).existsByPostIdAndUserId(any(), any())
        }
    }

    @Nested
    @DisplayName("시리즈에 포스트 추가 테스트")
    inner class AddPostToSeries {
        @Test
        @DisplayName("성공: 포스트와 시리즈 모두 본인 소유일 때 추가된다.")
        fun add_Success() {
            val series = buildSeries(5L, mockUser)
            val post = buildPost(10L, mockUser, null)

            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post))
            given(seriesRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(series))

            postService.addPostToSeries(10L, 5L, mockUser)

            assertThat(post.series).isEqualTo(series)
        }

        // 포스트 조회 실패 시 시리즈 조회는 아예 하지 않아야 함 (불필요한 DB 호출 방지)
        @Test
        @DisplayName("실패: 존재하지 않는 포스트면 POST_NOT_FOUND 예외를 던진다.")
        fun add_PostNotFound() {
            given(postRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty())

            assertThatThrownBy { postService.addPostToSeries(999L, 5L, mockUser) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND)

            verify(seriesRepository, never()).findByIdAndDeletedAtIsNull(any())
        }

        // 포스트 주인이 아니면 시리즈 조회 전에 차단해야 함
        @Test
        @DisplayName("실패: 포스트 주인이 아니면 ACCESS_DENIED 예외를 던진다.")
        fun add_PostNotOwned() {
            val post = buildPost(10L, otherUser, null)
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post))

            assertThatThrownBy { postService.addPostToSeries(10L, 5L, mockUser) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)

            verify(seriesRepository, never()).findByIdAndDeletedAtIsNull(any())
        }

        @Test
        @DisplayName("실패: 존재하지 않는 시리즈면 SERIES_NOT_FOUND 예외를 던진다.")
        fun add_SeriesNotFound() {
            val post = buildPost(10L, mockUser, null)
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post))
            given(seriesRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty())

            assertThatThrownBy { postService.addPostToSeries(10L, 999L, mockUser) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND)
        }

        @Test
        @DisplayName("실패: 시리즈 주인이 아니면 ACCESS_DENIED 예외를 던진다.")
        fun add_SeriesNotOwned() {
            val series = buildSeries(5L, otherUser)
            val post = buildPost(10L, mockUser, null)

            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post))
            given(seriesRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(series))

            assertThatThrownBy { postService.addPostToSeries(10L, 5L, mockUser) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }
    }

    @Nested
    @DisplayName("시리즈에서 포스트 제거 테스트")
    inner class RemovePostFromSeries {
        @Test
        @DisplayName("성공: 포스트가 해당 시리즈에 속하고 시리즈 주인이면 제거된다.")
        fun remove_Success() {
            val series = buildSeries(5L, mockUser)
            val post = buildPost(10L, mockUser, series)

            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post))

            postService.removePostFromSeries(10L, 5L, mockUser)

            assertThat(post.series).isNull()
        }

        @Test
        @DisplayName("실패: 존재하지 않는 포스트면 POST_NOT_FOUND 예외를 던진다.")
        fun remove_PostNotFound() {
            given(postRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty())

            assertThatThrownBy { postService.removePostFromSeries(999L, 5L, mockUser) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND)
        }

        // 시리즈에 속하지 않은 포스트를 특정 시리즈에서 제거하려는 경우
        @Test
        @DisplayName("실패: 포스트가 어떤 시리즈에도 속하지 않으면 SERIES_NOT_FOUND 예외를 던진다.")
        fun remove_PostHasNoSeries() {
            val post = buildPost(10L, mockUser, null)
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post))

            assertThatThrownBy { postService.removePostFromSeries(10L, 5L, mockUser) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND)
        }

        // 다른 시리즈에 속한 포스트를 잘못된 시리즈 ID로 제거 시도
        @Test
        @DisplayName("실패: 포스트가 다른 시리즈에 속하면 SERIES_NOT_FOUND 예외를 던진다.")
        fun remove_PostBelongsToDifferentSeries() {
            val otherSeries = buildSeries(99L, mockUser)
            val post = buildPost(10L, mockUser, otherSeries)
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post))

            assertThatThrownBy { postService.removePostFromSeries(10L, 5L, mockUser) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND)
        }

        @Test
        @DisplayName("실패: 시리즈 주인이 아니면 ACCESS_DENIED 예외를 던진다.")
        fun remove_SeriesNotOwned() {
            val series = buildSeries(5L, otherUser)
            val post = buildPost(10L, mockUser, series)
            given(postRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(post))

            assertThatThrownBy { postService.removePostFromSeries(10L, 5L, mockUser) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }
    }

    @Nested
    @DisplayName("시리즈 내 게시글 목록 조회 테스트")
    inner class GetPostsBySeriesId {
        @Test
        @DisplayName("성공: 시리즈에 속한 게시글 목록을 반환한다.")
        fun getPostsBySeriesId_success() {
            val series = buildSeries(5L, mockUser)
            val post = buildPost(1L, mockUser, series)

            given(postRepository.findBySeriesIdAndDeletedAtIsNull(5L)).willReturn(listOf(post))

            val result = postService.getPostsBySeriesId(5L, null)

            assertThat(result).hasSize(1)
            assertThat(result[0].seriesId).isEqualTo(5L)
        }

        @Test
        @DisplayName("성공: 시리즈 게시글이 없으면 빈 목록을 반환한다.")
        fun getPostsBySeriesId_empty() {
            given(postRepository.findBySeriesIdAndDeletedAtIsNull(5L)).willReturn(emptyList())

            val result = postService.getPostsBySeriesId(5L, null)

            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("성공: 로그인한 유저가 좋아요한 게시글이면 isLiked=true를 반환한다.")
        fun getPostsBySeriesId_withActor_isLiked() {
            val series = buildSeries(5L, otherUser)
            val post = buildPost(1L, otherUser, series)

            given(postRepository.findBySeriesIdAndDeletedAtIsNull(5L)).willReturn(listOf(post))
            given(likeRepository.existsByPostIdAndUserId(1L, mockUser.id)).willReturn(true)

            val result = postService.getPostsBySeriesId(5L, mockUser)

            assertThat(result[0].isLiked).isTrue()
        }

        @Test
        @DisplayName("성공: 비로그인 사용자는 isLiked=false, isBookmarked=false를 반환한다.")
        fun getPostsBySeriesId_anonymous_isLikedAndBookmarkedFalse() {
            val series = buildSeries(5L, mockUser)
            val post = buildPost(1L, mockUser, series)

            given(postRepository.findBySeriesIdAndDeletedAtIsNull(5L)).willReturn(listOf(post))

            val result = postService.getPostsBySeriesId(5L, null)

            assertThat(result[0].isLiked).isFalse()
            assertThat(result[0].isBookmarked).isFalse()
            verify(likeRepository, never()).existsByPostIdAndUserId(any(), any())
            verify(bookmarkRepository, never()).existsByPostIdAndUserId(any(), any())
        }
    }
}
