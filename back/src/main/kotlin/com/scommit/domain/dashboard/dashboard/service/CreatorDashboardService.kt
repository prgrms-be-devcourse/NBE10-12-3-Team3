package com.scommit.domain.dashboard.dashboard.service

import com.scommit.domain.dashboard.dashboard.dto.CreatorDashboard
import com.scommit.domain.dashboard.dashboard.dto.CreatorDashboardMetrics
import com.scommit.domain.dashboard.dashboard.dto.CreatorRadarChart
import com.scommit.domain.dashboard.dashboard.dto.HeatmapPoint
import com.scommit.domain.dashboard.dashboard.dto.TopPost
import com.scommit.domain.dashboard.dashboard.dto.TopSeries
import com.scommit.domain.post.bookmark.repository.BookmarkRepository
import com.scommit.domain.post.comment.repository.CommentRepository
import com.scommit.domain.post.like.repository.LikeRepository
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.series.series.repository.SeriesRepository
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository
import com.scommit.domain.user.user.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Suppress("LongParameterList")
@Service
class CreatorDashboardService(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val seriesRepository: SeriesRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val commentRepository: CommentRepository,
    private val likeRepository: LikeRepository,
    private val bookmarkRepository: BookmarkRepository,
) {
    fun getCreatorDashboard(
        userId: Long,
        period: String,
    ): CreatorDashboard {
        val metrics = getCreatorDashboardMetrics(userId, period)
        val heatmap = buildHeatmap(userId)
        val radar =
            buildRadarChart(userId) ?: CreatorRadarChart(
                postWriteRate = 0.0,
                seriesBuildRate = 0.0,
                commentRate = 0.0,
                reactionRate = 0.0,
                subscriptionRate = 0.0,
            )
        val topPosts = buildTopPosts(userId, period)
        val topSeries = buildTopSeries(userId, period)

        return CreatorDashboard(
            metrics = metrics,
            heatmap = heatmap,
            radar = radar,
            topPosts = topPosts,
            topSeries = topSeries,
        )
    }

    fun getCreatorDashboardMetrics(
        userId: Long,
        period: String,
    ): CreatorDashboardMetrics {
        val periodStart = calculatePeriodStart(period)

        val newPostsThisPeriod =
            postRepository.countByUserIdAndCreatedAtGreaterThanEqualAndDeletedAtIsNull(userId, periodStart)
        val totalPosts = postRepository.countByUserIdAndDeletedAtIsNull(userId)
        val freePosts = postRepository.countByUserIdAndAccessLevelAndDeletedAtIsNull(userId, PostAccessLevel.FREE)
        val paidPosts = postRepository.countByUserIdAndAccessLevelAndDeletedAtIsNull(userId, PostAccessLevel.PAID)

        val newSeriesThisPeriod =
            seriesRepository.countByUserIdAndCreatedAtGreaterThanEqualAndDeletedAtIsNull(userId, periodStart)
        val totalSeries = seriesRepository.countByUserIdAndDeletedAtIsNull(userId)

        val totalViews = postRepository.sumViewCountByUserId(userId) ?: 0L
        val viewsThisPeriod = postRepository.sumViewCountByUserIdAndPeriod(userId, periodStart) ?: 0L
        val avgViewsPerPost = if (totalPosts > 0) totalViews.toDouble() / totalPosts else 0.0

        val totalLikes = postRepository.sumLikeCountByUserId(userId) ?: 0L
        val likesThisPeriod = postRepository.sumLikeCountByUserIdAndPeriod(userId, periodStart) ?: 0L
        val avgLikesPerPost = if (totalPosts > 0) totalLikes.toDouble() / totalPosts else 0.0

        val totalBookmarks = postRepository.sumBookmarkCountByUserId(userId) ?: 0L
        val bookmarksThisPeriod = postRepository.sumBookmarkCountByUserIdAndPeriod(userId, periodStart) ?: 0L
        val avgBookmarksPerPost = if (totalPosts > 0) totalBookmarks.toDouble() / totalPosts else 0.0

        val newFollowersThisPeriod = subscriptionRepository.countNewFollowersByUserIdAndPeriod(userId, periodStart)
        val totalFollowers = subscriptionRepository.countByCreatorIdAndDeletedAtIsNull(userId)

        val membershipConversionRate = subscriptionRepository.getMembershipConversionRate(userId)
        val paidMembershipCount = subscriptionRepository.countPaidMembershipsByCreatorId(userId)

        return CreatorDashboardMetrics(
            newPostsThisPeriod = newPostsThisPeriod,
            totalPosts = totalPosts,
            freePosts = freePosts,
            paidPosts = paidPosts,
            newSeriesThisPeriod = newSeriesThisPeriod,
            totalSeries = totalSeries,
            viewsThisPeriod = viewsThisPeriod,
            totalViews = totalViews,
            avgViewsPerPost = avgViewsPerPost,
            likesThisPeriod = likesThisPeriod,
            totalLikes = totalLikes,
            avgLikesPerPost = avgLikesPerPost,
            bookmarksThisPeriod = bookmarksThisPeriod,
            totalBookmarks = totalBookmarks,
            avgBookmarksPerPost = avgBookmarksPerPost,
            newFollowersThisPeriod = newFollowersThisPeriod,
            totalFollowers = totalFollowers,
            membershipConversionRate = membershipConversionRate,
            paidMembershipCount = paidMembershipCount,
        )
    }

    private fun buildHeatmap(userId: Long): List<HeatmapPoint> {
        val rollingWindowStart = LocalDateTime.now().minusDays(ROLLING_WINDOW_DAYS)
        val posts = postRepository.findActivityHeatmapByUserId(userId, rollingWindowStart)

        val dailyCount = posts.groupingBy { checkNotNull(it.createdAt).toLocalDate() }.eachCount()

        val today = LocalDate.now()
        return ((ROLLING_WINDOW_DAYS - 1) downTo 0).map { i ->
            val date = today.minusDays(i)
            HeatmapPoint(date.toString(), (dailyCount[date] ?: 0).toLong())
        }
    }

    private fun buildRadarChart(userId: Long): CreatorRadarChart? {
        val rollingWindowStart = LocalDateTime.now().minusDays(ROLLING_WINDOW_DAYS)

        val postCount =
            postRepository.countByUserIdAndCreatedAtGreaterThanEqualAndDeletedAtIsNull(userId, rollingWindowStart)
        val seriesCount =
            seriesRepository.countByUserIdAndCreatedAtGreaterThanEqualAndDeletedAtIsNull(userId, rollingWindowStart)
        val commentCount = commentRepository.countByUserIdAndPeriod(userId, rollingWindowStart)
        val reactionCount =
            likeRepository.countByUserIdAndPeriod(userId, rollingWindowStart) +
                bookmarkRepository.countByUserIdAndPeriod(userId, rollingWindowStart)
        val subscriptionCount = subscriptionRepository.countNewFollowersByUserIdAndPeriod(userId, rollingWindowStart)

        val totalActivity = postCount + seriesCount + commentCount + reactionCount + subscriptionCount

        if (totalActivity < MIN_ACTIVITY_FOR_RADAR) {
            return null
        }

        val averages = calculatePlatformAverages(rollingWindowStart)

        val postMultiple = if (averages.avgPostCount > 0) postCount / averages.avgPostCount else 1.0
        val seriesMultiple = if (averages.avgSeriesCount > 0) seriesCount / averages.avgSeriesCount else 1.0
        val commentMultiple = if (averages.avgCommentCount > 0) commentCount / averages.avgCommentCount else 1.0
        val reactionMultiple = if (averages.avgReactionCount > 0) reactionCount / averages.avgReactionCount else 1.0
        val subscriptionMultiple =
            if (averages.avgSubscriptionCount > 0) subscriptionCount / averages.avgSubscriptionCount else 1.0

        val totalMultiple = postMultiple + seriesMultiple + commentMultiple + reactionMultiple + subscriptionMultiple
        val percentOf: (Double) -> Double = { multiple ->
            if (totalMultiple > 0) multiple / totalMultiple * FULL_PERCENT else 0.0
        }

        return CreatorRadarChart(
            postWriteRate = percentOf(postMultiple),
            seriesBuildRate = percentOf(seriesMultiple),
            commentRate = percentOf(commentMultiple),
            reactionRate = percentOf(reactionMultiple),
            subscriptionRate = percentOf(subscriptionMultiple),
        )
    }

    private fun calculatePlatformAverages(rollingWindowStart: LocalDateTime): PlatformAverages {
        val totalUsers = userRepository.countByDeletedAtIsNull()

        val totalPostCount = postRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(rollingWindowStart)
        val totalSeriesCount = seriesRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(rollingWindowStart)
        val totalCommentCount = commentRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(rollingWindowStart)
        val totalLikeCount = likeRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(rollingWindowStart)
        val totalBookmarkCount =
            bookmarkRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(rollingWindowStart)
        val totalSubscriptionCount =
            subscriptionRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(rollingWindowStart)
        val totalReactionCount = totalLikeCount + totalBookmarkCount

        return PlatformAverages(
            avgPostCount = if (totalUsers > 0) totalPostCount.toDouble() / totalUsers else 1.0,
            avgSeriesCount = if (totalUsers > 0) totalSeriesCount.toDouble() / totalUsers else 1.0,
            avgCommentCount = if (totalUsers > 0) totalCommentCount.toDouble() / totalUsers else 1.0,
            avgReactionCount = if (totalUsers > 0) totalReactionCount.toDouble() / totalUsers else 1.0,
            avgSubscriptionCount = if (totalUsers > 0) totalSubscriptionCount.toDouble() / totalUsers else 1.0,
        )
    }

    private data class PlatformAverages(
        val avgPostCount: Double,
        val avgSeriesCount: Double,
        val avgCommentCount: Double,
        val avgReactionCount: Double,
        val avgSubscriptionCount: Double,
    )

    private fun buildTopPosts(
        userId: Long,
        period: String,
    ): List<TopPost> {
        val periodStart = calculatePeriodStart(period)
        return postRepository
            .findTop5ByUserIdAndPeriod(userId, periodStart, PageRequest.of(0, TOP_N))
            .map { TopPost.from(it) }
    }

    private fun buildTopSeries(
        userId: Long,
        period: String,
    ): List<TopSeries> {
        val periodStart = calculatePeriodStart(period)
        return seriesRepository
            .findTop5SeriesByUserIdAndPeriod(userId, periodStart, PageRequest.of(0, TOP_N))
            .map { series ->
                val seriesPosts = postRepository.findBySeriesIdAndDeletedAtIsNull(checkNotNull(series.id))
                TopSeries(
                    id = checkNotNull(series.id),
                    title = series.title,
                    postCount = seriesPosts.size.toLong(),
                    viewCount = seriesPosts.sumOf { it.viewCount },
                )
            }
    }

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
        private const val HEATMAP_WEEKS = 52L
        private const val DAYS_PER_WEEK = 7L
        private const val ROLLING_WINDOW_DAYS = HEATMAP_WEEKS * DAYS_PER_WEEK
        private const val TOP_N = 5
        private const val MIN_ACTIVITY_FOR_RADAR = 20L
        private const val SEVEN_DAYS = 7L
        private const val THIRTY_DAYS = 30L
        private val ALL_TIME_START: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)
        private const val FULL_PERCENT = 100.0
    }
}
