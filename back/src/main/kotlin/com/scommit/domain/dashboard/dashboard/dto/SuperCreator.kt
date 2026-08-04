package com.scommit.domain.dashboard.dashboard.dto

data class SuperCreator(
    val id: Long,
    val nickname: String,
    val subscriberCount: Long,
    val followerIncrease: Long,
    val profileImageUrl: String? = null,
    val introduction: String? = null,
)
