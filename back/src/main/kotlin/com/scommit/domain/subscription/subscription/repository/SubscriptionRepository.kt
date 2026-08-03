package com.scommit.domain.subscription.subscription.repository

import com.scommit.domain.subscription.subscription.entity.Subscription
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SubscriptionRepository : JpaRepository<Subscription, Long> {

    // 2. 팔로우: 특정 유저가 특정 창작자를 구독하는지 조회 (단건)
    fun findByUserIdAndCreatorId(userId: Long, creatorId: Long): Subscription?

    // 3. 내 구독/멤버십 조회 (N+1 방지용 creator 패치 조인)
    @Query(
        value = "SELECT s FROM Subscription s JOIN FETCH s.creator WHERE s.user.id = :userId AND s.deletedAt IS NULL",
        countQuery = "SELECT count(s) FROM Subscription s WHERE s.user.id = :userId AND s.deletedAt IS NULL"
    )
    fun findMySubscriptions(@Param("userId") userId: Long, pageable: Pageable): Page<Subscription>

    // 4. 새 포스트 알림: 창작자를 구독하는 유저 조회 (전건)
    fun findByCreatorIdAndDeletedAtIsNull(creatorId: Long): List<Subscription>

    // 5. 새 멤버십 포스트 알림: 창작자의 멤버십 유저만 조회 (전건)
    fun findByCreatorIdAndTierAndDeletedAtIsNull(creatorId: Long, tier: SubscriptionTier): List<Subscription>

    // 6. N+1 방지: 여러 창작자의 팔로워 수를 한 번에 조회
    @Query("SELECT s.creator.id, COUNT(s) FROM Subscription s WHERE s.creator.id IN :creatorIds AND s.deletedAt IS NULL GROUP BY s.creator.id")
    fun countFollowersGroupedByCreatorIds(@Param("creatorIds") creatorIds: List<Long>): List<Array<Any>>

    // 7. 내 구독 총 수 조회 API용
    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.user.id = :userId AND s.deletedAt IS NULL")
    fun countByUserIdAndDeletedAtIsNull(@Param("userId") userId: Long): Long

    // 8. 총 팔로워 수 단건 조회용
    fun countByCreatorIdAndDeletedAtIsNull(creatorId: Long): Long
}
