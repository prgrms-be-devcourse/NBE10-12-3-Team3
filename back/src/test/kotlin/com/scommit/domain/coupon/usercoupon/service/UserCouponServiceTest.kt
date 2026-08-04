package com.scommit.domain.coupon.usercoupon.service

import com.scommit.domain.coupon.couponpolicy.entity.CouponPolicy
import com.scommit.domain.coupon.couponpolicy.entity.DiscountType
import com.scommit.domain.coupon.couponpolicy.entity.ExpiryType
import com.scommit.domain.coupon.couponpolicy.repository.CouponPolicyRepository
import com.scommit.domain.coupon.usercoupon.repository.UserCouponRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * UserCouponService 단위 테스트
 * - DB, Spring Context 없이 Mockito로 의존성을 가짜(Mock)로 대체
 */
@ExtendWith(MockitoExtension::class)
class UserCouponServiceTest {
    @Mock
    private lateinit var couponPolicyRepository: CouponPolicyRepository

    @Mock
    private lateinit var userCouponRepository: UserCouponRepository

    @InjectMocks
    private lateinit var userCouponService: UserCouponService

    private lateinit var actor: User

    @BeforeEach
    fun setUp() {
        actor =
            User
                .builder()
                .email("test@example.com")
                .nickname("테스터")
                .role(UserRole.USER)
                .build()
                .also { ReflectionTestUtils.setField(it, "id", 1L) }
    }

    @Suppress("LongParameterList")
    private fun buildPolicy(
        id: Long,
        totalQuantity: Int = 100,
        issuedQuantity: Int = 0,
        startAt: LocalDateTime = LocalDateTime.now().minusDays(1),
        endAt: LocalDateTime = LocalDateTime.now().plusDays(7),
        expiryType: ExpiryType = ExpiryType.RELATIVE,
        validDays: Int? = 7,
        fixedExpiredAt: LocalDateTime? = null,
    ): CouponPolicy =
        CouponPolicy(
            "여름 할인 이벤트",
            "선착순 100명",
            DiscountType.PERCENT,
            10,
            totalQuantity,
            startAt,
            endAt,
            expiryType,
            validDays,
            fixedExpiredAt,
        ).also {
            ReflectionTestUtils.setField(it, "id", id)
            ReflectionTestUtils.setField(it, "issuedQuantity", issuedQuantity)
        }

    @Nested
    @DisplayName("쿠폰 발급 테스트")
    inner class IssueCoupon {
        @Test
        @DisplayName("성공: RELATIVE 정책이면 발급일 + validDays로 만료일이 계산된다.")
        fun issue_Success_Relative() {
            val policy = buildPolicy(1L, validDays = 7)
            given(couponPolicyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(policy)
            given(userCouponRepository.existsByUserAndCouponPolicy(actor, policy)).willReturn(false)

            val response = userCouponService.issueCoupon(actor, 1L)

            assertThat(response.couponPolicyId).isEqualTo(1L)
            assertThat(policy.issuedQuantity).isEqualTo(1)
            assertThat(response.expiredAt).isCloseTo(response.issuedAt.plusDays(7), within(1, ChronoUnit.SECONDS))
        }

        @Test
        @DisplayName("성공: ABSOLUTE 정책이면 고정된 날짜로 만료일이 설정된다.")
        fun issue_Success_Absolute() {
            val fixedExpiredAt = LocalDateTime.of(2026, 12, 31, 23, 59, 59)
            val policy =
                buildPolicy(1L, expiryType = ExpiryType.ABSOLUTE, validDays = null, fixedExpiredAt = fixedExpiredAt)
            given(couponPolicyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(policy)
            given(userCouponRepository.existsByUserAndCouponPolicy(actor, policy)).willReturn(false)

            val response = userCouponService.issueCoupon(actor, 1L)

            assertThat(response.expiredAt).isEqualTo(fixedExpiredAt)
        }

        @Test
        @DisplayName("실패: 존재하지 않는 쿠폰 이벤트면 COUPON_POLICY_NOT_FOUND 예외를 던진다.")
        fun issue_Fail_PolicyNotFound() {
            given(couponPolicyRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(null)

            assertThatThrownBy { userCouponService.issueCoupon(actor, 999L) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_POLICY_NOT_FOUND)
        }

        @Test
        @DisplayName("실패: 발급 가능 기간이 아니면 COUPON_NOT_ACTIVE 예외를 던진다.")
        fun issue_Fail_NotActive() {
            val policy =
                buildPolicy(
                    1L,
                    startAt = LocalDateTime.now().plusDays(1),
                    endAt = LocalDateTime.now().plusDays(10),
                )
            given(couponPolicyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(policy)

            assertThatThrownBy { userCouponService.issueCoupon(actor, 1L) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_NOT_ACTIVE)
        }

        @Test
        @DisplayName("실패: 이미 발급받은 유저면 COUPON_ALREADY_ISSUED 예외를 던진다.")
        fun issue_Fail_AlreadyIssued() {
            val policy = buildPolicy(1L)
            given(couponPolicyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(policy)
            given(userCouponRepository.existsByUserAndCouponPolicy(actor, policy)).willReturn(true)

            assertThatThrownBy { userCouponService.issueCoupon(actor, 1L) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_ALREADY_ISSUED)
        }

        @Test
        @DisplayName("실패: 이미 소진된 이벤트면 COUPON_SOLD_OUT 예외를 던진다.")
        fun issue_Fail_SoldOut() {
            val policy = buildPolicy(1L, totalQuantity = 10, issuedQuantity = 10)
            given(couponPolicyRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(policy)
            given(userCouponRepository.existsByUserAndCouponPolicy(actor, policy)).willReturn(false)

            assertThatThrownBy { userCouponService.issueCoupon(actor, 1L) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_SOLD_OUT)

            // 실패한 요청이 수량을 건드리지 않았는지 확인한다.
            assertThat(policy.issuedQuantity).isEqualTo(10)
        }
    }

    @Nested
    @DisplayName("내 쿠폰 목록 조회 테스트")
    inner class GetMyCoupons {
        @Test
        @DisplayName("성공: 발급받은 쿠폰이 없으면 빈 목록을 반환한다.")
        fun getMyCoupons_Empty() {
            given(userCouponRepository.findAllByUser(actor)).willReturn(emptyList())

            val result = userCouponService.getMyCoupons(actor)

            assertThat(result).isEmpty()
        }
    }
}
