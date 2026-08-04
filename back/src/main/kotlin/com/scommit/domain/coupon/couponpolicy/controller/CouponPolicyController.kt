package com.scommit.domain.coupon.couponpolicy.controller

import com.scommit.domain.coupon.couponpolicy.dto.CouponPolicyCreateRequest
import com.scommit.domain.coupon.couponpolicy.dto.CouponPolicyResponse
import com.scommit.domain.coupon.couponpolicy.service.CouponPolicyService
import com.scommit.global.dto.RsData
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import com.scommit.global.security.SecurityHelper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Coupon", description = "쿠폰 관련 API")
@RestController
class CouponPolicyController(
    private val couponPolicyService: CouponPolicyService,
    private val securityHelper: SecurityHelper,
) {
    // POST /api/admin/coupon-policies 쿠폰 이벤트 생성 (관리자 전용)
    @Operation(summary = "쿠폰 이벤트 생성", description = "관리자가 선착순 쿠폰 이벤트를 생성합니다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/admin/coupon-policies")
    fun createCouponPolicy(
        @RequestBody request: CouponPolicyCreateRequest,
    ): RsData<CouponPolicyResponse> {
        val actor = securityHelper.actor ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val response =
            couponPolicyService.createCouponPolicy(
                actor,
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
        return RsData("201-1", "쿠폰 이벤트가 생성되었습니다.", response)
    }

    // GET /api/coupon-policies/active 진행 중인 쿠폰 이벤트 조회 (인증 불필요)
    @Operation(summary = "진행 중인 쿠폰 이벤트 조회", description = "발급 가능 기간 안에 있는 쿠폰 이벤트 목록을 조회합니다.")
    @GetMapping("/api/coupon-policies/active")
    fun getActiveCouponPolicies(): RsData<List<CouponPolicyResponse>> {
        val response = couponPolicyService.getActiveCouponPolicies()
        return RsData("200-1", "진행 중인 쿠폰 이벤트 목록입니다.", response)
    }
}
