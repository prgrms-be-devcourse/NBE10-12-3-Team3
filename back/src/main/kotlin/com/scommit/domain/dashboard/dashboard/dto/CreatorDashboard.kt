package com.scommit.domain.dashboard.dashboard.dto

data class CreatorDashboard(
    val metrics: CreatorDashboardMetrics,
    val heatmap: List<HeatmapPoint>,
    val radar: CreatorRadarChart,
    val topPosts: List<TopPost>,
    val topSeries: List<TopSeries>,
)
