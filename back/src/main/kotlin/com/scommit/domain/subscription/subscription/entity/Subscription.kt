package com.scommit.domain.subscription.subscription.entity

import com.scommit.domain.user.user.entity.User
import com.scommit.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
@Table(
    name = "subscriptions",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_subscription_user_creator",
            columnNames = ["user_id", "creator_id"]
        )
    ]
)
class Subscription(
    user: User,
    creator: User,
    tier: SubscriptionTier,
    startedAt: LocalDate? = null,
    expiredAt: LocalDate? = null
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User = user
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    var creator: User = creator
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false)
    var tier: SubscriptionTier = tier
        protected set

    @Column(name = "started_at")
    var startedAt: LocalDate? = startedAt
        protected set

    @Column(name = "expired_at")
    var expiredAt: LocalDate? = expiredAt
        protected set

    fun restoreSubscription() {
        this.tier = SubscriptionTier.FOLLOW
        this.startedAt = LocalDate.now()
        this.expiredAt = null
        this.restore()
    }

    fun upgradeToMembership(durationMonths: Long = 1L) {
        this.tier = SubscriptionTier.MEMBERSHIP
        this.expiredAt = LocalDate.now().plusMonths(durationMonths)
    }

    // 멤버십을 해지하고 팔로우 티어로 강등 처리
    fun downgradeToFollow() {
        this.tier = SubscriptionTier.FOLLOW
        this.expiredAt = null
    }
}
