package com.scommit.domain.coupon.couponpolicy.entity

import com.scommit.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "coupon_policies")
class CouponPolicy(
    @Column(nullable = false)
    val title: String,
    @Column(columnDefinition = "TEXT")
    val description: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    val discountType: DiscountType,
    @Column(name = "discount_value", nullable = false)
    val discountValue: Int,
    @Column(name = "total_quantity", nullable = false)
    val totalQuantity: Int,
    @Column(name = "start_at", nullable = false)
    val startAt: LocalDateTime,
    @Column(name = "end_at", nullable = false)
    val endAt: LocalDateTime,
    // 발급받은 쿠폰의 사용 기한을 어떻게 정할지 — RELATIVE면 validDays, ABSOLUTE면 fixedExpiredAt을 쓴다.
    @Enumerated(EnumType.STRING)
    @Column(name = "expiry_type", nullable = false)
    val expiryType: ExpiryType,
    // RELATIVE일 때만 사용: 발급일로부터 며칠 후 만료되는지 (예: 7 → 발급일 + 7일)
    @Column(name = "valid_days")
    val validDays: Int?,
    // ABSOLUTE일 때만 사용: 발급 시점과 무관하게 모두 이 날짜에 만료
    @Column(name = "fixed_expired_at")
    val fixedExpiredAt: LocalDateTime?,
) : BaseEntity() {
    init {
        when (expiryType) {
            ExpiryType.RELATIVE ->
                require(validDays != null) { "RELATIVE 정책은 validDays가 필요합니다." }
            ExpiryType.ABSOLUTE ->
                require(fixedExpiredAt != null) { "ABSOLUTE 정책은 fixedExpiredAt이 필요합니다." }
        }
    }

    @Column(name = "issued_quantity", nullable = false)
    var issuedQuantity: Int = 0
        protected set

    fun increaseIssuedQuantity() {
        issuedQuantity++
    }

    fun isSoldOut(): Boolean = issuedQuantity >= totalQuantity

    fun isActive(now: LocalDateTime = LocalDateTime.now()): Boolean =
        deletedAt == null && !now.isBefore(startAt) && !now.isAfter(endAt)

    // 발급 시점에 실제 만료 시각을 계산해 UserCoupon에 고정 저장할 때 쓴다.
    fun calculateExpiredAt(issuedAt: LocalDateTime): LocalDateTime =
        when (expiryType) {
            ExpiryType.RELATIVE -> issuedAt.plusDays(validDays!!.toLong())
            ExpiryType.ABSOLUTE -> fixedExpiredAt!!
        }
}
