package com.scommit.domain.payment.payment.repository

import com.scommit.domain.payment.payment.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByOrderId(orderId: String): Payment?
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Payment>
}
