package com.scommit.domain.payment.payment.entity

import com.scommit.domain.user.user.entity.User
import com.scommit.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "payments",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_payments_order_id",
            columnNames = ["order_id"],
        ),
    ],
)
class Payment(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    // 유니크 제약은 테이블 레벨(uk_payments_order_id)에서 이름과 함께 선언한다.
    @Column(name = "order_id", nullable = false)
    val orderId: String,
    @Column(name = "order_name", nullable = false)
    val orderName: String,
    @Column(name = "target_creator_id", nullable = false)
    val targetCreatorId: Long,
    /** 서버가 결정한 결제 금액. 클라이언트 입력을 그대로 신뢰해서는 안 된다. */
    @Column(nullable = false)
    val amount: Long,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.READY
        protected set

    @Column(name = "payment_key")
    var paymentKey: String? = null
        protected set

    /** 아직 승인 처리가 가능한 상태인지 여부 (멱등성 방어) */
    fun isConfirmable(): Boolean = status == PaymentStatus.READY || status == PaymentStatus.IN_PROGRESS

    fun isOwnedBy(userId: Long): Boolean = user.id == userId

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
