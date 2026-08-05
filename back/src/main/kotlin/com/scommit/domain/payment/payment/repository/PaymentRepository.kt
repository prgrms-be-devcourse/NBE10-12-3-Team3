package com.scommit.domain.payment.payment.repository

import com.scommit.domain.payment.payment.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByOrderId(orderId: String): Payment?

    /**
     * 승인 처리는 트랜잭션 밖에서 이루어지므로 소유자 검증에 필요한 User를 함께 로딩한다.
     * (LAZY 프록시를 영속성 컨텍스트 밖에서 초기화하는 것을 방지)
     */
    @Query("select p from Payment p join fetch p.user where p.orderId = :orderId")
    fun findWithUserByOrderId(
        @Param("orderId") orderId: String,
    ): Payment?

    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Payment>
}
