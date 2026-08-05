package com.scommit.domain.payment.payment.dto

import jakarta.validation.constraints.Positive

/**
 * 결제 준비 요청.
 * 결제 금액과 주문명은 서버가 결정하므로 클라이언트는 대상 창작자만 지정한다.
 */
data class PaymentReadyRequest(
    @field:Positive(message = "창작자 ID가 올바르지 않습니다.")
    val targetCreatorId: Long,
)
