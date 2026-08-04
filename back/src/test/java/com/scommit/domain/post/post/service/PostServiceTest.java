package com.scommit.domain.post.post.service;

import com.scommit.domain.notification.notification.repository.SseEmitterRepository;
import com.scommit.domain.post.bookmark.repository.BookmarkRepository;
import com.scommit.domain.post.like.repository.LikeRepository;
import com.scommit.domain.post.post.dto.PostListResponse;
import com.scommit.domain.post.post.dto.PostResponse;
import com.scommit.domain.post.post.entity.Post;
import com.scommit.domain.post.post.entity.PostAccessLevel;
import com.scommit.domain.post.post.entity.PublishStatus;
import com.scommit.domain.post.post.repository.PostRepository;
import com.scommit.domain.series.series.entity.Series;
import com.scommit.domain.series.series.repository.SeriesRepository;
import com.scommit.domain.subscription.subscription.entity.Subscription;
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier;
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository;
import com.scommit.domain.user.user.entity.User;
import com.scommit.domain.user.user.entity.UserRole;
import com.scommit.domain.user.user.repository.UserRepository;
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
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * PostService 단위 테스트
 * - DB, Spring Context 없이 Mockito로 의존성을 가짜(Mock)로 대체
 * - postRepository, seriesRepository, userRepository의 반환값을 미리 지정(when/thenReturn)하고
 *   실제 서비스 로직만 검증
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private SeriesRepository seriesRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SseEmitterRepository sseEmitterRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @InjectMocks
    private PostService postService;

    // 테스트에 사용할 유저 2명 (본인 / 타인 구분용)
    private User mockUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        // JPA가 없으므로 id는 ReflectionTestUtils로 직접 주입
        mockUser = User.builder()
                .email("test@example.com")
                .nickname("테스터")
                .role(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(mockUser, "id", 1L);

        otherUser = User.builder()
                .email("other@example.com")
                .nickname("다른유저")
                .role(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(otherUser, "id", 2L);
    }

    // 테스트용 Post 빌더 헬퍼 - 매 테스트마다 반복 코드를 줄이기 위해 사용
    private Post buildPost(Long id, User user, Series series) {
        Post post = Post.builder()
                .user(user)
                .series(series)
                .title("테스트 포스트")
                .body("내용")
                .publishStatus(PublishStatus.PUBLIC)
                .accessLevel(PostAccessLevel.FREE)
                .build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private Series buildSeries(Long id, User owner) {
        Series series = Series.builder()
                .user(owner)
                .title("시리즈")
                .body("설명")
                .build();
        ReflectionTestUtils.setField(series, "id", id);
        return series;
    }

    @Nested
    @DisplayName("게시글 검색 테스트")
    class SearchPosts {

        @Test
        @DisplayName("성공: 키워드가 제목에 포함된 PUBLIC 게시글을 반환한다.")
        void searchPosts_Success() {
            Pageable pageable = PageRequest.of(0, 20);
            Post post = buildPost(1L, mockUser, null);
            Page<Post> postPage = new PageImpl<>(List.of(post), pageable, 1);

            when(postRepository.searchByKeyword("테스트", PublishStatus.PUBLIC, pageable)).thenReturn(postPage);

            Page<PostListResponse> result = postService.searchPosts("테스트", pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).title()).isEqualTo("테스트 포스트");
        }

        @Test
        @DisplayName("성공: 검색 결과가 없으면 빈 페이지를 반환한다.")
        void searchPosts_Empty() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Post> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(postRepository.searchByKeyword("없는키워드", PublishStatus.PUBLIC, pageable)).thenReturn(emptyPage);

            Page<PostListResponse> result = postService.searchPosts("없는키워드", pageable);

            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("게시글 목록 조회 테스트")
    class GetPosts {

        // creatorId 없이 전체 조회 시 PUBLIC 게시글만 반환해야 함
        @Test
        @DisplayName("성공: creatorId 없이 조회하면 PUBLIC 게시글 목록을 반환한다.")
        void getPosts_All_OnlyPublic() {
            Pageable pageable = PageRequest.of(0, 8);
            Post post = buildPost(1L, mockUser, null);
            SliceImpl<Post> slice = new SliceImpl<>(List.of(post), pageable, false);

            when(postRepository.findAllByDeletedAtIsNullAndPublishStatus(PublishStatus.PUBLIC, pageable))
                    .thenReturn(slice);

            var result = postService.getPosts(null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(postRepository).findAllByDeletedAtIsNullAndPublishStatus(PublishStatus.PUBLIC, pageable);
        }

        // creatorId 지정 시 해당 유저의 게시글만 조회
        @Test
        @DisplayName("성공: creatorId를 지정하면 해당 유저의 게시글 목록을 반환한다.")
        void getPosts_ByCreator() {
            Pageable pageable = PageRequest.of(0, 8);
            Post post = buildPost(1L, mockUser, null);
            SliceImpl<Post> slice = new SliceImpl<>(List.of(post), pageable, false);

            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(mockUser));
            when(postRepository.findSliceByUserAndDeletedAtIsNull(mockUser, pageable)).thenReturn(slice);

            var result = postService.getPosts(1L, null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        // 존재하지 않는 creatorId로 조회 시 예외
        @Test
        @DisplayName("실패: 존재하지 않는 creatorId면 USER_NOT_FOUND 예외를 던진다.")
        void getPosts_CreatorNotFound() {
            Pageable pageable = PageRequest.of(0, 8);
            when(userRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.getPosts(999L, null, pageable))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("성공: 로그인한 유저가 좋아요한 게시글이면 isLiked=true를 반환한다.")
        void getPosts_withActor_isLiked() {
            Pageable pageable = PageRequest.of(0, 8);
            Post post = buildPost(1L, otherUser, null);
            SliceImpl<Post> slice = new SliceImpl<>(List.of(post), pageable, false);

            when(postRepository.findAllByDeletedAtIsNullAndPublishStatus(PublishStatus.PUBLIC, pageable))
                    .thenReturn(slice);
            when(likeRepository.existsByPostIdAndUserId(1L, mockUser.getId())).thenReturn(true);

            var result = postService.getPosts(null, mockUser, pageable);

            assertThat(result.getContent().get(0).isLiked()).isTrue();
        }

        @Test
        @DisplayName("성공: 로그인한 유저가 북마크한 게시글이면 isBookmarked=true를 반환한다.")
        void getPosts_withActor_isBookmarked() {
            Pageable pageable = PageRequest.of(0, 8);
            Post post = buildPost(1L, otherUser, null);
            SliceImpl<Post> slice = new SliceImpl<>(List.of(post), pageable, false);

            when(postRepository.findAllByDeletedAtIsNullAndPublishStatus(PublishStatus.PUBLIC, pageable))
                    .thenReturn(slice);
            when(bookmarkRepository.existsByPostIdAndUserId(1L, mockUser.getId())).thenReturn(true);

            var result = postService.getPosts(null, mockUser, pageable);

            assertThat(result.getContent().get(0).isBookmarked()).isTrue();
        }

        @Test
        @DisplayName("성공: 비로그인 사용자는 isLiked=false, isBookmarked=false를 반환한다.")
        void getPosts_anonymous_isLikedAndBookmarkedFalse() {
            Pageable pageable = PageRequest.of(0, 8);
            Post post = buildPost(1L, otherUser, null);
            SliceImpl<Post> slice = new SliceImpl<>(List.of(post), pageable, false);

            when(postRepository.findAllByDeletedAtIsNullAndPublishStatus(PublishStatus.PUBLIC, pageable))
                    .thenReturn(slice);

            var result = postService.getPosts(null, null, pageable);

            assertThat(result.getContent().get(0).isLiked()).isFalse();
            assertThat(result.getContent().get(0).isBookmarked()).isFalse();
            verify(likeRepository, never()).existsByPostIdAndUserId(anyLong(), anyLong());
            verify(bookmarkRepository, never()).existsByPostIdAndUserId(anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("유저 게시글 목록 조회 테스트 (번호 페이지네이션)")
    class GetUserPosts {

        // 프로필 화면에서 특정 유저의 게시글을 페이지 번호 방식으로 조회
        @Test
        @DisplayName("성공: 특정 유저의 게시글 목록을 페이지로 반환한다.")
        void getUserPosts_Success() {
            Pageable pageable = PageRequest.of(0, 10);
            Post post = buildPost(1L, mockUser, null);
            Page<Post> postPage = new PageImpl<>(List.of(post), pageable, 1);

            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(mockUser));
            when(postRepository.findByUserAndDeletedAtIsNull(mockUser, pageable)).thenReturn(postPage);

            Page<PostListResponse> result = postService.getUserPosts(1L, null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).title()).isEqualTo("테스트 포스트");
        }

        // 존재하지 않는 유저 조회 시 예외
        @Test
        @DisplayName("실패: 존재하지 않는 유저면 USER_NOT_FOUND 예외를 던진다.")
        void getUserPosts_UserNotFound() {
            Pageable pageable = PageRequest.of(0, 10);
            when(userRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.getUserPosts(999L, null, pageable))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }

        // 게시글이 없는 유저도 빈 페이지를 반환해야 함
        @Test
        @DisplayName("성공: 게시글이 없는 유저면 빈 페이지를 반환한다.")
        void getUserPosts_Empty() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Post> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(mockUser));
            when(postRepository.findByUserAndDeletedAtIsNull(mockUser, pageable)).thenReturn(emptyPage);

            Page<PostListResponse> result = postService.getUserPosts(1L, null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("성공: 로그인한 유저가 좋아요한 게시글이면 isLiked=true를 반환한다.")
        void getUserPosts_withActor_isLiked() {
            Pageable pageable = PageRequest.of(0, 10);
            Post post = buildPost(1L, mockUser, null);
            Page<Post> postPage = new PageImpl<>(List.of(post), pageable, 1);

            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(mockUser));
            when(postRepository.findByUserAndDeletedAtIsNull(mockUser, pageable)).thenReturn(postPage);
            when(likeRepository.existsByPostIdAndUserId(1L, otherUser.getId())).thenReturn(true);

            Page<PostListResponse> result = postService.getUserPosts(1L, otherUser, pageable);

            assertThat(result.getContent().get(0).isLiked()).isTrue();
        }
    }

    @Nested
    @DisplayName("게시글 생성 테스트")
    class CreatePost {

        // seriesId가 null이면 시리즈 조회를 아예 하지 않아야 함
        @Test
        @DisplayName("성공: 시리즈 없이 게시글을 생성한다.")
        void create_Success_NoSeries() {
            Post saved = buildPost(1L, mockUser, null);
            when(postRepository.save(any(Post.class))).thenReturn(saved);

            PostResponse response = postService.createPost(mockUser, "제목", "내용",
                    PublishStatus.PUBLIC, PostAccessLevel.FREE, null);

            assertThat(response.userId()).isEqualTo(mockUser.getId());
            // seriesId가 null이면 seriesRepository를 호출하지 않는지 검증
            verify(seriesRepository, never()).findById(any());
        }

        // seriesId가 있으면 시리즈를 조회해서 게시글에 연결해야 함
        @Test
        @DisplayName("성공: 존재하는 시리즈와 함께 게시글을 생성한다.")
        void create_Success_WithSeries() {
            Series series = buildSeries(5L, mockUser);
            Post saved = buildPost(1L, mockUser, series);
            when(seriesRepository.findById(5L)).thenReturn(Optional.of(series));
            when(postRepository.save(any(Post.class))).thenReturn(saved);

            PostResponse response = postService.createPost(mockUser, "제목", "내용",
                    PublishStatus.PUBLIC, PostAccessLevel.FREE, 5L);

            assertThat(response.seriesId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("성공: PUBLIC 게시글 생성 시 구독자에게 SSE 알림을 전송한다.")
        void create_Public_SendsSse() {
            Subscription sub = Subscription.builder()
                    .user(otherUser)
                    .creator(mockUser)
                    .tier(SubscriptionTier.FOLLOW)
                    .build();
            when(subscriptionRepository.findByCreatorIdAndDeletedAtIsNull(1L)).thenReturn(List.of(sub));

            postService.createPost(mockUser, "제목", "내용", PublishStatus.PUBLIC, PostAccessLevel.FREE, null);

            verify(sseEmitterRepository).sendToUser(eq(2L), any());
        }

        @Test
        @DisplayName("성공: PAID 게시글 생성 시 멤버십 구독자에게만 SSE 알림을 전송한다.")
        void create_Paid_SendsSseOnlyToMembers() {
            Subscription member = Subscription.builder()
                    .user(otherUser)
                    .creator(mockUser)
                    .tier(SubscriptionTier.MEMBERSHIP)
                    .build();
            when(subscriptionRepository.findByCreatorIdAndTierAndDeletedAtIsNull(1L, SubscriptionTier.MEMBERSHIP))
                    .thenReturn(List.of(member));

            postService.createPost(mockUser, "제목", "내용", PublishStatus.PUBLIC, PostAccessLevel.PAID, null);

            verify(sseEmitterRepository).sendToUser(eq(2L), any());
            verify(subscriptionRepository, never()).findByCreatorIdAndDeletedAtIsNull(any());
        }

        @Test
        @DisplayName("성공: DRAFT 게시글 생성 시 SSE 알림을 전송하지 않는다.")
        void create_Draft_NoSse() {
            postService.createPost(mockUser, "제목", "내용", PublishStatus.DRAFT, PostAccessLevel.FREE, null);

            verify(sseEmitterRepository, never()).sendToUser(anyLong(), any());
        }

        // 없는 시리즈 ID를 넘기면 저장 전에 예외가 발생해야 함
        @Test
        @DisplayName("실패: 존재하지 않는 시리즈 ID면 SERIES_NOT_FOUND 예외를 던진다.")
        void create_SeriesNotFound() {
            when(seriesRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.createPost(mockUser, "제목", "내용",
                    PublishStatus.PUBLIC, PostAccessLevel.FREE, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("게시글 수정 테스트")
    class UpdatePost {

        // 본인 게시글 수정 → 제목/내용이 실제로 바뀌는지 확인
        @Test
        @DisplayName("성공: 본인 게시글을 수정한다.")
        void update_Success() {
            Post post = buildPost(1L, mockUser, null);
            when(postRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(post));

            PostResponse response = postService.updatePost(mockUser, 1L, "수정제목", "수정내용",
                    PublishStatus.DRAFT, PostAccessLevel.FREE, null);

            assertThat(response.title()).isEqualTo("수정제목");
        }

        @Test
        @DisplayName("성공: DRAFT→PUBLIC 전환 시 구독자에게 SSE 알림을 전송한다.")
        void update_DraftToPublic_SendsSse() {
            Post post = buildPost(1L, mockUser, null);
            ReflectionTestUtils.setField(post, "publishStatus", PublishStatus.DRAFT);
            Subscription sub = Subscription.builder()
                    .user(otherUser)
                    .creator(mockUser)
                    .tier(SubscriptionTier.FOLLOW)
                    .build();
            when(postRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(post));
            when(subscriptionRepository.findByCreatorIdAndDeletedAtIsNull(1L)).thenReturn(List.of(sub));

            postService.updatePost(mockUser, 1L, "제목", "내용", PublishStatus.PUBLIC, PostAccessLevel.FREE, null);

            verify(sseEmitterRepository).sendToUser(eq(2L), any());
        }

        @Test
        @DisplayName("성공: 이미 PUBLIC인 게시글 수정 시 SSE 알림을 다시 전송하지 않는다.")
        void update_AlreadyPublic_NoSse() {
            Post post = buildPost(1L, mockUser, null);
            when(postRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(post));

            postService.updatePost(mockUser, 1L, "수정제목", "수정내용", PublishStatus.PUBLIC, PostAccessLevel.FREE, null);

            verify(sseEmitterRepository, never()).sendToUser(anyLong(), any());
        }

        // 없는 게시글 ID → 조회 시점에 예외 발생
        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다.")
        void update_PostNotFound() {
            when(postRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.updatePost(mockUser, 999L, "제목", "내용",
                    PublishStatus.PUBLIC, PostAccessLevel.FREE, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        }

        // 타인의 게시글 수정 시도 → 본인 확인 로직에서 차단
        @Test
        @DisplayName("실패: 다른 유저의 게시글을 수정하면 ACCESS_DENIED 예외를 던진다.")
        void update_NotOwner() {
            Post post = buildPost(1L, otherUser, null);
            when(postRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.updatePost(mockUser, 1L, "제목", "내용",
                    PublishStatus.PUBLIC, PostAccessLevel.FREE, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("게시글 삭제 테스트")
    class DeletePost {

        // softDelete 방식이므로 실제로 행이 지워지는 게 아니라 deletedAt이 채워져야 함
        @Test
        @DisplayName("성공: 본인 게시글을 삭제한다.")
        void delete_Success() {
            Post post = buildPost(1L, mockUser, null);
            when(postRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(post));

            postService.deletePost(mockUser, 1L);

            assertThat(post.getDeletedAt()).isNotNull();
        }

        // 없는 게시글 삭제 시도
        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다.")
        void delete_PostNotFound() {
            when(postRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.deletePost(mockUser, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        }

        // 타인 게시글 삭제 시도 → 본인 확인 로직에서 차단
        @Test
        @DisplayName("실패: 다른 유저의 게시글을 삭제하면 ACCESS_DENIED 예외를 던진다.")
        void delete_NotOwner() {
            Post post = buildPost(1L, otherUser, null);
            when(postRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.deletePost(mockUser, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("게시글 상세 조회 테스트")
    class GetPost {

        // 조회할 때마다 viewCount가 1씩 올라야 함 (더티체킹 방식)
        @Test
        @DisplayName("성공: 게시글 조회 시 조회수가 1 증가한다.")
        void getPost_Success_ViewCountIncreased() {
            Post post = buildPost(1L, mockUser, null);
            when(postRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(post));

            postService.getPost(1L, mockUser);

            assertThat(post.getViewCount()).isEqualTo(1L);
        }

        // 없는 게시글 조회
        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다.")
        void getPost_NotFound() {
            when(postRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.getPost(999L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        }

        // PRIVATE 게시글은 작성자 본인만 조회 가능
        @Test
        @DisplayName("실패: PRIVATE 게시글을 타인이 조회하면 ACCESS_DENIED 예외를 던진다.")
        void getPost_Private_NotOwner() {
            Post post = buildPost(1L, mockUser, null);
            ReflectionTestUtils.setField(post, "publishStatus", PublishStatus.PRIVATE);
            when(postRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.getPost(1L, otherUser))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }

        // PRIVATE 게시글은 비로그인 사용자도 조회 불가
        @Test
        @DisplayName("실패: PRIVATE 게시글을 비로그인 사용자가 조회하면 ACCESS_DENIED 예외를 던진다.")
        void getPost_Private_Anonymous() {
            Post post = buildPost(1L, mockUser, null);
            ReflectionTestUtils.setField(post, "publishStatus", PublishStatus.PRIVATE);
            when(postRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.getPost(1L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }

        // PAID 게시글을 멤버십 비구독자가 조회하면 본문이 잠긴 상태로 반환
        @Test
        @DisplayName("성공: PAID 게시글을 비구독자가 조회하면 isLocked=true로 반환한다.")
        void getPost_Paid_NotMember_IsLocked() {
            Post post = buildPost(1L, mockUser, null);
            ReflectionTestUtils.setField(post, "accessLevel", PostAccessLevel.PAID);
            when(postRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(post));
            when(subscriptionRepository.findByUserIdAndCreatorId(otherUser.getId(), mockUser.getId()))
                    .thenReturn(Optional.empty());

            PostResponse response = postService.getPost(1L, otherUser);

            assertThat(response.isLocked()).isTrue();
            assertThat(response.body()).isNull();
        }

        @Test
        @DisplayName("성공: 로그인한 유저가 좋아요한 게시글 조회 시 isLiked=true를 반환한다.")
        void getPost_withActor_isLiked() {
            Post post = buildPost(1L, otherUser, null);
            when(postRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(post));
            when(likeRepository.existsByPostIdAndUserId(1L, mockUser.getId())).thenReturn(true);

            PostResponse response = postService.getPost(1L, mockUser);

            assertThat(response.isLiked()).isTrue();
            assertThat(response.isBookmarked()).isFalse();
        }

        @Test
        @DisplayName("성공: 로그인한 유저가 북마크한 게시글 조회 시 isBookmarked=true를 반환한다.")
        void getPost_withActor_isBookmarked() {
            Post post = buildPost(1L, otherUser, null);
            when(postRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(post));
            when(bookmarkRepository.existsByPostIdAndUserId(1L, mockUser.getId())).thenReturn(true);

            PostResponse response = postService.getPost(1L, mockUser);

            assertThat(response.isBookmarked()).isTrue();
            assertThat(response.isLiked()).isFalse();
        }

        @Test
        @DisplayName("성공: 비로그인 사용자가 조회하면 isLiked=false, isBookmarked=false를 반환한다.")
        void getPost_anonymous_isLikedAndBookmarkedFalse() {
            Post post = buildPost(1L, mockUser, null);
            when(postRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(post));

            PostResponse response = postService.getPost(1L, null);

            assertThat(response.isLiked()).isFalse();
            assertThat(response.isBookmarked()).isFalse();
            verify(likeRepository, never()).existsByPostIdAndUserId(anyLong(), anyLong());
            verify(bookmarkRepository, never()).existsByPostIdAndUserId(anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("시리즈에 포스트 추가 테스트")
    class AddPostToSeries {

        @Test
        @DisplayName("성공: 포스트와 시리즈 모두 본인 소유일 때 추가된다.")
        void add_Success() {
            Series series = buildSeries(5L, mockUser);
            Post post = buildPost(10L, mockUser, null);

            when(postRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));
            when(seriesRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(series));

            postService.addPostToSeries(10L, 5L, mockUser);

            assertThat(post.getSeries()).isEqualTo(series);
        }

        // 포스트 조회 실패 시 시리즈 조회는 아예 하지 않아야 함 (불필요한 DB 호출 방지)
        @Test
        @DisplayName("실패: 존재하지 않는 포스트면 POST_NOT_FOUND 예외를 던진다.")
        void add_PostNotFound() {
            when(postRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.addPostToSeries(999L, 5L, mockUser))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

            verify(seriesRepository, never()).findByIdAndDeletedAtIsNull(any());
        }

        // 포스트 주인이 아니면 시리즈 조회 전에 차단해야 함
        @Test
        @DisplayName("실패: 포스트 주인이 아니면 ACCESS_DENIED 예외를 던진다.")
        void add_PostNotOwned() {
            Post post = buildPost(10L, otherUser, null);
            when(postRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.addPostToSeries(10L, 5L, mockUser))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

            verify(seriesRepository, never()).findByIdAndDeletedAtIsNull(any());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 시리즈면 SERIES_NOT_FOUND 예외를 던진다.")
        void add_SeriesNotFound() {
            Post post = buildPost(10L, mockUser, null);
            when(postRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));
            when(seriesRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.addPostToSeries(10L, 999L, mockUser))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 시리즈 주인이 아니면 ACCESS_DENIED 예외를 던진다.")
        void add_SeriesNotOwned() {
            Series series = buildSeries(5L, otherUser);
            Post post = buildPost(10L, mockUser, null);

            when(postRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));
            when(seriesRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(series));

            assertThatThrownBy(() -> postService.addPostToSeries(10L, 5L, mockUser))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("시리즈에서 포스트 제거 테스트")
    class RemovePostFromSeries {

        @Test
        @DisplayName("성공: 포스트가 해당 시리즈에 속하고 시리즈 주인이면 제거된다.")
        void remove_Success() {
            Series series = buildSeries(5L, mockUser);
            Post post = buildPost(10L, mockUser, series);

            when(postRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));

            postService.removePostFromSeries(10L, 5L, mockUser);

            assertThat(post.getSeries()).isNull();
        }

        @Test
        @DisplayName("실패: 존재하지 않는 포스트면 POST_NOT_FOUND 예외를 던진다.")
        void remove_PostNotFound() {
            when(postRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.removePostFromSeries(999L, 5L, mockUser))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        }

        // 시리즈에 속하지 않은 포스트를 특정 시리즈에서 제거하려는 경우
        @Test
        @DisplayName("실패: 포스트가 어떤 시리즈에도 속하지 않으면 SERIES_NOT_FOUND 예외를 던진다.")
        void remove_PostHasNoSeries() {
            Post post = buildPost(10L, mockUser, null);
            when(postRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.removePostFromSeries(10L, 5L, mockUser))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND);
        }

        // 다른 시리즈에 속한 포스트를 잘못된 시리즈 ID로 제거 시도
        @Test
        @DisplayName("실패: 포스트가 다른 시리즈에 속하면 SERIES_NOT_FOUND 예외를 던진다.")
        void remove_PostBelongsToDifferentSeries() {
            Series otherSeries = buildSeries(99L, mockUser);
            Post post = buildPost(10L, mockUser, otherSeries);
            when(postRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.removePostFromSeries(10L, 5L, mockUser))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 시리즈 주인이 아니면 ACCESS_DENIED 예외를 던진다.")
        void remove_SeriesNotOwned() {
            Series series = buildSeries(5L, otherUser);
            Post post = buildPost(10L, mockUser, series);
            when(postRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.removePostFromSeries(10L, 5L, mockUser))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("시리즈 내 게시글 목록 조회 테스트")
    class GetPostsBySeriesId {

        @Test
        @DisplayName("성공: 시리즈에 속한 게시글 목록을 반환한다.")
        void getPostsBySeriesId_success() {
            Series series = buildSeries(5L, mockUser);
            Post post = buildPost(1L, mockUser, series);

            when(postRepository.findBySeriesIdAndDeletedAtIsNull(5L)).thenReturn(List.of(post));

            var result = postService.getPostsBySeriesId(5L, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).seriesId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("성공: 시리즈 게시글이 없으면 빈 목록을 반환한다.")
        void getPostsBySeriesId_empty() {
            when(postRepository.findBySeriesIdAndDeletedAtIsNull(5L)).thenReturn(List.of());

            var result = postService.getPostsBySeriesId(5L, null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공: 로그인한 유저가 좋아요한 게시글이면 isLiked=true를 반환한다.")
        void getPostsBySeriesId_withActor_isLiked() {
            Series series = buildSeries(5L, otherUser);
            Post post = buildPost(1L, otherUser, series);

            when(postRepository.findBySeriesIdAndDeletedAtIsNull(5L)).thenReturn(List.of(post));
            when(likeRepository.existsByPostIdAndUserId(1L, mockUser.getId())).thenReturn(true);

            var result = postService.getPostsBySeriesId(5L, mockUser);

            assertThat(result.get(0).isLiked()).isTrue();
        }

        @Test
        @DisplayName("성공: 비로그인 사용자는 isLiked=false, isBookmarked=false를 반환한다.")
        void getPostsBySeriesId_anonymous_isLikedAndBookmarkedFalse() {
            Series series = buildSeries(5L, mockUser);
            Post post = buildPost(1L, mockUser, series);

            when(postRepository.findBySeriesIdAndDeletedAtIsNull(5L)).thenReturn(List.of(post));

            var result = postService.getPostsBySeriesId(5L, null);

            assertThat(result.get(0).isLiked()).isFalse();
            assertThat(result.get(0).isBookmarked()).isFalse();
            verify(likeRepository, never()).existsByPostIdAndUserId(anyLong(), anyLong());
            verify(bookmarkRepository, never()).existsByPostIdAndUserId(anyLong(), anyLong());
        }
    }
}
