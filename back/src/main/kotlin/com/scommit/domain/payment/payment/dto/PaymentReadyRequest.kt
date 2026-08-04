package com.scommit.domain.payment.payment.dto

data class PaymentReadyRequest(
    val amount: Long,
    val orderName: String,
    val targetCreatorId: Long
)
