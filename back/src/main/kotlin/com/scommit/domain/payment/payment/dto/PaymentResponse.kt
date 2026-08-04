package com.scommit.domain.payment.payment.dto

import com.scommit.domain.payment.payment.entity.Payment
import java.time.LocalDateTime

data class PaymentResponse(
    val id: Long,
    val orderId: String,
    val orderName: String,
    val amount: Long,
    val status: String,
    val createDate: LocalDateTime
) {
    companion object {
        fun from(payment: Payment): PaymentResponse {
            return PaymentResponse(
                id = payment.id,
                orderId = payment.orderId,
                orderName = payment.orderName,
                amount = payment.amount,
                status = payment.status.name,
                createDate = payment.createdAt
            )
        }
    }
}
