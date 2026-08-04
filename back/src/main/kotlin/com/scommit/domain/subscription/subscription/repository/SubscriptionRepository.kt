package com.scommit.domain.subscription.subscription.repository

import com.scommit.domain.subscription.subscription.entity.Subscription
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

@Suppress("TooManyFunctions")
interface SubscriptionRepository : JpaRepository<Subscription, Long> {
    // 2. 팔로우: 특정 유저가 특정 창작자를 구독하는지 조회 (단건)
    fun findByUserIdAndCreatorId(
        userId: Long,
        creatorId: Long,
    ): Subscription?

    // 3. 내 구독/멤버십 조회 (N+1 방지용 creator 패치 조인)
    @Query(
        value = "SELECT s FROM Subscription s JOIN FETCH s.creator WHERE s.user.id = :userId AND s.deletedAt IS NULL",
        countQuery = "SELECT count(s) FROM Subscription s WHERE s.user.id = :userId AND s.deletedAt IS NULL",
    )
    fun findMySubscriptions(
        @Param("userId") userId: Long,
        pageable: Pageable,
    ): Page<Subscription>

    // 4. 새 포스트 알림: 창작자를 구독하는 유저 조회 (전건)
    fun findByCreatorIdAndDeletedAtIsNull(creatorId: Long): List<Subscription>

    // 5. 새 멤버십 포스트 알림: 창작자의 멤버십 유저만 조회 (전건)
    fun findByCreatorIdAndTierAndDeletedAtIsNull(
        creatorId: Long,
        tier: SubscriptionTier,
    ): List<Subscription>

    // 6. N+1 방지: 여러 창작자의 팔로워 수를 한 번에 조회
    @Query(
        "SELECT s.creator.id, COUNT(s) FROM Subscription s " +
            "WHERE s.creator.id IN :creatorIds AND s.deletedAt IS NULL " +
            "GROUP BY s.creator.id",
    )
    fun countFollowersGroupedByCreatorIds(
        @Param("creatorIds") creatorIds: List<Long>,
    ): List<Array<Any>>

    // 7. 내 구독 총 수 조회 API용
    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.user.id = :userId AND s.deletedAt IS NULL")
    fun countByUserIdAndDeletedAtIsNull(
        @Param("userId") userId: Long,
    ): Long

    // 8. 총 팔로워 수 단건 조회용
    fun countByCreatorIdAndDeletedAtIsNull(creatorId: Long): Long

    // 대시보드 통계용 쿼리
    @Query(
        "SELECT COUNT(s) FROM Subscription s WHERE s.creator.id = :creatorId " +
            "AND s.createdAt >= :createdAt AND s.deletedAt IS NULL",
    )
    fun countNewFollowersByUserIdAndPeriod(
        @Param("creatorId") creatorId: Long,
        @Param("createdAt") createdAt: LocalDateTime,
    ): Long

    @Query(
        "SELECT CASE WHEN COUNT(*) = 0 THEN 0 " +
            "ELSE CAST(COUNT(CASE WHEN s.tier = 'MEMBERSHIP' THEN 1 END) AS DOUBLE) / COUNT(*) * 100.0 END " +
            "FROM Subscription s WHERE s.creator.id = :creatorId AND s.deletedAt IS NULL",
    )
    fun getMembershipConversionRate(
        @Param("creatorId") creatorId: Long,
    ): Double

    @Query(
        "SELECT COUNT(s) FROM Subscription s WHERE s.creator.id = :creatorId " +
            "AND s.tier = 'MEMBERSHIP' AND s.deletedAt IS NULL",
    )
    fun countPaidMembershipsByCreatorId(
        @Param("creatorId") creatorId: Long,
    ): Long

    // 관리자 대시보드: 인기 창작자 TOP 5 (followerIncrease 기준)
    @Query(
        "SELECT s.creator, COUNT(s) as followerIncrease FROM Subscription s " +
            "WHERE s.createdAt >= :createdAt AND s.deletedAt IS NULL GROUP BY s.creator ORDER BY COUNT(s) DESC",
    )
    fun findTopCreatorsByFollowerIncreaseAndPeriod(
        @Param("createdAt") createdAt: LocalDateTime,
    ): List<Array<Any>>

    // 전체 통계
    @Query("SELECT COUNT(CASE WHEN s.tier = 'MEMBERSHIP' THEN 1 END) FROM Subscription s WHERE s.deletedAt IS NULL")
    fun countByTierMembership(): Long

    @Query("SELECT COUNT(CASE WHEN s.tier = 'FOLLOW' THEN 1 END) FROM Subscription s WHERE s.deletedAt IS NULL")
    fun countByTierFollow(): Long

    // 플랫폼 전체 구독 수 (플랫폼 평균 계산용)
    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.createdAt >= :createdAt AND s.deletedAt IS NULL")
    fun countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(
        @Param("createdAt") createdAt: LocalDateTime,
    ): Long
}
