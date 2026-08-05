package com.scommit.domain.dashboard.dashboard.dto

data class AdminDashboard(
    val metrics: AdminDashboardMetrics,
    val signupTrend: List<SignupTrendPoint>,
    val subscriptionRatio: SubscriptionRatio,
    val superCreators: List<SuperCreator>,
    val topPosts: List<TopPost>,
)
