package com.scommit.domain.subscription.subscription.dto

import com.scommit.domain.subscription.subscription.entity.Subscription
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier
import java.time.LocalDate

data class SubscriptionInfo(
    val creatorId: Long,
    val nickname: String,
    val creatorProfileImage: String, // 창작자(타겟)의 프로필 이미지
    val tier: SubscriptionTier,
    val startedAt: LocalDate?,
    val expiredAt: LocalDate?,
    val followerCount: Long,
) {
    companion object {
        fun from(
            subscription: Subscription,
            followerCount: Long,
        ): SubscriptionInfo =
            SubscriptionInfo(
                creatorId = subscription.creator.id ?: error("Creator ID cannot be null"),
                nickname = subscription.creator.nickname,
                // UserMedia 분리 후 수정 필요
                creatorProfileImage = "",
                tier = subscription.tier,
                startedAt = subscription.startedAt,
                expiredAt = subscription.expiredAt,
                followerCount = followerCount,
            )
    }
}
