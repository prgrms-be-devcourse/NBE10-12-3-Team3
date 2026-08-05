package com.scommit.domain.payment.payment.service

import com.scommit.domain.payment.payment.dto.PaymentReadyResponse
import com.scommit.domain.payment.payment.dto.PaymentResponse
import com.scommit.domain.payment.payment.dto.TossConfirmResponse
import com.scommit.domain.payment.payment.entity.Payment
import com.scommit.domain.payment.payment.repository.PaymentRepository
import com.scommit.domain.subscription.subscription.dto.SubscriptionStatus
import com.scommit.domain.subscription.subscription.service.SubscriptionService
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.Base64
import java.util.UUID

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val subscriptionService: SubscriptionService,
    private val userRepository: UserRepository,
    // 테스트에서 MockRestServiceServer로 토스 응답을 대체할 수 있도록 빌더를 주입받는다.
    // RestClient.Builder 빈이 없는 컨텍스트(슬라이스 테스트 등)에서도 뜨도록 기본 빌더로 대체한다.
    restClientBuilderProvider: ObjectProvider<RestClient.Builder>,
    @Value("\${toss.payment.secret-key}")
    private val secretKey: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = restClientBuilderProvider.getIfAvailable { RestClient.builder() }.build()

    /**
     * 결제 준비 단계.
     *
     * 결제 금액은 서버가 결정한다. 또한 승인 이후에 실패한다면 이미 청구가 끝난 뒤이므로,
     * 멤버십 가입이 불가능한 조건(본인 구독·중복 가입)은 반드시 이 시점에 먼저 걸러낸다.
     */
    @Suppress("ThrowsCount")
    @Transactional
    fun readyPayment(
        userId: Long,
        creatorId: Long,
    ): PaymentReadyResponse {
        if (userId == creatorId) {
            throw BusinessException(ErrorCode.SELF_SUBSCRIPTION_NOT_ALLOWED)
        }
        if (subscriptionService.getSubscriptionStatus(userId, creatorId) == SubscriptionStatus.MEMBERSHIP) {
            throw BusinessException(ErrorCode.ALREADY_JOINED_MEMBERSHIP)
        }

        val user =
            userRepository.findByIdOrNull(userId)
                ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val creator =
            userRepository.findByIdOrNull(creatorId)
                ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val orderName = "${creator.nickname} 멤버십 구독"
        val payment =
            Payment(
                user = user,
                orderId = generateOrderId(),
                orderName = orderName,
                targetCreatorId = creatorId,
                amount = MEMBERSHIP_PRICE,
            )
        paymentRepository.save(payment)

        return PaymentReadyResponse(
            orderId = payment.orderId,
            orderName = orderName,
            amount = MEMBERSHIP_PRICE,
        )
    }

    /**
     * 결제 승인 단계.
     *
     * 외부 API 호출을 하나의 트랜잭션으로 감싸면 (1) DB 커넥션을 HTTP 응답 시간만큼 붙잡고,
     * (2) 예외가 나가는 순간 실패 상태 기록까지 함께 롤백되어 사라진다.
     * 따라서 이 메서드는 트랜잭션 밖에 두고 상태 변경마다 명시적으로 저장한다.
     */
    fun confirmPayment(
        userId: Long,
        paymentKey: String,
        orderId: String,
    ) {
        val payment = validateConfirmable(userId, orderId)

        // 토스에는 반드시 서버가 보관 중인 금액을 보낸다. 클라이언트가 보낸 금액은 신뢰하지 않는다.
        val response = requestTossConfirm(payment, paymentKey)

        // 가상계좌(WAITING_FOR_DEPOSIT)처럼 200을 받아도 입금이 끝나지 않은 상태가 있으므로
        // status와 실제 승인 금액을 모두 확인한다.
        if (!response.isSettled() || response.totalAmount != payment.amount) {
            log.warn(
                "결제 승인 응답 검증 실패 orderId={} status={} totalAmount={} expected={}",
                orderId,
                response.status,
                response.totalAmount,
                payment.amount,
            )
            markFailed(payment)
            throw BusinessException(ErrorCode.PAYMENT_NOT_APPROVED)
        }

        // 승인이 확정된 시점부터는 실제로 청구가 끝난 상태다.
        // 이후 단계가 실패하더라도 결제 기록은 반드시 남겨야 하므로 먼저 저장한다.
        payment.confirm(paymentKey)
        paymentRepository.save(payment)

        grantMembership(payment)
    }

    @Transactional(readOnly = true)
    fun getMyPayments(userId: Long): List<PaymentResponse> =
        paymentRepository
            .findByUserIdOrderByCreatedAtDesc(userId)
            .map { PaymentResponse.from(it) }

    @Suppress("ThrowsCount")
    private fun validateConfirmable(
        userId: Long,
        orderId: String,
    ): Payment {
        val payment =
            paymentRepository.findWithUserByOrderId(orderId)
                ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)

        if (!payment.isOwnedBy(userId)) {
            throw BusinessException(ErrorCode.PAYMENT_OWNER_MISMATCH)
        }
        if (!payment.isConfirmable()) {
            throw BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED)
        }
        return payment
    }

    private fun requestTossConfirm(
        payment: Payment,
        paymentKey: String,
    ): TossConfirmResponse {
        val encodedAuthKey = Base64.getEncoder().encodeToString("$secretKey:".toByteArray())
        val body =
            mapOf(
                "paymentKey" to paymentKey,
                "orderId" to payment.orderId,
                "amount" to payment.amount,
            )

        return try {
            restClient
                .post()
                .uri(TOSS_CONFIRM_URL)
                .header(HttpHeaders.AUTHORIZATION, "Basic $encodedAuthKey")
                // 네트워크 재시도로 인한 중복 승인을 토스 측에서 차단하도록 멱등키를 함께 보낸다.
                .header(IDEMPOTENCY_KEY_HEADER, payment.orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(TossConfirmResponse::class.java)
                ?: throw BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR)
        } catch (e: RestClientException) {
            log.warn("토스 결제 승인 API 호출 실패 orderId={}", payment.orderId, e)
            markFailed(payment)
            throw BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR)
        }
    }

    /**
     * 승인 완료 후 멤버십을 부여한다.
     *
     * ready 단계에서 선검증하므로 정상 흐름에서는 실패하지 않는다.
     * 다만 동시 요청 등으로 실패하더라도 이미 청구된 결제 기록을 되돌려서는 안 되므로
     * 결제 상태는 DONE으로 유지한 채 예외만 전파한다.
     */
    private fun grantMembership(payment: Payment) {
        try {
            subscriptionService.joinMembership(payment.user.id!!, payment.targetCreatorId)
        } catch (e: BusinessException) {
            log.error(
                "결제는 승인되었으나 멤버십 승급에 실패했습니다. 수동 확인 필요 orderId={} userId={} creatorId={}",
                payment.orderId,
                payment.user.id,
                payment.targetCreatorId,
                e,
            )
            throw e
        }
    }

    /**
     * 실패 상태를 별도 트랜잭션(repository.save)으로 커밋한다.
     * 호출자가 곧바로 예외를 던지더라도 기록이 롤백되지 않는다.
     */
    private fun markFailed(payment: Payment) {
        payment.fail()
        paymentRepository.save(payment)
    }

    private fun generateOrderId(): String =
        "order_" +
            UUID
                .randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, ORDER_ID_SUFFIX_LENGTH)

    companion object {
        /** 멤버십 구독 가격(원). 클라이언트 입력이 아닌 서버 상수로 고정한다. */
        const val MEMBERSHIP_PRICE = 9_900L

        private const val TOSS_CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm"
        private const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        private const val ORDER_ID_SUFFIX_LENGTH = 16
    }
}
