package com.scommit.domain.coupon.couponpolicy.service

import com.scommit.domain.coupon.couponpolicy.dto.CouponPolicyResponse
import com.scommit.domain.coupon.couponpolicy.entity.CouponPolicy
import com.scommit.domain.coupon.couponpolicy.entity.DiscountType
import com.scommit.domain.coupon.couponpolicy.entity.ExpiryType
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
@Transactional(readOnly = true)
class CouponPolicyService(
    private val couponPolicyRepository: CouponPolicyRepository,
    private val userRepository: UserRepository,
) {
    @Suppress("LongParameterList", "ThrowsCount")
    @Transactional
    fun createCouponPolicy(
        actor: User,
        title: String,
        description: String?,
        discountType: DiscountType,
        discountValue: Int,
        totalQuantity: Int,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        expiryType: ExpiryType,
        validDays: Int?,
        fixedExpiredAt: LocalDateTime?,
    ): CouponPolicyResponse {
        val user =
            userRepository
                .findByIdAndDeletedAtIsNull(actor.id)
                .orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
        if (user.role != UserRole.ADMIN) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }
        when (expiryType) {
            ExpiryType.RELATIVE -> {
                if (validDays == null) throw BusinessException(ErrorCode.INVALID_INPUT_VALUE)
            }

            ExpiryType.ABSOLUTE -> {
                if (fixedExpiredAt == null) throw BusinessException(ErrorCode.INVALID_INPUT_VALUE)
            }
        }

        val couponPolicy =
            CouponPolicy(
                title,
                description,
                discountType,
                discountValue,
                totalQuantity,
                startAt,
                endAt,
                expiryType,
                validDays,
                fixedExpiredAt,
            )
        couponPolicyRepository.save(couponPolicy)
        return CouponPolicyResponse(couponPolicy)
    }

    fun getActiveCouponPolicies(): List<CouponPolicyResponse> =
        couponPolicyRepository.findAllActive(LocalDateTime.now()).map { CouponPolicyResponse(it) }
}
