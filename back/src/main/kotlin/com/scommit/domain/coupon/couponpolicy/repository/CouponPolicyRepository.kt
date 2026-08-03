package com.scommit.domain.coupon.couponpolicy.repository

import com.scommit.domain.coupon.couponpolicy.entity.CouponPolicy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface CouponPolicyRepository : JpaRepository<CouponPolicy, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): CouponPolicy?

    @Query(
        "SELECT c FROM CouponPolicy c " +
                "WHERE c.deletedAt IS NULL AND c.startAt <= :now AND c.endAt >= :now",
    )
    fun findAllActive(
        @Param("now") now: LocalDateTime,
    ): List<CouponPolicy>
}
