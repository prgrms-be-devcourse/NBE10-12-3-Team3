package com.scommit.domain.payment.payment.service

import com.scommit.domain.payment.payment.dto.PaymentResponse
import com.scommit.domain.payment.payment.dto.TossConfirmRequest
import com.scommit.domain.payment.payment.dto.PaymentReadyRequest
import com.scommit.domain.payment.payment.dto.PaymentReadyResponse
import com.scommit.domain.payment.payment.entity.Payment
import com.scommit.domain.payment.payment.entity.PaymentStatus
import com.scommit.domain.payment.payment.repository.PaymentRepository
import com.scommit.domain.subscription.subscription.service.SubscriptionService
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import java.util.Base64
import java.util.UUID

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val subscriptionService: SubscriptionService,
    private val userRepository: UserRepository,
    @Value("\${toss.payment.secret-key}")
    private val secretKey: String
) {
    private val restClient = RestClient.create()

    @Transactional
    fun readyPayment(userId: Long, request: PaymentReadyRequest): PaymentReadyResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }

        val orderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)

        val payment = Payment(
            user = user,
            orderId = orderId,
            orderName = request.orderName,
            targetCreatorId = request.targetCreatorId,
            amount = request.amount
        )
        paymentRepository.save(payment)

        return PaymentReadyResponse(orderId)
    }

    @Transactional
    fun confirmPayment(request: TossConfirmRequest) {
        // 1. 우리 DB에서 결제 고유 번호(orderId)로 결제 정보 조회
        val payment = paymentRepository.findByOrderId(request.orderId)
            ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)

        // 2. 멱등성 및 상태 검증 (이미 처리된 결제인지 확인)
        if (payment.status != PaymentStatus.READY && payment.status != PaymentStatus.IN_PROGRESS) {
            throw IllegalArgumentException("이미 처리되었거나 취소된 결제입니다.")
        }

        // 3. 금액 위변조 검증 (DB 가격 vs 프론트엔드 요청 가격)
        if (payment.amount != request.amount) {
            payment.fail()
            throw IllegalArgumentException("결제 요청 금액이 변조되었습니다. (DB: ${payment.amount}, 요청: ${request.amount})")
        }

        // 4. 토스 페이먼츠 승인(Confirm) API 호출
        val encodedAuthKey = Base64.getEncoder().encodeToString("$secretKey:".toByteArray())
        
        try {
            val response = restClient.post()
                .uri("https://api.tosspayments.com/v1/payments/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Basic $encodedAuthKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map::class.java)

            val status = response?.get("status") as? String
            if (status != "DONE") {
                payment.fail()
                throw IllegalStateException("결제가 승인되지 않았습니다. 상태: $status")
            }
                
            // 5. 결제 성공 처리
            payment.confirm(request.paymentKey)
            
            // 6. 비즈니스 로직 연동: 해당 유저의 멤버십 업그레이드
            subscriptionService.joinMembership(payment.user.id!!, payment.targetCreatorId)
            
        } catch (e: Exception) {
            // 토스 측 승인 실패 시
            payment.fail()
            throw IllegalStateException("결제 승인 중 오류가 발생했습니다: ${e.message}")
        }
    }

    @Transactional(readOnly = true)
    fun getMyPayments(userId: Long): List<PaymentResponse> {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .map { PaymentResponse.from(it) }
    }
}
