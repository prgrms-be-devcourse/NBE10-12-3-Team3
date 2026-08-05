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
import org.mockito.BDDMockito.willThrow
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.util.Optional

/**
 * PaymentService 단위 테스트
 * - DB, Spring Context 없이 Mockito로 의존성을 가짜(Mock)로 대체
 * - 토스 승인 API는 MockRestServiceServer로 대체해 실제 외부 호출 없이 검증한다.
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
    private lateinit var tossServer: MockRestServiceServer

    private lateinit var buyer: User
    private lateinit var creator: User

    @BeforeEach
    fun setUp() {
        // 빌더에 목 서버를 먼저 바인딩한 뒤 서비스에 넘겨야 서비스가 만든 클라이언트가 가로채진다.
        val builder = RestClient.builder()
        tossServer = MockRestServiceServer.bindTo(builder).build()

        paymentService =
            PaymentService(
                paymentRepository,
                subscriptionService,
                userRepository,
                SingletonObjectProvider(builder),
                TEST_SECRET_KEY,
            )
        buyer = buildUser(BUYER_ID, "buyer@example.com", "구매자")
        creator = buildUser(CREATOR_ID, "creator@example.com", "창작자")
    }

    /** 토스 승인 API가 주어진 본문으로 200을 반환하도록 세운다. */
    private fun expectTossConfirm(responseBody: String) {
        tossServer
            .expect(requestTo(TOSS_CONFIRM_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON))
    }

    private fun tossResponse(
        status: String,
        totalAmount: Long,
    ): String = """{"paymentKey":"$PAYMENT_KEY","orderId":"$ORDER_ID","status":"$status","totalAmount":$totalAmount}"""

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

    private fun buildPayment(amount: Long = 9_900L): Payment =
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

            // then: 응답 금액과 실제 저장 금액이 모두 서버가 정한 가격이어야 한다.
            // 상수끼리 비교하면 가격이 바뀌어도 통과하므로 리터럴로 못박는다.
            assertThat(response.amount).isEqualTo(9_900L)
            assertThat(response.orderName).isEqualTo("창작자 멤버십 구독")

            val saved = ArgumentCaptor.forClass(Payment::class.java)
            verify(paymentRepository).save(saved.capture())
            assertThat(saved.value.amount).isEqualTo(9_900L)
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

    @Nested
    @DisplayName("토스 승인 연동")
    inner class TossConfirm {
        @Test
        @DisplayName("토스에는 클라이언트가 아닌 서버가 보관 중인 금액이 전송된다")
        fun sendsServerAmountToToss() {
            val payment = buildPayment()
            given(paymentRepository.findWithUserByOrderId(ORDER_ID)).willReturn(payment)

            tossServer
                .expect(requestTo(TOSS_CONFIRM_URL))
                .andExpect(method(HttpMethod.POST))
                // 재시도로 인한 중복 승인을 토스가 막을 수 있도록 멱등키가 실려야 한다
                .andExpect(header("Idempotency-Key", ORDER_ID))
                .andExpect(
                    content().json(
                        """{"paymentKey":"$PAYMENT_KEY","orderId":"$ORDER_ID","amount":9900}""",
                    ),
                ).andRespond(
                    withSuccess(tossResponse("DONE", 9_900L), MediaType.APPLICATION_JSON),
                )

            paymentService.confirmPayment(BUYER_ID, PAYMENT_KEY, ORDER_ID)

            tossServer.verify()
        }

        @Test
        @DisplayName("정상 승인되면 DONE으로 저장한 뒤 멤버십을 부여한다")
        fun confirmsAndGrantsMembership() {
            val payment = buildPayment()
            given(paymentRepository.findWithUserByOrderId(ORDER_ID)).willReturn(payment)
            expectTossConfirm(tossResponse("DONE", 9_900L))

            paymentService.confirmPayment(BUYER_ID, PAYMENT_KEY, ORDER_ID)

            assertThat(payment.status).isEqualTo(PaymentStatus.DONE)
            assertThat(payment.paymentKey).isEqualTo(PAYMENT_KEY)
            verify(paymentRepository).save(payment)
            verify(subscriptionService).joinMembership(BUYER_ID, CREATOR_ID)
        }

        @Test
        @DisplayName("가상계좌처럼 입금 대기 상태면 승인으로 처리하지 않는다")
        fun rejectsWaitingForDeposit() {
            val payment = buildPayment()
            given(paymentRepository.findWithUserByOrderId(ORDER_ID)).willReturn(payment)
            expectTossConfirm(tossResponse("WAITING_FOR_DEPOSIT", 9_900L))

            assertThatThrownBy { paymentService.confirmPayment(BUYER_ID, PAYMENT_KEY, ORDER_ID) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_APPROVED)

            assertThat(payment.status).isEqualTo(PaymentStatus.ABORTED)
            // 입금 전에 멤버십이 부여되면 안 된다
            verify(subscriptionService, never()).joinMembership(anyLong(), anyLong())
        }

        @Test
        @DisplayName("토스가 승인한 금액이 서버 금액과 다르면 승인을 거부한다")
        fun rejectsAmountMismatch() {
            val payment = buildPayment()
            given(paymentRepository.findWithUserByOrderId(ORDER_ID)).willReturn(payment)
            expectTossConfirm(tossResponse("DONE", 100L))

            assertThatThrownBy { paymentService.confirmPayment(BUYER_ID, PAYMENT_KEY, ORDER_ID) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_APPROVED)

            assertThat(payment.status).isEqualTo(PaymentStatus.ABORTED)
            verify(subscriptionService, never()).joinMembership(anyLong(), anyLong())
        }

        @Test
        @DisplayName("토스 API 호출이 실패하면 게이트웨이 오류로 처리하고 실패를 기록한다")
        fun handlesTossApiFailure() {
            val payment = buildPayment()
            given(paymentRepository.findWithUserByOrderId(ORDER_ID)).willReturn(payment)

            tossServer
                .expect(requestTo(TOSS_CONFIRM_URL))
                .andRespond(withServerError())

            assertThatThrownBy { paymentService.confirmPayment(BUYER_ID, PAYMENT_KEY, ORDER_ID) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_GATEWAY_ERROR)

            assertThat(payment.status).isEqualTo(PaymentStatus.ABORTED)
            verify(paymentRepository).save(payment)
        }

        @Test
        @DisplayName("멤버십 승급에 실패해도 이미 청구된 결제는 DONE으로 남는다")
        fun keepsPaymentDoneWhenMembershipFails() {
            val payment = buildPayment()
            given(paymentRepository.findWithUserByOrderId(ORDER_ID)).willReturn(payment)
            expectTossConfirm(tossResponse("DONE", 9_900L))
            willThrow(BusinessException(ErrorCode.ALREADY_JOINED_MEMBERSHIP))
                .given(subscriptionService)
                .joinMembership(BUYER_ID, CREATOR_ID)

            assertThatThrownBy { paymentService.confirmPayment(BUYER_ID, PAYMENT_KEY, ORDER_ID) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_JOINED_MEMBERSHIP)

            // 승인은 이미 끝났으므로 결제 기록이 되돌려지면 안 된다
            assertThat(payment.status).isEqualTo(PaymentStatus.DONE)
        }
    }

    @Nested
    @DisplayName("결제 내역 조회")
    inner class PaymentHistory {
        @Test
        @DisplayName("본인 결제 내역을 최신순으로 반환한다")
        fun returnsMyPayments() {
            given(paymentRepository.findByUserIdOrderByCreatedAtDesc(BUYER_ID))
                .willReturn(listOf(buildPayment()))

            val result = paymentService.getMyPayments(BUYER_ID)

            assertThat(result).hasSize(1)
            assertThat(result[0].orderId).isEqualTo(ORDER_ID)
            assertThat(result[0].amount).isEqualTo(9_900L)
            assertThat(result[0].status).isEqualTo(PaymentStatus.READY.name)
        }
    }

    /** 테스트에서 특정 빌더 하나만 돌려주기 위한 최소 ObjectProvider 구현체. */
    private class SingletonObjectProvider(
        private val builder: RestClient.Builder,
    ) : ObjectProvider<RestClient.Builder> {
        override fun getObject(): RestClient.Builder = builder

        override fun getObject(vararg args: Any?): RestClient.Builder = builder

        override fun getIfAvailable(): RestClient.Builder = builder

        override fun getIfUnique(): RestClient.Builder = builder
    }

    companion object {
        private const val BUYER_ID = 1L
        private const val CREATOR_ID = 2L
        private const val OTHER_USER_ID = 99L
        private const val ORDER_ID = "order_test123456789"
        private const val PAYMENT_KEY = "test_payment_key"
        private const val TEST_SECRET_KEY = "test_secret_key"
        private const val TOSS_CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm"
    }
}
