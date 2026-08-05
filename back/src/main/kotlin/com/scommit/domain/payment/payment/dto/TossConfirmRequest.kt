package com.scommit.domain.payment.payment.dto

import jakarta.validation.constraints.NotBlank

/**
 * 토스 리다이렉트로 돌아온 결제 승인 요청.
 *
 * 금액은 클라이언트가 전달하더라도 신뢰하지 않는다.
 * 승인 시 토스로 전송하는 금액은 서버가 보관 중인 값을 사용한다.
 */
data class TossConfirmRequest(
    @field:NotBlank(message = "paymentKey는 필수입니다.")
    val paymentKey: String,
    @field:NotBlank(message = "orderId는 필수입니다.")
    val orderId: String,
)
