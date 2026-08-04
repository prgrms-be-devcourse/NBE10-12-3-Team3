package com.scommit.domain.coupon.couponpolicy.dto

import com.scommit.domain.coupon.couponpolicy.entity.DiscountType
import com.scommit.domain.coupon.couponpolicy.entity.ExpiryType
import java.time.LocalDateTime

data class CouponPolicyCreateRequest(
    val title: String,
    val description: String?,
    val discountType: DiscountType,
    val discountValue: Int,
    val totalQuantity: Int,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val expiryType: ExpiryType,
    // expiryType이 RELATIVE면 validDays, ABSOLUTE면 fixedExpiredAt만 채운다.
    val validDays: Int?,
    val fixedExpiredAt: LocalDateTime?,
)
