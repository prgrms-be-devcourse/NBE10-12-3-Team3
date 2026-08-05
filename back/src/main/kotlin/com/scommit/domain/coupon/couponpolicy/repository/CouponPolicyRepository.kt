package com.scommit.domain.coupon.couponpolicy.repository

import com.scommit.domain.coupon.couponpolicy.entity.CouponPolicy
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface CouponPolicyRepository : JpaRepository<CouponPolicy, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): CouponPolicy?

    // 선착순 발급용: 동시 요청이 같은 row를 두고 경쟁하므로 비관적 락으로 순차 처리한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponPolicy c WHERE c.id = :id AND c.deletedAt IS NULL")
    fun findByIdAndDeletedAtIsNullForUpdate(
        @Param("id") id: Long,
    ): CouponPolicy?

    @Query(
        "SELECT c FROM CouponPolicy c " +
            "WHERE c.deletedAt IS NULL AND c.startAt <= :now AND c.endAt >= :now",
    )
    fun findAllActive(
        @Param("now") now: LocalDateTime,
    ): List<CouponPolicy>
}
