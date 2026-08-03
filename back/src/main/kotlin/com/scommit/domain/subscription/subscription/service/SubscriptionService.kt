package com.scommit.domain.subscription.subscription.service

import com.scommit.domain.notification.notification.dto.NotificationResponse
import com.scommit.domain.notification.notification.dto.NotificationType
import com.scommit.domain.notification.notification.repository.SseEmitterRepository
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
    private val sseEmitterRepository: SseEmitterRepository
) {

    @Transactional
    fun follow(userId: Long, creatorId: Long) {
        if (userId == creatorId) {
            throw BusinessException(ErrorCode.SELF_SUBSCRIPTION_NOT_ALLOWED)
        }

        val user = userRepository.findByIdOrNull(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val creator = userRepository.findByIdOrNull(creatorId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val existingSubscription = subscriptionRepository.findByUserIdAndCreatorId(userId, creatorId)

        if (existingSubscription != null) {
            if (existingSubscription.deletedAt == null) {
                throw BusinessException(ErrorCode.ALREADY_SUBSCRIBED)
            }
            existingSubscription.restoreSubscription()
        } else {
            val newSubscription = Subscription(
                user = user,
                creator = creator,
                tier = SubscriptionTier.FOLLOW,
                startedAt = LocalDate.now(),
                expiredAt = null
            )
            subscriptionRepository.save(newSubscription)
        }

        sseEmitterRepository.sendToUser(
            creatorId, NotificationResponse(
                type = NotificationType.FOLLOW,
                message = "${user.nickname}님이 팔로우했습니다.",
                targetId = userId
            )
        )
    }

    @Transactional
    fun unfollow(userId: Long, creatorId: Long) {
        val subscription = subscriptionRepository.findByUserIdAndCreatorId(userId, creatorId)
            ?: throw BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND)

        if (subscription.deletedAt != null) {
            throw BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND)
        }

        if (subscription.tier == SubscriptionTier.MEMBERSHIP) {
            throw BusinessException(ErrorCode.MEMBERSHIP_ACTIVE_CANCEL_REQUIRED)
        }

        subscription.softDelete()
    }

    @Transactional
    fun joinMembership(userId: Long, creatorId: Long) {
        if (userId == creatorId) {
            throw BusinessException(ErrorCode.SELF_SUBSCRIPTION_NOT_ALLOWED)
        }

        val subscription = subscriptionRepository.findByUserIdAndCreatorId(userId, creatorId)

        if (subscription != null) {
            if (subscription.deletedAt != null) {
                subscription.restoreSubscription()
                subscription.upgradeToMembership()
            } else if (subscription.tier == SubscriptionTier.MEMBERSHIP) {
                throw BusinessException(ErrorCode.ALREADY_JOINED_MEMBERSHIP)
            } else {
                subscription.upgradeToMembership()
            }
            
            sseEmitterRepository.sendToUser(
                creatorId, NotificationResponse(
                    type = NotificationType.MEMBERSHIP,
                    message = "${subscription.user.nickname}님이 멤버십에 가입했습니다.",
                    targetId = userId
                )
            )
        } else {
            val user = userRepository.findByIdOrNull(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
            val creator = userRepository.findByIdOrNull(creatorId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

            val newSubscription = Subscription(
                user = user,
                creator = creator,
                tier = SubscriptionTier.MEMBERSHIP,
                startedAt = LocalDate.now(),
                expiredAt = null
            )
            subscriptionRepository.save(newSubscription)
            
            sseEmitterRepository.sendToUser(
                creatorId, NotificationResponse(
                    type = NotificationType.MEMBERSHIP,
                    message = "${user.nickname}님이 멤버십에 가입했습니다.",
                    targetId = userId
                )
            )
        }
    }

    @Transactional
    fun cancelMembership(userId: Long, creatorId: Long) {
        val subscription = subscriptionRepository.findByUserIdAndCreatorId(userId, creatorId)
            ?: throw BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND)

        if (subscription.deletedAt != null) {
            throw BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND)
        }

        if (subscription.tier != SubscriptionTier.MEMBERSHIP) {
            throw BusinessException(ErrorCode.NOT_MEMBERSHIP_SUBSCRIBER)
        }

        subscription.downgradeToFollow()
    }

    fun getMySubscriptions(userId: Long, pageable: Pageable): Page<SubscriptionInfo> {
        val subscriptionsPage = subscriptionRepository.findMySubscriptions(userId, pageable)
        
        val creatorIds = subscriptionsPage.content.map { it.creator.id!! }.distinct()

        val followerCounts = if (creatorIds.isEmpty()) {
            emptyMap()
        } else {
            subscriptionRepository.countFollowersGroupedByCreatorIds(creatorIds)
                .associate { (it[0] as Long) to (it[1] as Long) }
        }

        return subscriptionsPage.map { sub ->
            val count = followerCounts[sub.creator.id] ?: 0L
            SubscriptionInfo.from(sub, count)
        }
    }

    fun getMySubscriptionCount(userId: Long): Long {
        return subscriptionRepository.countByUserIdAndDeletedAtIsNull(userId)
    }

    fun getSubscriptionStatus(userId: Long, creatorId: Long): SubscriptionStatus {
        if (userId == creatorId) return SubscriptionStatus.NONE
        
        val subscription = subscriptionRepository.findByUserIdAndCreatorId(userId, creatorId)
        
        return if (subscription == null || subscription.deletedAt != null) {
            SubscriptionStatus.NONE
        } else {
            SubscriptionStatus.valueOf(subscription.tier.name)
        }
    }

    fun getFollowerCount(creatorId: Long): Long {
        return subscriptionRepository.countByCreatorIdAndDeletedAtIsNull(creatorId)
    }
}
