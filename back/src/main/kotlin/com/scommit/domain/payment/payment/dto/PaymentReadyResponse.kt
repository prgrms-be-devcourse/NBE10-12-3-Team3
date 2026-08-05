package com.scommit.domain.payment.payment.dto

/**
 * 결제 준비 응답.
 * 클라이언트는 여기서 내려준 금액·주문명으로 결제 위젯을 확정해야 한다.
 */
data class PaymentReadyResponse(
    val orderId: String,
    val orderName: String,
    val amount: Long,
)
