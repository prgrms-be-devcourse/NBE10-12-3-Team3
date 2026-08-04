package com.scommit.domain.coupon.usercoupon.repository

import com.scommit.domain.coupon.couponpolicy.entity.CouponPolicy
import com.scommit.domain.coupon.usercoupon.entity.UserCoupon
import com.scommit.domain.user.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserCouponRepository : JpaRepository<UserCoupon, Long> {
    fun existsByUserAndCouponPolicy(
        user: User,
        couponPolicy: CouponPolicy,
    ): Boolean

    fun findAllByUser(user: User): List<UserCoupon>
}
