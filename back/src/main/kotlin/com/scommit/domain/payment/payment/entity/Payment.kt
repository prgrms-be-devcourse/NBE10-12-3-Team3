package com.scommit.domain.payment.payment.entity

import com.scommit.domain.user.user.entity.User
import com.scommit.global.base.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "payments",
    indexes = [
        Index(name = "idx_payment_order_id", columnList = "order_id", unique = true)
    ]
)
class Payment(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "order_id", nullable = false, unique = true)
    val orderId: String,

    @Column(name = "order_name", nullable = false)
    val orderName: String,

    @Column(name = "target_creator_id", nullable = false)
    val targetCreatorId: Long,

    @Column(nullable = false)
    val amount: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.READY,

    @Column(name = "payment_key")
    var paymentKey: String? = null
) : BaseEntity() {

    fun confirm(paymentKey: String) {
        this.status = PaymentStatus.DONE
        this.paymentKey = paymentKey
    }

    fun fail() {
        this.status = PaymentStatus.ABORTED
    }
    
    fun cancel() {
        this.status = PaymentStatus.CANCELED
    }
}
