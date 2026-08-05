package com.scommit.domain.payment.payment.service

import com.scommit.domain.payment.payment.dto.PaymentReadyRequest
import com.scommit.domain.payment.payment.entity.Payment
import com.scommit.domain.payment.payment.entity.PaymentStatus
import com.scommit.domain.payment.payment.repository.PaymentRepository
import com.scommit.domain.subscription.subscription.dto.SubscriptionStatus
import com.scommit.domain.subscription.subscription.service.SubscriptionService
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
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

/**
 * PaymentService 단위 테스트
 * - DB, Spring Context 없이 Mockito로 의존성을 가짜(Mock)로 대체
 * - 토스 승인 API를 실제로 호출하는 경로는 E2E/수동 검증 영역으로 남기고,
 *   여기서는 호출 이전에 차단되어야 하는 검증 로직을 다룬다.
 */
@ExtendWith(MockitoExtension::class)
class PaymentServiceTest {
    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var subscriptionService: SubscriptionService

    @Mock
    private lateinit var userRepository: UserRepository

    private lateinit var paymentService: PaymentService

    private lateinit var buyer: User
    private lateinit var creator: User

    @BeforeEach
    fun setUp() {
        paymentService =
            PaymentService(
                paymentRepository,
                subscriptionService,
                userRepository,
                "test_secret_key",
            )
        buyer = buildUser(BUYER_ID, "buyer@example.com", "구매자")
        creator = buildUser(CREATOR_ID, "creator@example.com", "창작자")
    }

    private fun buildUser(
        id: Long,
        email: String,
        nickname: String,
    ): User =
        User(
            email = email,
            nickname = nickname,
            role = UserRole.USER,
        ).also { ReflectionTestUtils.setField(it, "id", id) }

    private fun buildPayment(amount: Long = PaymentService.MEMBERSHIP_PRICE): Payment =
        Payment(
            user = buyer,
            orderId = ORDER_ID,
            orderName = "창작자 멤버십 구독",
            targetCreatorId = CREATOR_ID,
            amount = amount,
        )

    @Nested
    @DisplayName("결제 준비")
    inner class ReadyPayment {
        @Test
        @DisplayName("결제 금액은 클라이언트 입력이 아닌 서버 상수로 결정된다")
        fun amountIsDecidedByServer() {
            // given
            given(subscriptionService.getSubscriptionStatus(BUYER_ID, CREATOR_ID))
                .willReturn(SubscriptionStatus.NONE)
            given(userRepository.findById(BUYER_ID)).willReturn(Optional.of(buyer))
            given(userRepository.findById(CREATOR_ID)).willReturn(Optional.of(creator))

            // when
            val response = paymentService.readyPayment(BUYER_ID, PaymentReadyRequest(CREATOR_ID))

            // then: 응답 금액과 실제 저장 금액이 모두 서버 가격이어야 한다
            assertThat(response.amount).isEqualTo(PaymentService.MEMBERSHIP_PRICE)
            assertThat(response.orderName).isEqualTo("창작자 멤버십 구독")

            val saved = ArgumentCaptor.forClass(Payment::class.java)
            verify(paymentRepository).save(saved.capture())
            assertThat(saved.value.amount).isEqualTo(PaymentService.MEMBERSHIP_PRICE)
        }

        @Test
        @DisplayName("자기 자신에게는 결제를 시작할 수 없다")
        fun rejectSelfSubscription() {
            assertThatThrownBy { paymentService.readyPayment(BUYER_ID, PaymentReadyRequest(BUYER_ID)) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SELF_SUBSCRIPTION_NOT_ALLOWED)

            // 결제 건 자체가 생성되면 안 된다
            verify(paymentRepository, never()).save(any())
        }

        @Test
        @DisplayName("이미 멤버십인 창작자에게는 결제를 시작할 수 없다")
        fun rejectDuplicatedMembership() {
            given(subscriptionService.getSubscriptionStatus(BUYER_ID, CREATOR_ID))
                .willReturn(SubscriptionStatus.MEMBERSHIP)

            assertThatThrownBy { paymentService.readyPayment(BUYER_ID, PaymentReadyRequest(CREATOR_ID)) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_JOINED_MEMBERSHIP)

            verify(paymentRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("결제 승인")
    inner class ConfirmPayment {
        @Test
        @DisplayName("존재하지 않는 주문번호는 승인할 수 없다")
        fun rejectUnknownOrder() {
            given(paymentRepository.findWithUserByOrderId("UNKNOWN")).willReturn(null)

            assertThatThrownBy { paymentService.confirmPayment(BUYER_ID, "pk", "UNKNOWN") }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND)
        }

        @Test
        @DisplayName("타인의 결제 건은 승인할 수 없다")
        fun rejectOtherUsersPayment() {
            val payment = buildPayment()
            given(paymentRepository.findWithUserByOrderId(ORDER_ID)).willReturn(payment)

            assertThatThrownBy { paymentService.confirmPayment(OTHER_USER_ID, "pk", ORDER_ID) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_OWNER_MISMATCH)

            // 토스 승인 호출 이전에 차단되어야 하므로 상태는 그대로여야 한다
            assertThat(payment.status).isEqualTo(PaymentStatus.READY)
        }

        @Test
        @DisplayName("이미 승인 완료된 결제는 다시 승인할 수 없다")
        fun rejectAlreadyConfirmed() {
            val payment = buildPayment()
            payment.confirm("already_done_key")
            given(paymentRepository.findWithUserByOrderId(ORDER_ID)).willReturn(payment)

            assertThatThrownBy { paymentService.confirmPayment(BUYER_ID, "pk", ORDER_ID) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_ALREADY_PROCESSED)

            verify(subscriptionService, never()).joinMembership(anyLong(), anyLong())
        }
    }

    companion object {
        private const val BUYER_ID = 1L
        private const val CREATOR_ID = 2L
        private const val OTHER_USER_ID = 99L
        private const val ORDER_ID = "order_test123456789"
    }
}
