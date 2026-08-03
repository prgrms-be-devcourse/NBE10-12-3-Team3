package com.scommit.domain.coupon.couponpolicy.service

import com.scommit.domain.coupon.couponpolicy.dto.CouponPolicyCreateRequest
import com.scommit.domain.coupon.couponpolicy.entity.CouponPolicy
import com.scommit.domain.coupon.couponpolicy.entity.DiscountType
import com.scommit.domain.coupon.couponpolicy.entity.ExpiryType
import com.scommit.domain.coupon.couponpolicy.repository.CouponPolicyRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.Optional

// any()는 null을 반환하는데, findAllActive(now: LocalDateTime)처럼 Kotlin에서 선언한
// non-null 파라미터에 그대로 넘기면 실패한다. UserServiceTest.kt와 동일한 워크어라운드.
private fun <T> anyOfType(): T {
    any<T>()
    @Suppress("UNCHECKED_CAST")
    return null as T
}

/**
 * CouponPolicyService 단위 테스트
 * - DB, Spring Context 없이 Mockito로 의존성을 가짜(Mock)로 대체
 */
@ExtendWith(MockitoExtension::class)
class CouponPolicyServiceTest {
    @Mock
    private lateinit var couponPolicyRepository: CouponPolicyRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @InjectMocks
    private lateinit var couponPolicyService: CouponPolicyService

    private lateinit var adminUser: User
    private lateinit var normalUser: User

    @BeforeEach
    fun setUp() {
        adminUser =
            User
                .builder()
                .email("admin@example.com")
                .nickname("관리자")
                .role(UserRole.ADMIN)
                .build()
                .also { ReflectionTestUtils.setField(it, "id", 1L) }

        normalUser =
            User
                .builder()
                .email("user@example.com")
                .nickname("유저")
                .role(UserRole.USER)
                .build()
                .also { ReflectionTestUtils.setField(it, "id", 2L) }
    }

    private fun buildRequest(
        expiryType: ExpiryType = ExpiryType.RELATIVE,
        validDays: Int? = 7,
        fixedExpiredAt: LocalDateTime? = null,
    ) = CouponPolicyCreateRequest(
        title = "여름 할인 이벤트",
        description = "선착순 100명",
        discountType = DiscountType.PERCENT,
        discountValue = 10,
        totalQuantity = 100,
        startAt = LocalDateTime.now().minusDays(1),
        endAt = LocalDateTime.now().plusDays(7),
        expiryType = expiryType,
        validDays = validDays,
        fixedExpiredAt = fixedExpiredAt,
    )

    private fun buildPolicy(
        id: Long,
        expiryType: ExpiryType = ExpiryType.RELATIVE,
        validDays: Int? = 7,
        fixedExpiredAt: LocalDateTime? = null,
    ): CouponPolicy =
        CouponPolicy(
            "여름 할인 이벤트",
            "선착순 100명",
            DiscountType.PERCENT,
            10,
            100,
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(7),
            expiryType,
            validDays,
            fixedExpiredAt,
        ).also { ReflectionTestUtils.setField(it, "id", id) }

    @Nested
    @DisplayName("쿠폰 이벤트 생성 테스트")
    inner class CreateCouponPolicy {
        @Test
        @DisplayName("성공: 관리자가 RELATIVE 방식 이벤트를 생성한다.")
        fun create_Success_Relative() {
            given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(adminUser))
            given(couponPolicyRepository.save(any<CouponPolicy>())).willAnswer { it.arguments[0] }

            val response = couponPolicyService.createCouponPolicy(adminUser, buildRequest())

            assertThat(response.title).isEqualTo("여름 할인 이벤트")
            assertThat(response.expiryType).isEqualTo(ExpiryType.RELATIVE)
            assertThat(response.validDays).isEqualTo(7)
        }

        @Test
        @DisplayName("성공: 관리자가 ABSOLUTE 방식 이벤트를 생성한다.")
        fun create_Success_Absolute() {
            val fixedExpiredAt = LocalDateTime.of(2026, 12, 31, 23, 59, 59)
            given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(adminUser))
            given(couponPolicyRepository.save(any<CouponPolicy>())).willAnswer { it.arguments[0] }

            val response =
                couponPolicyService.createCouponPolicy(
                    adminUser,
                    buildRequest(expiryType = ExpiryType.ABSOLUTE, validDays = null, fixedExpiredAt = fixedExpiredAt),
                )

            assertThat(response.expiryType).isEqualTo(ExpiryType.ABSOLUTE)
            assertThat(response.fixedExpiredAt).isEqualTo(fixedExpiredAt)
        }

        @Test
        @DisplayName("실패: 관리자가 아니면 ACCESS_DENIED 예외를 던진다.")
        fun create_Fail_NotAdmin() {
            given(userRepository.findByIdAndDeletedAtIsNull(2L)).willReturn(Optional.of(normalUser))

            assertThatThrownBy { couponPolicyService.createCouponPolicy(normalUser, buildRequest()) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }

        @Test
        @DisplayName("실패: 존재하지 않는 유저면 USER_NOT_FOUND 예외를 던진다.")
        fun create_Fail_UserNotFound() {
            given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty())

            assertThatThrownBy { couponPolicyService.createCouponPolicy(adminUser, buildRequest()) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("진행 중인 쿠폰 이벤트 조회 테스트")
    inner class GetActiveCouponPolicies {
        @Test
        @DisplayName("성공: 진행 중인 이벤트 목록을 반환한다.")
        fun getActive_Success() {
            val policy = buildPolicy(1L)
            given(couponPolicyRepository.findAllActive(anyOfType())).willReturn(listOf(policy))

            val result = couponPolicyService.getActiveCouponPolicies()

            assertThat(result).hasSize(1)
            assertThat(result[0].title).isEqualTo("여름 할인 이벤트")
        }

        @Test
        @DisplayName("성공: 진행 중인 이벤트가 없으면 빈 목록을 반환한다.")
        fun getActive_Empty() {
            given(couponPolicyRepository.findAllActive(anyOfType())).willReturn(emptyList())

            val result = couponPolicyService.getActiveCouponPolicies()

            assertThat(result).isEmpty()
        }
    }
}
