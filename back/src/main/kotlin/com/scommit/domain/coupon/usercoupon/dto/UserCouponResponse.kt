package com.scommit.domain.coupon.usercoupon.dto

import com.scommit.domain.coupon.couponpolicy.entity.DiscountType
import com.scommit.domain.coupon.usercoupon.entity.UserCoupon
import java.time.LocalDateTime

data class UserCouponResponse(
    val id: Long?,
    val couponPolicyId: Long?,
    val title: String,
    val discountType: DiscountType,
    val discountValue: Int,
    val issuedAt: LocalDateTime,
    val expiredAt: LocalDateTime,
    val usedAt: LocalDateTime?,
) {
    constructor(userCoupon: UserCoupon) : this(
        userCoupon.id,
        userCoupon.couponPolicy.id,
        userCoupon.couponPolicy.title,
        userCoupon.couponPolicy.discountType,
        userCoupon.couponPolicy.discountValue,
        userCoupon.issuedAt,
        userCoupon.expiredAt,
        userCoupon.usedAt,
    )
}
