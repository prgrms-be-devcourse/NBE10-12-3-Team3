package com.scommit.domain.coupon.usercoupon.controller

import com.scommit.domain.coupon.usercoupon.dto.UserCouponResponse
import com.scommit.domain.coupon.usercoupon.service.UserCouponService
import com.scommit.domain.user.user.entity.User
import com.scommit.global.dto.RsData
import com.scommit.global.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Coupon", description = "쿠폰 관련 API")
@RestController
@RequestMapping("/api")
class UserCouponController(
    private val userCouponService: UserCouponService,
) {
    // POST /api/coupon-policies/{id}/issue 쿠폰 발급받기 (선착순)
    @Operation(summary = "쿠폰 발급", description = "로그인한 유저가 특정 쿠폰 이벤트의 쿠폰을 선착순으로 발급받습니다.")
    @PostMapping("/coupon-policies/{id}/issue")
    fun issueCoupon(
        @CurrentUser actor: User,
        @PathVariable id: Long,
    ): RsData<UserCouponResponse> {
        val response = userCouponService.issueCoupon(actor, id)
        return RsData("200-1", "쿠폰이 발급되었습니다.", response)
    }

    // GET /api/coupons/me 내가 발급받은 쿠폰 목록 조회
    @Operation(summary = "내 쿠폰 목록 조회", description = "로그인한 유저가 발급받은 쿠폰 목록을 조회합니다.")
    @GetMapping("/coupons/me")
    fun getMyCoupons(
        @CurrentUser actor: User,
    ): RsData<List<UserCouponResponse>> {
        val response = userCouponService.getMyCoupons(actor)
        return RsData("200-1", "내 쿠폰 목록입니다.", response)
    }
}
