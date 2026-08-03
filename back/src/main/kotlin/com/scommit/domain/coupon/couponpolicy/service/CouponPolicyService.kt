package com.scommit.domain.coupon.couponpolicy.service

import com.scommit.domain.coupon.couponpolicy.dto.CouponPolicyCreateRequest
import com.scommit.domain.coupon.couponpolicy.dto.CouponPolicyResponse
import com.scommit.domain.coupon.couponpolicy.entity.CouponPolicy
import com.scommit.domain.coupon.couponpolicy.repository.CouponPolicyRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CouponPolicyService(
    private val couponPolicyRepository: CouponPolicyRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createCouponPolicy(
        actor: User,
        request: CouponPolicyCreateRequest,
    ): CouponPolicyResponse {
        val user =
            userRepository.findByIdAndDeletedAtIsNull(actor.id)
                .orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
        if (user.role != UserRole.ADMIN) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        val couponPolicy =
            CouponPolicy(
                request.title,
                request.description,
                request.discountType,
                request.discountValue,
                request.totalQuantity,
                request.startAt,
                request.endAt,
                request.expiryType,
                request.validDays,
                request.fixedExpiredAt,
            )
        couponPolicyRepository.save(couponPolicy)
        return CouponPolicyResponse(couponPolicy)
    }

    fun getActiveCouponPolicies(): List<CouponPolicyResponse> =
        couponPolicyRepository.findAllActive(LocalDateTime.now()).map { CouponPolicyResponse(it) }
}
