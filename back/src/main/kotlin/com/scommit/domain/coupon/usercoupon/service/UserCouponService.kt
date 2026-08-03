package com.scommit.domain.coupon.usercoupon.service

import com.scommit.domain.coupon.couponpolicy.repository.CouponPolicyRepository
import com.scommit.domain.coupon.usercoupon.dto.UserCouponResponse
import com.scommit.domain.coupon.usercoupon.entity.UserCoupon
import com.scommit.domain.coupon.usercoupon.repository.UserCouponRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UserCouponService(
    private val couponPolicyRepository: CouponPolicyRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    @Transactional
    fun issueCoupon(
        actor: User,
        couponPolicyId: Long,
    ): UserCouponResponse {
        val couponPolicy =
            couponPolicyRepository.findByIdAndDeletedAtIsNull(couponPolicyId)
                ?: throw BusinessException(ErrorCode.COUPON_POLICY_NOT_FOUND)

        if (!couponPolicy.isActive()) {
            throw BusinessException(ErrorCode.COUPON_NOT_ACTIVE)
        }
        if (userCouponRepository.existsByUserAndCouponPolicy(actor, couponPolicy)) {
            throw BusinessException(ErrorCode.COUPON_ALREADY_ISSUED)
        }
        // TODO: 동시성 제어 없음 — 다음 단계에서 비관적 락 적용 예정.
        // 지금은 여러 요청이 동시에 들어오면 issuedQuantity가 totalQuantity를 넘어설 수 있다.
        if (couponPolicy.isSoldOut()) {
            throw BusinessException(ErrorCode.COUPON_SOLD_OUT)
        }

        couponPolicy.increaseIssuedQuantity()
        val issuedAt = LocalDateTime.now()
        val userCoupon = UserCoupon(actor, couponPolicy, issuedAt, couponPolicy.calculateExpiredAt(issuedAt))
        userCouponRepository.save(userCoupon)
        return UserCouponResponse(userCoupon)
    }

    fun getMyCoupons(actor: User): List<UserCouponResponse> =
        userCouponRepository.findAllByUser(actor).map { UserCouponResponse(it) }
}
