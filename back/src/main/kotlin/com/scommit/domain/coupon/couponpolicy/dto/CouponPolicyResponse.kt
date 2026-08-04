package com.scommit.domain.coupon.couponpolicy.dto

import com.scommit.domain.coupon.couponpolicy.entity.CouponPolicy
import com.scommit.domain.coupon.couponpolicy.entity.DiscountType
import com.scommit.domain.coupon.couponpolicy.entity.ExpiryType
import java.time.LocalDateTime

data class CouponPolicyResponse(
    val id: Long?,
    val title: String,
    val description: String?,
    val discountType: DiscountType,
    val discountValue: Int,
    val totalQuantity: Int,
    val issuedQuantity: Int,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val expiryType: ExpiryType,
    val validDays: Int?,
    val fixedExpiredAt: LocalDateTime?,
) {
    constructor(couponPolicy: CouponPolicy) : this(
        couponPolicy.id,
        couponPolicy.title,
        couponPolicy.description,
        couponPolicy.discountType,
        couponPolicy.discountValue,
        couponPolicy.totalQuantity,
        couponPolicy.issuedQuantity,
        couponPolicy.startAt,
        couponPolicy.endAt,
        couponPolicy.expiryType,
        couponPolicy.validDays,
        couponPolicy.fixedExpiredAt,
    )
}
