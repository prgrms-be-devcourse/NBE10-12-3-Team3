package com.scommit.domain.coupon.couponpolicy.controller

import com.scommit.domain.coupon.couponpolicy.dto.CouponPolicyCreateRequest
import com.scommit.domain.coupon.couponpolicy.dto.CouponPolicyResponse
import com.scommit.domain.coupon.couponpolicy.entity.DiscountType
import com.scommit.domain.coupon.couponpolicy.entity.ExpiryType
import com.scommit.domain.coupon.couponpolicy.service.CouponPolicyService
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
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

@WebMvcTest(
    controllers = [CouponPolicyController::class],
    excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [JwtFilter::class])],
)
class CouponPolicyControllerTest {
    private val mockActor = User(1L, "admin@example.com", "관리자")

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var couponPolicyService: CouponPolicyService

    @Suppress("UnusedPrivateProperty")
    @MockitoBean
    private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    @Nested
    @DisplayName("POST /api/admin/coupon-policies - 쿠폰 이벤트 생성")
    inner class CreateCouponPolicy {
        @Test
        @DisplayName("성공: 201 응답과 생성된 쿠폰 이벤트를 반환한다")
        fun createCouponPolicy_success() {
            val request = sampleRequest()
            given(
                couponPolicyService.createCouponPolicy(
                    mockActor,
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
                ),
            ).willReturn(sampleResponse())

            mockMvc
                .perform(
                    post("/api/admin/coupon-policies")
                        .with(currentUser(mockActor))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.msg").value("쿠폰 이벤트가 생성되었습니다."))
        }

        @Test
        @DisplayName("실패: 관리자가 아니면 403을 반환한다")
        fun createCouponPolicy_accessDenied() {
            val request = sampleRequest()
            willThrow(BusinessException(ErrorCode.ACCESS_DENIED))
                .given(couponPolicyService)
                .createCouponPolicy(
                    mockActor,
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

            mockMvc
                .perform(
                    post("/api/admin/coupon-policies")
                        .with(currentUser(mockActor))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-1"))
        }
    }

    private fun sampleRequest() =
        CouponPolicyCreateRequest(
            title = "여름 할인 이벤트",
            description = "여름 시즌 한정 쿠폰",
            discountType = DiscountType.PERCENT,
            discountValue = 10,
            totalQuantity = 100,
            startAt = LocalDateTime.of(2026, 8, 1, 0, 0),
            endAt = LocalDateTime.of(2026, 8, 31, 23, 59),
            expiryType = ExpiryType.RELATIVE,
            validDays = 7,
            fixedExpiredAt = null,
        )

    private fun sampleResponse() =
        CouponPolicyResponse(
            id = 1L,
            title = "여름 할인 이벤트",
            description = "여름 시즌 한정 쿠폰",
            discountType = DiscountType.PERCENT,
            discountValue = 10,
            totalQuantity = 100,
            issuedQuantity = 0,
            startAt = LocalDateTime.of(2026, 8, 1, 0, 0),
            endAt = LocalDateTime.of(2026, 8, 31, 23, 59),
            expiryType = ExpiryType.RELATIVE,
            validDays = 7,
            fixedExpiredAt = null,
        )
}
