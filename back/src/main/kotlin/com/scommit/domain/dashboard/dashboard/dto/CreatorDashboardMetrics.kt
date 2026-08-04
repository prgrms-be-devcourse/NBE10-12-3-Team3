package com.scommit.domain.dashboard.dashboard.dto

data class CreatorDashboardMetrics(
    val newPostsThisPeriod: Long,
    val totalPosts: Long,
    val freePosts: Long,
    val paidPosts: Long,
    val newSeriesThisPeriod: Long,
    val totalSeries: Long,
    val viewsThisPeriod: Long,
    val totalViews: Long,
    val avgViewsPerPost: Double,
    val likesThisPeriod: Long,
    val totalLikes: Long,
    val avgLikesPerPost: Double,
    val bookmarksThisPeriod: Long,
    val totalBookmarks: Long,
    val avgBookmarksPerPost: Double,
    val newFollowersThisPeriod: Long,
    val totalFollowers: Long,
    val membershipConversionRate: Double,
    val paidMembershipCount: Long,
)
