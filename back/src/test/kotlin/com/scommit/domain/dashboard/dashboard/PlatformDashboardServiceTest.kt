package com.scommit.domain.dashboard.dashboard

import com.scommit.domain.dashboard.dashboard.service.PlatformDashboardService
import com.scommit.domain.media.media.entity.Media
import com.scommit.domain.media.media.entity.MediaType
import com.scommit.domain.media.media.repository.MediaRepository
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
import com.scommit.domain.user.usermedia.entity.UserMedia
import com.scommit.domain.user.usermedia.repository.UserMediaRepository
import org.assertj.core.api.Assertions.assertThat
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
@DisplayName("플랫폼 대시보드 Service")
class PlatformDashboardServiceTest {
    @Autowired
    private lateinit var platformDashboardService: PlatformDashboardService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var subscriptionRepository: SubscriptionRepository

    @Autowired
    private lateinit var seriesRepository: SeriesRepository

    @Autowired
    private lateinit var mediaRepository: MediaRepository

    @Autowired
    private lateinit var userMediaRepository: UserMediaRepository

    private lateinit var creator1: User
    private lateinit var creator2: User
    private lateinit var follower: User

    @BeforeEach
    fun setUp() {
        creator1 = saveUser("creator1", "창작자1")
        creator2 = saveUser("creator2", "창작자2")
        follower = saveUser("follower", "팔로워")
    }

    @Test
    @DisplayName("30일 기간으로 관리자 대시보드를 조회한다")
    fun `30일 기간으로 관리자 대시보드를 조회한다`() {
        // Given: 포스트 3개 추가
        savePost(creator1, "Post 1", PostAccessLevel.FREE)
        savePost(creator1, "Post 2", PostAccessLevel.PAID)
        savePost(creator2, "Post 3", PostAccessLevel.FREE)

        // 구독 추가 (팔로워가 2명의 창작자를 팔로우)
        subscriptionRepository.save(Subscription(follower, creator1, SubscriptionTier.FOLLOW, null, null))
        subscriptionRepository.save(Subscription(follower, creator2, SubscriptionTier.FOLLOW, null, null))

        // When
        val data = platformDashboardService.getAdminDashboard("30d")

        // Then
        assertThat(data.metrics.totalUsers).isEqualTo(3) // creator1, creator2, follower
        assertThat(data.metrics.totalPosts).isEqualTo(3)
        assertThat(data.metrics.newPostsThisPeriod).isEqualTo(3)
        assertThat(data.metrics.totalSeries).isEqualTo(0)

        assertThat(data.signupTrend).isNotEmpty()
        assertThat(data.subscriptionRatio).isNotNull()
        assertThat(data.superCreators).isNotEmpty()
        assertThat(data.topPosts).isNotEmpty()
    }

    @Test
    @DisplayName("관리자 대시보드의 인기 창작자는 followerIncrease 기준으로 정렬된다")
    fun `인기 창작자는 followerIncrease 기준으로 정렬된다`() {
        // Given: creator1은 2명, creator2는 1명의 팔로워를 가짐
        subscriptionRepository.save(Subscription(follower, creator1, SubscriptionTier.FOLLOW, null, null))
        val follower2 = saveUser("follower2", "팔로워2")
        subscriptionRepository.save(Subscription(follower2, creator1, SubscriptionTier.FOLLOW, null, null))

        val follower3 = saveUser("follower3", "팔로워3")
        subscriptionRepository.save(Subscription(follower3, creator2, SubscriptionTier.FOLLOW, null, null))

        // When
        val data = platformDashboardService.getAdminDashboard("7d")

        // Then: creator1이 1등, creator2가 2등
        assertThat(data.superCreators).isNotEmpty()
        assertThat(data.superCreators[0].id).isEqualTo(creator1.id)
        assertThat(data.superCreators[1].id).isEqualTo(creator2.id)
    }

    @Test
    @DisplayName("메인페이지 데이터는 trending creators, popular posts를 반환한다")
    fun `메인페이지 데이터는 trending creators와 popular posts를 반환한다`() {
        // Given: 포스트와 구독 데이터
        savePost(creator1, "Popular FREE Post", PostAccessLevel.FREE)
        savePost(creator2, "Popular PAID Post", PostAccessLevel.PAID)
        subscriptionRepository.save(Subscription(follower, creator1, SubscriptionTier.FOLLOW, null, null))

        // When
        val mainpageData = platformDashboardService.getMainpage()

        // Then
        assertThat(mainpageData.trendingCreators).isNotEmpty()
        assertThat(mainpageData.popularPaidPosts).isNotEmpty()
        assertThat(mainpageData.popularFreePosts).isNotEmpty()
    }

    @Test
    @DisplayName("구독이 하나도 없으면 구독 비율이 0퍼센트로 반환된다")
    fun `구독이 없으면 구독 비율이 0으로 반환된다`() {
        // When: setUp에서 만든 유저 3명 외에 구독을 하나도 만들지 않은 상태
        val data = platformDashboardService.getAdminDashboard("30d")

        // Then
        assertThat(data.subscriptionRatio.followCount).isEqualTo(0)
        assertThat(data.subscriptionRatio.membershipCount).isEqualTo(0)
        assertThat(data.subscriptionRatio.followPercentage).isEqualTo(0.0)
        assertThat(data.subscriptionRatio.membershipPercentage).isEqualTo(0.0)
    }

    @Test
    @DisplayName("시리즈가 있으면 시리즈당 평균 포스트 수가 계산된다")
    fun `시리즈가 존재하면 시리즈당 평균 포스트 수가 0보다 크다`() {
        // Given: 포스트 2개, 시리즈 1개
        savePost(creator1, "Post 1", PostAccessLevel.FREE)
        savePost(creator1, "Post 2", PostAccessLevel.FREE)
        seriesRepository.save(Series(user = creator1, title = "Series 1", body = "Body"))

        // When
        val metrics = platformDashboardService.getAdminDashboardMetrics("30d")

        // Then
        assertThat(metrics.totalSeries).isEqualTo(1)
        assertThat(metrics.avgPostsPerSeries).isGreaterThan(0.0)
    }

    @Test
    @DisplayName("인식할 수 없는 기간 문자열은 30일 기본값으로 처리된다")
    fun `알 수 없는 기간 문자열이 오면 기본 30일로 처리된다`() {
        // Given
        savePost(creator1, "Post", PostAccessLevel.FREE)

        // When
        val metrics = platformDashboardService.getAdminDashboardMetrics("invalid-period")

        // Then: 30일 기본값과 동일하게 신규 포스트로 집계된다
        assertThat(metrics.newPostsThisPeriod).isEqualTo(1)
    }

    @Test
    @DisplayName("인기 창작자에 프로필 이미지가 있으면 profileImageUrl이 채워진다")
    fun `인기 창작자에게 프로필 이미지가 있으면 URL이 반환된다`() {
        // Given
        val media = mediaRepository.save(Media(url = "https://cdn.test/creator1.png", type = MediaType.IMAGE))
        userMediaRepository.save(UserMedia(user = creator1, media = media))
        subscriptionRepository.save(Subscription(follower, creator1, SubscriptionTier.FOLLOW, null, null))

        // When
        val data = platformDashboardService.getAdminDashboard("30d")

        // Then
        val superCreator = data.superCreators.first { it.id == creator1.id }
        assertThat(superCreator.profileImageUrl).isEqualTo("https://cdn.test/creator1.png")
    }

    private fun saveUser(
        label: String,
        nickname: String,
    ): User =
        userRepository.save(
            User(
                email = "$label-" + System.nanoTime() + "@test.com",
                password = "password",
                nickname = nickname,
                introduction = "intro",
                role = UserRole.USER,
            ),
        )

    private fun savePost(
        author: User,
        title: String,
        accessLevel: PostAccessLevel,
    ): Post =
        postRepository.save(
            Post(
                user = author,
                series = null,
                title = title,
                body = "Content",
                publishStatus = PublishStatus.PUBLIC,
                accessLevel = accessLevel,
            ),
        )
}
