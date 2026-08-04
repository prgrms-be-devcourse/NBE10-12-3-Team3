package com.scommit.domain.dashboard.dashboard.service

import com.scommit.domain.dashboard.dashboard.dto.AdminDashboard
import com.scommit.domain.dashboard.dashboard.dto.AdminDashboardMetrics
import com.scommit.domain.dashboard.dashboard.dto.Mainpage
import com.scommit.domain.dashboard.dashboard.dto.SignupTrendPoint
import com.scommit.domain.dashboard.dashboard.dto.SubscriptionRatio
import com.scommit.domain.dashboard.dashboard.dto.SuperCreator
import com.scommit.domain.dashboard.dashboard.dto.TopPost
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.series.series.repository.SeriesRepository
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.domain.user.usermedia.repository.UserMediaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.WeekFields

@Service
class PlatformDashboardService(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val seriesRepository: SeriesRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userMediaRepository: UserMediaRepository,
) {
    fun getAdminDashboard(period: String): AdminDashboard {
        val periodStart = calculatePeriodStart(period)

        return AdminDashboard(
            metrics = getAdminDashboardMetrics(period),
            signupTrend = buildSignupTrend(period, periodStart),
            subscriptionRatio = buildSubscriptionRatio(),
            superCreators = buildSuperCreators(periodStart),
            topPosts = buildTopPosts(periodStart),
        )
    }

    fun getAdminDashboardMetrics(period: String): AdminDashboardMetrics {
        val periodStart = calculatePeriodStart(period)

        val totalUsers = userRepository.countByDeletedAtIsNull()
        val newUsers = userRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(periodStart)

        val totalPosts = postRepository.countByDeletedAtIsNull()
        val newPosts = postRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(periodStart)

        val freePosts = postRepository.countByAccessLevelAndDeletedAtIsNull(PostAccessLevel.FREE)
        val paidPosts = postRepository.countByAccessLevelAndDeletedAtIsNull(PostAccessLevel.PAID)

        val totalSeries = seriesRepository.countByDeletedAtIsNull()
        val newSeries = seriesRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(periodStart)
        val avgPostsPerSeries = if (totalSeries > 0) totalPosts.toDouble() / totalSeries else 0.0

        val totalViews = postRepository.sumAllViewCount() ?: 0L
        val viewsThisPeriod = postRepository.sumAllViewCountByPeriod(periodStart) ?: 0L
        val avgViewsPerPost = if (totalPosts > 0) totalViews.toDouble() / totalPosts else 0.0

        return AdminDashboardMetrics(
            newUsersThisPeriod = newUsers,
            totalUsers = totalUsers,
            newPostsThisPeriod = newPosts,
            totalPosts = totalPosts,
            freePosts = freePosts,
            paidPosts = paidPosts,
            newSeriesThisPeriod = newSeries,
            totalSeries = totalSeries,
            avgPostsPerSeries = avgPostsPerSeries,
            viewsThisPeriod = viewsThisPeriod,
            totalViews = totalViews,
            avgViewsPerPost = avgViewsPerPost,
        )
    }

    private fun buildSignupTrend(
        period: String,
        periodStart: LocalDateTime,
    ): List<SignupTrendPoint> {
        val createdAtList = userRepository.findCreatedAtByPeriod(periodStart)
        val countByLabel =
            when (period) {
                "7d" -> createdAtList.groupingBy { it.toLocalDate().toString() }.eachCount()
                "30d" -> createdAtList.groupingBy { it.toIsoWeekLabel() }.eachCount()
                else -> createdAtList.groupingBy { YearMonth.from(it).toString() }.eachCount()
            }

        return countByLabel.entries
            .sortedBy { it.key }
            .map { (label, count) -> SignupTrendPoint(label, count.toLong()) }
    }

    private fun LocalDateTime.toIsoWeekLabel(): String {
        val date = toLocalDate()
        val year = date.get(WeekFields.ISO.weekBasedYear())
        val week = date.get(WeekFields.ISO.weekOfWeekBasedYear())
        return "%d-%02d".format(year, week)
    }

    private fun buildSubscriptionRatio(): SubscriptionRatio {
        val followCount = subscriptionRepository.countByTierFollow()
        val membershipCount = subscriptionRepository.countByTierMembership()
        val total = followCount + membershipCount

        return SubscriptionRatio(
            followCount = followCount,
            membershipCount = membershipCount,
            followPercentage = if (total > 0) followCount.toDouble() / total * FULL_PERCENT else 0.0,
            membershipPercentage = if (total > 0) membershipCount.toDouble() / total * FULL_PERCENT else 0.0,
        )
    }

    private fun buildSuperCreators(periodStart: LocalDateTime): List<SuperCreator> {
        val rawData = subscriptionRepository.findTopCreatorsByFollowerIncreaseAndPeriod(periodStart)

        return rawData.take(TOP_N).map { row ->
            val creator = row[0] as User
            val followerIncrease = (row[1] as Number).toLong()
            val totalFollowers = subscriptionRepository.countByCreatorIdAndDeletedAtIsNull(checkNotNull(creator.id))
            val profileImageUrl = userMediaRepository.findByUser(creator)?.media?.url

            SuperCreator(
                id = checkNotNull(creator.id),
                nickname = creator.nickname,
                subscriberCount = totalFollowers,
                followerIncrease = followerIncrease,
                profileImageUrl = profileImageUrl,
                introduction = creator.introduction,
            )
        }
    }

    private fun buildTopPosts(periodStart: LocalDateTime): List<TopPost> =
        postRepository
            .findTop5PostsByPeriod(periodStart, PageRequest.of(0, TOP_N))
            .map { TopPost.from(it) }

    fun getMainpage(): Mainpage {
        val sevenDaysAgo = LocalDateTime.now().minusDays(SEVEN_DAYS)
        val thirtyDaysAgo = LocalDateTime.now().minusDays(THIRTY_DAYS)

        return Mainpage(
            trendingCreators = buildSuperCreators(sevenDaysAgo),
            popularPaidPosts = buildTopPostsByAccessLevel(thirtyDaysAgo, PostAccessLevel.PAID),
            popularFreePosts = buildTopPostsByAccessLevel(thirtyDaysAgo, PostAccessLevel.FREE),
        )
    }

    private fun buildTopPostsByAccessLevel(
        periodStart: LocalDateTime,
        accessLevel: PostAccessLevel,
    ): List<TopPost> =
        postRepository
            .findTop5PostsByAccessLevelAndPeriod(periodStart, accessLevel, PageRequest.of(0, MAINPAGE_TOP_N))
            .map { TopPost.from(it) }

    private fun calculatePeriodStart(period: String): LocalDateTime {
        val now = LocalDateTime.now()
        return when (period) {
            "7d" -> now.minusDays(SEVEN_DAYS)
            "30d" -> now.minusDays(THIRTY_DAYS)
            "all" -> ALL_TIME_START
            else -> now.minusDays(THIRTY_DAYS)
        }
    }

    companion object {
        private const val TOP_N = 5
        private const val MAINPAGE_TOP_N = 10
        private const val SEVEN_DAYS = 7L
        private const val THIRTY_DAYS = 30L
        private const val FULL_PERCENT = 100.0
        private val ALL_TIME_START: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)
    }
}
