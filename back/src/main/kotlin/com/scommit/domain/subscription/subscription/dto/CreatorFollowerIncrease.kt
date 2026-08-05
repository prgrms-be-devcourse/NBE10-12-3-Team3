package com.scommit.domain.subscription.subscription.dto

import com.scommit.domain.user.user.entity.User

data class CreatorFollowerIncrease(
    val creator: User,
    val followerIncrease: Long,
)
