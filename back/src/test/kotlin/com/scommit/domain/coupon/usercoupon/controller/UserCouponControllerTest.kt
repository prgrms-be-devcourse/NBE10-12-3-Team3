package com.scommit.domain.coupon.usercoupon.controller

import com.scommit.domain.coupon.couponpolicy.entity.DiscountType
import com.scommit.domain.coupon.usercoupon.dto.UserCouponResponse
import com.scommit.domain.coupon.usercoupon.service.UserCouponService
import com.scommit.domain.user.user.entity.User
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import com.scommit.global.security.currentUser
import com.scommit.global.security.jwt.JwtFilter
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(
    controllers = [UserCouponController::class],
    excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [JwtFilter::class])],
)
class UserCouponControllerTest {
    private val mockActor = User(1L, "test@example.com", "테스터")

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var userCouponService: UserCouponService

    @Suppress("UnusedPrivateProperty")
    @MockitoBean
    private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    @Nested
    @DisplayName("POST /api/coupon-policies/{id}/issue - 쿠폰 발급")
    inner class IssueCoupon {
        @Test
        @DisplayName("성공: 200 응답과 발급된 쿠폰을 반환한다")
        fun issueCoupon_success() {
            given(userCouponService.issueCoupon(mockActor, 1L)).willReturn(sampleUserCouponResponse())

            mockMvc
                .perform(post("/api/coupon-policies/{id}/issue", 1L).with(currentUser(mockActor)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("쿠폰이 발급되었습니다."))
        }

        @Test
        @DisplayName("실패: 존재하지 않는 쿠폰 이벤트면 404를 반환한다")
        fun issueCoupon_policyNotFound() {
            willThrow(BusinessException(ErrorCode.COUPON_POLICY_NOT_FOUND))
                .given(userCouponService)
                .issueCoupon(mockActor, 999L)

            mockMvc
                .perform(post("/api/coupon-policies/{id}/issue", 999L).with(currentUser(mockActor)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-10"))
        }
    }

    @Nested
    @DisplayName("GET /api/coupons/me - 내 쿠폰 목록 조회")
    inner class GetMyCoupons {
        @Test
        @DisplayName("성공: 200 응답과 내 쿠폰 목록을 반환한다")
        fun getMyCoupons_success() {
            given(userCouponService.getMyCoupons(mockActor)).willReturn(listOf(sampleUserCouponResponse()))

            mockMvc
                .perform(get("/api/coupons/me").with(currentUser(mockActor)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.length()").value(1))
        }
    }

    private fun sampleUserCouponResponse() =
        UserCouponResponse(
            id = 1L,
            couponPolicyId = 1L,
            title = "여름 할인 쿠폰",
            discountType = DiscountType.PERCENT,
            discountValue = 10,
            issuedAt = LocalDateTime.of(2026, 8, 1, 0, 0),
            expiredAt = LocalDateTime.of(2026, 8, 8, 0, 0),
            usedAt = null,
        )
}
