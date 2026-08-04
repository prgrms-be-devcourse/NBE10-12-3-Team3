package com.scommit.domain.payment.payment.service

import com.scommit.domain.payment.payment.dto.TossConfirmRequest
import com.scommit.domain.payment.payment.entity.Payment
import com.scommit.domain.payment.payment.entity.PaymentStatus
import com.scommit.domain.payment.payment.repository.PaymentRepository
import com.scommit.domain.subscription.subscription.service.SubscriptionService
import com.scommit.domain.user.user.entity.User
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PaymentServiceTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var subscriptionService: SubscriptionService

    private lateinit var paymentService: PaymentService

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        paymentService = PaymentService(paymentRepository, subscriptionService, "test_secret_key")
    }

    @Test
    @DisplayName("결제 금액 위변조 시 예외가 발생한다")
    fun failWhenAmountIsManipulated() {
        // given
        val user = User(1L, "test@test.com", "tester")
        val payment = Payment(
            user = user,
            orderId = "ORDER_123",
            orderName = "멤버십 구독",
            amount = 10000L // 정상 DB 가격
        )
        
        val request = TossConfirmRequest(
            paymentKey = "test_key",
            orderId = "ORDER_123",
            amount = 100L // 해커가 100원으로 조작한 가격
        )

        `when`(paymentRepository.findByOrderId("ORDER_123")).thenReturn(payment)

        // when & then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            paymentService.confirmPayment(request)
        }
        
        assert(exception.message!!.contains("결제 요청 금액이 변조되었습니다"))
        assert(payment.status == PaymentStatus.ABORTED)
    }
}
