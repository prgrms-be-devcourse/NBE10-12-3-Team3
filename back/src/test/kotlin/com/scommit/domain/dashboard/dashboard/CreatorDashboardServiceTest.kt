package com.scommit.domain.dashboard.dashboard

import com.scommit.domain.dashboard.dashboard.dto.CreatorRadarChart
import com.scommit.domain.dashboard.dashboard.service.CreatorDashboardService
import com.scommit.domain.post.bookmark.entity.Bookmark
import com.scommit.domain.post.bookmark.repository.BookmarkRepository
import com.scommit.domain.post.comment.entity.Comment
import com.scommit.domain.post.comment.repository.CommentRepository
import com.scommit.domain.post.like.entity.Like
import com.scommit.domain.post.like.repository.LikeRepository
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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("창작자 대시보드 Service")
class CreatorDashboardServiceTest {
    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var seriesRepository: SeriesRepository

    @Autowired
    private lateinit var commentRepository: CommentRepository

    @Autowired
    private lateinit var likeRepository: LikeRepository

    @Autowired
    private lateinit var bookmarkRepository: BookmarkRepository

    @Autowired
    private lateinit var subscriptionRepository: SubscriptionRepository

    @Autowired
    private lateinit var dashboardService: CreatorDashboardService

    private lateinit var creator: User
    private lateinit var otherUser: User

    @BeforeEach
    fun setUp() {
        creator =
            userRepository.save(
                User(
                    email = "user-creator-" + System.nanoTime() + "@test.com",
                    password = "password123",
                    nickname = "창작자",
                    introduction = "Creator intro",
                    role = UserRole.USER,
                ),
            )

        otherUser =
            userRepository.save(
                User(
                    email = "user-other-" + System.nanoTime() + "@test.com",
                    password = "password123",
                    nickname = "타사용자",
                    introduction = "Other intro",
                    role = UserRole.USER,
                ),
            )
    }

    @Test
    @DisplayName("최근 7일 내 생성된 포스트와 lifetime 누적 포스트 통계를 조회한다")
    fun `getCreatorDashboardMetrics_7일 이내 신규 포스트와 누적 포스트를 반환한다`() {
        // Given: 기간 내 포스트 2개
        postRepository.save(
            Post(
                user = creator,
                series = null,
                title = "Recent Post 1",
                body = "Content",
                publishStatus = PublishStatus.PUBLIC,
                accessLevel = PostAccessLevel.FREE,
            ),
        )
        postRepository.save(
            Post(
                user = creator,
                series = null,
                title = "Recent Post 2",
                body = "Content",
                publishStatus = PublishStatus.PUBLIC,
                accessLevel = PostAccessLevel.PAID,
            ),
        )

        // When
        val metrics = dashboardService.getCreatorDashboardMetrics(checkNotNull(creator.id), "7d")

        // Then: full-flow 모델 검증
        assertThat(metrics.newPostsThisPeriod).isEqualTo(2)
        assertThat(metrics.totalPosts).isEqualTo(2)
        assertThat(metrics.freePosts).isEqualTo(1)
        assertThat(metrics.paidPosts).isEqualTo(1)
    }

    @Test
    @DisplayName("기간을 'all'로 선택하면 lifetime 누적값 = 기간 내 신규값")
    fun `기간이 all이면 신규값과 누적값이 같다`() {
        // Given: 3개 포스트
        postRepository.save(
            Post(
                user = creator,
                series = null,
                title = "Post 1",
                body = "Content",
                publishStatus = PublishStatus.PUBLIC,
                accessLevel = PostAccessLevel.FREE,
            ),
        )
        postRepository.save(
            Post(
                user = creator,
                series = null,
                title = "Post 2",
                body = "Content",
                publishStatus = PublishStatus.PUBLIC,
                accessLevel = PostAccessLevel.PAID,
            ),
        )
        postRepository.save(
            Post(
                user = creator,
                series = null,
                title = "Post 3",
                body = "Content",
                publishStatus = PublishStatus.PUBLIC,
                accessLevel = PostAccessLevel.PAID,
            ),
        )

        // When
        val metrics = dashboardService.getCreatorDashboardMetrics(checkNotNull(creator.id), "all")

        // Then
        assertThat(metrics.totalPosts).isEqualTo(3)
        assertThat(metrics.newPostsThisPeriod).isEqualTo(metrics.totalPosts)
        assertThat(metrics.freePosts).isEqualTo(1)
        assertThat(metrics.paidPosts).isEqualTo(2)
    }

    @Test
    @DisplayName("오방진 차트: 총 활동 수 20건 미만 시 콜드스타트 가드로 0을 반환한다")
    fun `총 활동 수가 20건 미만이면 콜드스타트 가드가 동작한다`() {
        // Given: 최근 1년 내 포스트 5개만 (총 활동 5건 < 20)
        repeat(5) { i ->
            postRepository.save(
                Post(
                    user = creator,
                    series = null,
                    title = "Post $i",
                    body = "Content",
                    publishStatus = PublishStatus.PUBLIC,
                    accessLevel = PostAccessLevel.FREE,
                ),
            )
        }

        // When
        val radar: CreatorRadarChart = dashboardService.getCreatorDashboard(checkNotNull(creator.id), "30d").radar

        // Then: 콜드스타트 상태이므로 모두 0
        assertThat(radar.postWriteRate).isEqualTo(0.0)
        assertThat(radar.seriesBuildRate).isEqualTo(0.0)
        assertThat(radar.commentRate).isEqualTo(0.0)
        assertThat(radar.reactionRate).isEqualTo(0.0)
        assertThat(radar.subscriptionRate).isEqualTo(0.0)
    }

    @Test
    @DisplayName("오방진 차트: 플랫폼 평균 정규화 (§3.3 스펙)")
    fun `플랫폼 평균 대비 배수로 정규화되어 합계가 100퍼센트가 된다`() {
        // Given: creator 활동 합계 = post 5 + series 2 + comment 3 + reaction 8(좋아요5+북마크3) + subscription 2
        // = 20건 (콜드스타트 가드 임계치를 정확히 충족)
        seedCreatorActivityForRadarTest()

        // When
        val radar = dashboardService.getCreatorDashboard(checkNotNull(creator.id), "30d").radar

        // Then: 5개 축이 모두 0보다 크고, 합계가 약 100% (부동소수점 오차 고려)
        assertThat(radar.postWriteRate).isGreaterThan(0.0)
        assertThat(radar.seriesBuildRate).isGreaterThan(0.0)
        assertThat(radar.commentRate).isGreaterThan(0.0)
        assertThat(radar.reactionRate).isGreaterThan(0.0)
        assertThat(radar.subscriptionRate).isGreaterThan(0.0)

        val sum =
            radar.postWriteRate + radar.seriesBuildRate + radar.commentRate +
                radar.reactionRate + radar.subscriptionRate
        assertThat(sum).isCloseTo(100.0, within(0.1))
    }

    @Test
    @DisplayName("포스트가 하나도 없으면 조회수/좋아요/북마크 합계와 평균이 모두 0이다")
    fun `포스트가 없으면 통계가 0으로 반환된다`() {
        // When: creator가 포스트를 한 건도 쓰지 않은 상태에서 조회
        val metrics = dashboardService.getCreatorDashboardMetrics(checkNotNull(creator.id), "30d")

        // Then: SUM 쿼리가 NULL을 반환해도 0으로, totalPosts=0이므로 평균도 0
        assertThat(metrics.totalPosts).isEqualTo(0)
        assertThat(metrics.totalViews).isEqualTo(0)
        assertThat(metrics.avgViewsPerPost).isEqualTo(0.0)
        assertThat(metrics.totalLikes).isEqualTo(0)
        assertThat(metrics.avgLikesPerPost).isEqualTo(0.0)
        assertThat(metrics.totalBookmarks).isEqualTo(0)
        assertThat(metrics.avgBookmarksPerPost).isEqualTo(0.0)
    }

    @Test
    @DisplayName("인식할 수 없는 기간 문자열은 30일 기본값으로 처리된다")
    fun `알 수 없는 기간 문자열이 오면 기본 30일로 처리된다`() {
        // Given
        savePost(creator, "Post")

        // When
        val metrics = dashboardService.getCreatorDashboardMetrics(checkNotNull(creator.id), "invalid-period")

        // Then: 30일 기본값과 동일하게 신규 포스트로 집계된다
        assertThat(metrics.newPostsThisPeriod).isEqualTo(1)
    }

    // creator를 구독할 팔로워 2명 (Subscription은 (user_id, creator_id) UNIQUE 제약이 있어
    // "creator를 구독하는 사람"을 늘리려면 서로 다른 유저가 필요함)
    private fun seedCreatorActivityForRadarTest() {
        val follower1 = saveUser("follower1")
        val follower2 = saveUser("follower2")

        // creator가 작성한 포스트 5개
        repeat(5) { i -> savePost(creator, "Creator Post $i") }

        // creator가 구축한 시리즈 2개
        repeat(2) { i ->
            seriesRepository.save(
                Series(
                    user = creator,
                    title = "Creator Series $i",
                    body = "Body",
                ),
            )
        }

        // creator가 반응(좋아요/북마크)할 대상: otherUser의 포스트 5개
        // Like/Bookmark는 (post_id, user_id) UNIQUE 제약이 있어 "좋아요 5개"를 걸려면
        // 서로 다른 포스트 5개가 필요함
        val otherPosts = (0 until 5).map { i -> savePost(otherUser, "Other Post $i") }

        // creator가 단 댓글 3개 (otherUser의 첫 포스트에)
        repeat(3) { i ->
            commentRepository.save(
                Comment(
                    post = otherPosts[0],
                    user = creator,
                    body = "Comment $i",
                ),
            )
        }

        // creator의 좋아요 5개 (서로 다른 포스트 5개에 각 1번)
        otherPosts.forEach { post -> likeRepository.save(Like(post, creator)) }

        // creator의 북마크 3개 (서로 다른 포스트 3개에 각 1번, Like와 다른 테이블이라 포스트 중복 무관)
        repeat(3) { i -> bookmarkRepository.save(Bookmark(otherPosts[i], creator)) }

        // creator를 구독하는 팔로워 2명 (Subscription.user = 구독자, Subscription.creator = 구독 대상)
        subscriptionRepository.save(Subscription(follower1, creator, SubscriptionTier.FOLLOW, null, null))
        subscriptionRepository.save(Subscription(follower2, creator, SubscriptionTier.FOLLOW, null, null))
    }

    private fun saveUser(label: String): User =
        userRepository.save(
            User(
                email = "$label-" + System.nanoTime() + "@test.com",
                password = "password123",
                nickname = label,
                introduction = "intro",
                role = UserRole.USER,
            ),
        )

    private fun savePost(
        author: User,
        title: String,
    ): Post =
        postRepository.save(
            Post(
                user = author,
                series = null,
                title = title,
                body = "Content",
                publishStatus = PublishStatus.PUBLIC,
                accessLevel = PostAccessLevel.FREE,
            ),
        )
}
