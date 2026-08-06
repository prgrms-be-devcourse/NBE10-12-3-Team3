package com.scommit.domain.subscription.subscription.service

import com.scommit.domain.notification.notification.service.NotificationService
import com.scommit.domain.subscription.subscription.dto.SubscriptionInfo
import com.scommit.domain.subscription.subscription.dto.SubscriptionStatus
import com.scommit.domain.subscription.subscription.entity.Subscription
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService,
) {
    private fun fail(errorCode: ErrorCode): Nothing = throw BusinessException(errorCode)

    @Transactional
    fun follow(
        userId: Long,
        creatorId: Long,
    ) {
        if (userId == creatorId) fail(ErrorCode.SELF_SUBSCRIPTION_NOT_ALLOWED)

        val user = userRepository.findByIdOrNull(userId) ?: fail(ErrorCode.USER_NOT_FOUND)
        val creator = userRepository.findByIdOrNull(creatorId) ?: fail(ErrorCode.USER_NOT_FOUND)

        val existingSubscription = subscriptionRepository.findByUserIdAndCreatorId(userId, creatorId)

        if (existingSubscription != null) {
            if (existingSubscription.deletedAt == null) fail(ErrorCode.ALREADY_SUBSCRIBED)
            existingSubscription.restoreSubscription()
        } else {
            val newSubscription =
                Subscription(
                    user = user,
                    creator = creator,
                    tier = SubscriptionTier.FOLLOW,
                    startedAt = LocalDate.now(),
                    expiredAt = null,
                )
            subscriptionRepository.save(newSubscription)
        }

        notificationService.notifyFollow(creatorId, user.nickname, userId)
    }

    @Transactional
    fun unfollow(
        userId: Long,
        creatorId: Long,
    ) {
        val subscription =
            subscriptionRepository.findByUserIdAndCreatorId(userId, creatorId)
                ?: fail(ErrorCode.SUBSCRIPTION_NOT_FOUND)

        if (subscription.deletedAt != null) fail(ErrorCode.SUBSCRIPTION_NOT_FOUND)
        if (subscription.tier == SubscriptionTier.MEMBERSHIP) fail(ErrorCode.MEMBERSHIP_ACTIVE_CANCEL_REQUIRED)

        subscription.softDelete()
    }

    @Transactional
    fun joinMembership(
        userId: Long,
        creatorId: Long,
    ) {
        if (userId == creatorId) fail(ErrorCode.SELF_SUBSCRIPTION_NOT_ALLOWED)

        val subscription = subscriptionRepository.findByUserIdAndCreatorId(userId, creatorId)

        if (subscription != null) {
            if (subscription.deletedAt != null) {
                subscription.restoreSubscription()
                subscription.upgradeToMembership()
            } else if (subscription.tier == SubscriptionTier.MEMBERSHIP) {
                fail(ErrorCode.ALREADY_JOINED_MEMBERSHIP)
            } else {
                subscription.upgradeToMembership()
            }

            notificationService.notifyMembership(creatorId, subscription.user.nickname, userId)
        } else {
            val user = userRepository.findByIdOrNull(userId) ?: fail(ErrorCode.USER_NOT_FOUND)
            val creator = userRepository.findByIdOrNull(creatorId) ?: fail(ErrorCode.USER_NOT_FOUND)

            val newSubscription =
                Subscription(
                    user = user,
                    creator = creator,
                    tier = SubscriptionTier.MEMBERSHIP,
                    startedAt = LocalDate.now(),
                    expiredAt = null,
                )
            subscriptionRepository.save(newSubscription)

            notificationService.notifyMembership(creatorId, user.nickname, userId)
        }
    }

    @Transactional
    fun cancelMembership(
        userId: Long,
        creatorId: Long,
    ) {
        val subscription =
            subscriptionRepository.findByUserIdAndCreatorId(userId, creatorId)
                ?: fail(ErrorCode.SUBSCRIPTION_NOT_FOUND)

        if (subscription.deletedAt != null) fail(ErrorCode.SUBSCRIPTION_NOT_FOUND)
        if (subscription.tier != SubscriptionTier.MEMBERSHIP) fail(ErrorCode.NOT_MEMBERSHIP_SUBSCRIBER)

        subscription.downgradeToFollow()
    }

    fun getMySubscriptions(
        userId: Long,
        pageable: Pageable,
    ): Page<SubscriptionInfo> {
        val subscriptionsPage = subscriptionRepository.findMySubscriptions(userId, pageable)

        val creatorIds = subscriptionsPage.content.mapNotNull { it.creator.id }.distinct()

        val followerCounts =
            if (creatorIds.isEmpty()) {
                emptyMap()
            } else {
                subscriptionRepository
                    .countFollowersGroupedByCreatorIds(creatorIds)
                    .associate { (it[0] as Long) to (it[1] as Long) }
            }

        return subscriptionsPage.map { sub ->
            val count = followerCounts[sub.creator.id] ?: 0L
            SubscriptionInfo.from(sub, count)
        }
    }

    fun getMySubscriptionCount(userId: Long): Long = subscriptionRepository.countByUserIdAndDeletedAtIsNull(userId)

    fun getSubscriptionStatus(
        userId: Long,
        creatorId: Long,
    ): SubscriptionStatus {
        if (userId == creatorId) return SubscriptionStatus.NONE

        val subscription = subscriptionRepository.findByUserIdAndCreatorId(userId, creatorId)

        return if (subscription == null || subscription.deletedAt != null) {
            SubscriptionStatus.NONE
        } else {
            SubscriptionStatus.valueOf(subscription.tier.name)
        }
    }

    fun getFollowerCount(creatorId: Long): Long = subscriptionRepository.countByCreatorIdAndDeletedAtIsNull(creatorId)
}
