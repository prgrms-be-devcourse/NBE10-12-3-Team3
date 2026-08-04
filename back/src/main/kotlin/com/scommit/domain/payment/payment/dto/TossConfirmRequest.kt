package com.scommit.domain.payment.payment.dto

data class TossConfirmRequest(
    val paymentKey: String,
    val orderId: String,
    val amount: Long
)
