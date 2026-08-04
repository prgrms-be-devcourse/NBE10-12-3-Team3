package com.scommit.domain.subscription.subscription.dto

import com.scommit.domain.subscription.subscription.entity.SubscriptionTier
import java.time.LocalDate

data class SubscriptionResponse(
    val creatorId: Long,
    val nickname: String,
    val creatorProfileImage: String,
    val tier: SubscriptionTier,
    val startedAt: LocalDate?,
    val expiredAt: LocalDate?,
    val followerCount: Long,
) {
    companion object {
        fun from(info: SubscriptionInfo): SubscriptionResponse =
            SubscriptionResponse(
                creatorId = info.creatorId,
                nickname = info.nickname,
                creatorProfileImage = info.creatorProfileImage,
                tier = info.tier,
                startedAt = info.startedAt,
                expiredAt = info.expiredAt,
                followerCount = info.followerCount,
            )
    }
}
