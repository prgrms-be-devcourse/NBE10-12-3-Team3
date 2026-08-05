package com.scommit.domain.dashboard.dashboard.dto

data class AdminDashboardMetrics(
    val newUsersThisPeriod: Long,
    val totalUsers: Long,
    val newPostsThisPeriod: Long,
    val totalPosts: Long,
    val freePosts: Long,
    val paidPosts: Long,
    val newSeriesThisPeriod: Long,
    val totalSeries: Long,
    val avgPostsPerSeries: Double,
    val viewsThisPeriod: Long,
    val totalViews: Long,
    val avgViewsPerPost: Double,
)
