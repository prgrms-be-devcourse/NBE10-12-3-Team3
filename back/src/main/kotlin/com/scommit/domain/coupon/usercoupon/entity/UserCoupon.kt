package com.scommit.domain.coupon.usercoupon.entity

import com.scommit.domain.coupon.couponpolicy.entity.CouponPolicy
import com.scommit.domain.user.user.entity.User
import com.scommit.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "coupon_policy_id"])],
)
class UserCoupon(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_policy_id", nullable = false)
    val couponPolicy: CouponPolicy,
    @Column(name = "issued_at", nullable = false)
    val issuedAt: LocalDateTime,
    // couponPolicy.calculateExpiredAt(issuedAt)으로 계산해 발급 시점에 고정한다 (정책이 나중에 바뀌어도 이미 발급된 쿠폰엔 영향 없어야 하므로).
    @Column(name = "expired_at", nullable = false)
    val expiredAt: LocalDateTime,
) : BaseEntity() {
    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null
        protected set

    fun isExpired(now: LocalDateTime = LocalDateTime.now()): Boolean = now.isAfter(expiredAt)
}
