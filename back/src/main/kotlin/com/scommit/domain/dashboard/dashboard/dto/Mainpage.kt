package com.scommit.domain.dashboard.dashboard.dto

data class Mainpage(
    val trendingCreators: List<SuperCreator>,
    val popularPaidPosts: List<TopPost>,
    val popularFreePosts: List<TopPost>,
)
