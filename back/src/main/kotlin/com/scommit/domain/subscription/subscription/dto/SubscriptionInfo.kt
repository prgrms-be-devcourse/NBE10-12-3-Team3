package com.scommit.domain.subscription.subscription.dto

import com.scommit.domain.subscription.subscription.entity.SubscriptionTier
import com.scommit.domain.subscription.subscription.entity.Subscription
import java.time.LocalDate

data class SubscriptionInfo(
    val creatorId: Long,
    val nickname: String,
    val creatorProfileImage: String, // 창작자(타겟)의 프로필 이미지
    val tier: SubscriptionTier,
    val startedAt: LocalDate?,
    val expiredAt: LocalDate?,
    val followerCount: Long
) {
    companion object {
        fun from(subscription: Subscription, followerCount: Long): SubscriptionInfo {
            return SubscriptionInfo(
                creatorId = subscription.creator.id ?: throw IllegalStateException("Creator ID cannot be null"),
                nickname = subscription.creator.nickname,
                // TODO: UserMedia 분리 후 수정 필요
                creatorProfileImage = "",
                tier = subscription.tier,
                startedAt = subscription.startedAt,
                expiredAt = subscription.expiredAt,
                followerCount = followerCount
            )
        }
    }
}
