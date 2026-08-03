package com.scommit.domain.subscription.subscription.service

import com.scommit.domain.notification.notification.dto.NotificationResponse
import com.scommit.domain.notification.notification.repository.SseEmitterRepository
import com.scommit.domain.subscription.subscription.dto.SubscriptionInfo
import com.scommit.domain.subscription.subscription.dto.SubscriptionStatus
import com.scommit.domain.subscription.subscription.entity.Subscription
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class SubscriptionServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var subscriptionRepository: SubscriptionRepository

    @Mock
    private lateinit var sseEmitterRepository: SseEmitterRepository

    @InjectMocks
    private lateinit var subscriptionService: SubscriptionService

    @Nested
    @DisplayName("API 1: 팔로우 테스트")
    inner class FollowTest {
        private val subscriberId = 1L
        private val creatorId = 2L

        private fun buildSubscriber(): User =
            User(subscriberId, "sub@test.com", "Subscriber", UserRole.USER)

        private fun buildCreator(): User =
            User(creatorId, "creator@test.com", "Creator", UserRole.USER)

        private fun buildFollowSubscription(subscriber: User, creator: User): Subscription =
            Subscription(user = subscriber, creator = creator, tier = SubscriptionTier.FOLLOW).apply {
                ReflectionTestUtils.setField(this, "id", 100L)
            }

        @Test
        @DisplayName("성공: 창작자를 처음 팔로우한다")
        fun followSuccess() {
            // given
            val subscriber = buildSubscriber()
            val creator = buildCreator()
            given(userRepository.findById(subscriberId)).willReturn(Optional.of(subscriber))
            given(userRepository.findById(creatorId)).willReturn(Optional.of(creator))
            given(subscriptionRepository.findByUserIdAndCreatorId(subscriberId, creatorId)).willReturn(null)
            given(subscriptionRepository.save(any(Subscription::class.java))).willAnswer { it.getArgument<Subscription>(0) }

            // when
            subscriptionService.follow(subscriberId, creatorId)

            // then
            verify(subscriptionRepository).save(any(Subscription::class.java))
        }

        @Test
        @DisplayName("실패: 자기 자신을 팔로우할 수 없다")
        fun followSelfFail() {
            // when & then
            assertThatThrownBy { subscriptionService.follow(subscriberId, subscriberId) }
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SELF_SUBSCRIPTION_NOT_ALLOWED)
                
            verify(subscriptionRepository, never()).save(any(Subscription::class.java))
        }

        @Test
        @DisplayName("실패: 이미 구독 중이면 중복 팔로우할 수 없다")
        fun followAlreadySubscribedFail() {
            // given
            val subscriber = buildSubscriber()
            val creator = buildCreator()
            val followSubscription = buildFollowSubscription(subscriber, creator)
            given(userRepository.findById(subscriberId)).willReturn(Optional.of(subscriber))
            given(userRepository.findById(creatorId)).willReturn(Optional.of(creator))
            given(subscriptionRepository.findByUserIdAndCreatorId(subscriberId, creatorId)).willReturn(followSubscription)

            // when & then
            assertThatThrownBy { subscriptionService.follow(subscriberId, creatorId) }
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_SUBSCRIBED)
        }
    }

    @Nested
    @DisplayName("API 2: 언팔로우 테스트")
    inner class UnfollowTest {
        private val subscriberId = 1L
        private val creatorId = 2L

        private fun buildFollowSubscription(): Subscription {
            val subscriber = User(subscriberId, "sub@test.com", "Subscriber", UserRole.USER)
            val creator = User(creatorId, "creator@test.com", "Creator", UserRole.USER)
            return Subscription(user = subscriber, creator = creator, tier = SubscriptionTier.FOLLOW).apply { ReflectionTestUtils.setField(this, "id", 100L) }
        }

        @Test
        @DisplayName("성공: 팔로우 상태에서 언팔로우 시 소프트 딜리트 처리된다")
        fun unfollowSuccess() {
            // given
            val followSubscription = buildFollowSubscription()
            given(subscriptionRepository.findByUserIdAndCreatorId(subscriberId, creatorId)).willReturn(followSubscription)

            // when
            subscriptionService.unfollow(subscriberId, creatorId)

            // then
            assertThat(followSubscription.deletedAt).isNotNull
        }

        @Test
        @DisplayName("실패: 멤버십 구독 중일 때는 직접 언팔로우할 수 없다")
        fun unfollowMembershipFail() {
            // given
            val followSubscription = buildFollowSubscription()
            followSubscription.upgradeToMembership()
            given(subscriptionRepository.findByUserIdAndCreatorId(subscriberId, creatorId)).willReturn(followSubscription)

            // when & then
            assertThatThrownBy { subscriptionService.unfollow(subscriberId, creatorId) }
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBERSHIP_ACTIVE_CANCEL_REQUIRED)
        }
    }

    @Nested
    @DisplayName("API 3, 4: 멤버십 가입/해지 테스트")
    inner class MembershipTest {
        private val subscriberId = 1L
        private val creatorId = 2L

        private fun buildFollowSubscription(): Subscription {
            val subscriber = User(subscriberId, "sub@test.com", "Subscriber", UserRole.USER)
            val creator = User(creatorId, "creator@test.com", "Creator", UserRole.USER)
            return Subscription(user = subscriber, creator = creator, tier = SubscriptionTier.FOLLOW).apply { ReflectionTestUtils.setField(this, "id", 100L) }
        }

        @Test
        @DisplayName("성공: 멤버십 가입 시 Tier가 MEMBERSHIP으로 오르고 만료일이 설정된다")
        fun joinMembershipSuccess() {
            // given
            val followSubscription = buildFollowSubscription()
            given(subscriptionRepository.findByUserIdAndCreatorId(subscriberId, creatorId)).willReturn(followSubscription)

            // when
            subscriptionService.joinMembership(subscriberId, creatorId)

            // then
            assertThat(followSubscription.tier).isEqualTo(SubscriptionTier.MEMBERSHIP)
            assertThat(followSubscription.expiredAt).isNotNull
        }

        @Test
        @DisplayName("성공: 팔로우하지 않은 상태에서 멤버십 가입 시 자동 팔로우 처리된다")
        fun joinMembershipAutoFollowSuccess() {
            // given
            val followSubscription = buildFollowSubscription()
            given(userRepository.findById(subscriberId)).willReturn(Optional.of(followSubscription.user))
            given(userRepository.findById(creatorId)).willReturn(Optional.of(followSubscription.creator))
            given(subscriptionRepository.findByUserIdAndCreatorId(subscriberId, creatorId)).willReturn(null)
            given(subscriptionRepository.save(any(Subscription::class.java))).willAnswer { it.getArgument<Subscription>(0) }

            // when
            subscriptionService.joinMembership(subscriberId, creatorId)

            // then
            verify(subscriptionRepository).save(any(Subscription::class.java))
        }

        @Test
        @DisplayName("성공: 멤버십 해지 시 Tier가 FOLLOW로 떨어지고 만료일이 지워진다")
        fun cancelMembershipSuccess() {
            // given
            val followSubscription = buildFollowSubscription()
            followSubscription.upgradeToMembership()
            given(subscriptionRepository.findByUserIdAndCreatorId(subscriberId, creatorId)).willReturn(followSubscription)

            // when
            subscriptionService.cancelMembership(subscriberId, creatorId)

            // then
            assertThat(followSubscription.tier).isEqualTo(SubscriptionTier.FOLLOW)
            assertThat(followSubscription.expiredAt).isNull()
        }
    }

    @Nested
    @DisplayName("API 5: 구독 목록 조회 테스트")
    inner class GetMySubscriptionsTest {
        private val subscriberId = 1L
        private val creatorId = 2L

        private fun buildFollowSubscription(): Subscription {
            val subscriber = User(subscriberId, "sub@test.com", "Subscriber", UserRole.USER)
            val creator = User(creatorId, "creator@test.com", "Creator", UserRole.USER)
            return Subscription(user = subscriber, creator = creator, tier = SubscriptionTier.FOLLOW).apply { ReflectionTestUtils.setField(this, "id", 100L) }
        }

        @Test
        @DisplayName("성공: 내 구독 목록을 조회하여 DTO로 반환한다")
        fun getMySubscriptionsSuccess() {
            // given
            val followSubscription = buildFollowSubscription()
            val pageable = PageRequest.of(0, 10)
            val page = PageImpl(listOf(followSubscription))
            given(subscriptionRepository.findMySubscriptions(subscriberId, pageable)).willReturn(page)
            val resultRow = arrayOf<Any>(creatorId, 150L)
            given(subscriptionRepository.countFollowersGroupedByCreatorIds(listOf(creatorId)))
                .willReturn(listOf(resultRow))

            // when
            val infos = subscriptionService.getMySubscriptions(subscriberId, pageable)

            // then
            assertThat(infos.content).hasSize(1)
            assertThat(infos.content[0].creatorId).isEqualTo(creatorId)
            assertThat(infos.content[0].tier).isEqualTo(SubscriptionTier.FOLLOW)
            assertThat(infos.content[0].followerCount).isEqualTo(150L)
        }
    }

    @Nested
    @DisplayName("API 6: 내 구독 총 수 조회 테스트")
    inner class GetMySubscriptionCountTest {
        @Test
        @DisplayName("성공: 내가 구독 중인 총 창작자 수를 반환한다")
        fun getMySubscriptionCountSuccess() {
            // given
            given(subscriptionRepository.countByUserIdAndDeletedAtIsNull(1L)).willReturn(42L)

            // when
            val count = subscriptionService.getMySubscriptionCount(1L)

            // then
            assertThat(count).isEqualTo(42L)
        }
    }

    @Nested
    @DisplayName("API 6: 단건 구독 상태 확인 테스트")
    inner class GetSubscriptionStatusTest {
        private val subscriberId = 1L
        private val creatorId = 2L

        private fun buildFollowSubscription(): Subscription {
            val subscriber = User(subscriberId, "sub@test.com", "Subscriber", UserRole.USER)
            val creator = User(creatorId, "creator@test.com", "Creator", UserRole.USER)
            return Subscription(user = subscriber, creator = creator, tier = SubscriptionTier.FOLLOW).apply { ReflectionTestUtils.setField(this, "id", 100L) }
        }

        @Test
        @DisplayName("성공: 구독하지 않은 경우 NONE 반환")
        fun returnNoneWhenNotSubscribed() {
            // given
            given(subscriptionRepository.findByUserIdAndCreatorId(subscriberId, creatorId)).willReturn(null)

            // when
            val status = subscriptionService.getSubscriptionStatus(subscriberId, creatorId)

            // then
            assertThat(status).isEqualTo(SubscriptionStatus.NONE)
        }

        @Test
        @DisplayName("성공: 팔로우 중인 경우 FOLLOW 반환")
        fun returnFollowWhenFollowing() {
            // given
            val followSubscription = buildFollowSubscription()
            given(subscriptionRepository.findByUserIdAndCreatorId(subscriberId, creatorId)).willReturn(followSubscription)

            // when
            val status = subscriptionService.getSubscriptionStatus(subscriberId, creatorId)

            // then
            assertThat(status).isEqualTo(SubscriptionStatus.FOLLOW)
        }

        @Test
        @DisplayName("성공: 멤버십 가입 중인 경우 MEMBERSHIP 반환")
        fun returnMembershipWhenJoined() {
            // given
            val followSubscription = buildFollowSubscription()
            followSubscription.upgradeToMembership()
            given(subscriptionRepository.findByUserIdAndCreatorId(subscriberId, creatorId)).willReturn(followSubscription)

            // when
            val status = subscriptionService.getSubscriptionStatus(subscriberId, creatorId)

            // then
            assertThat(status).isEqualTo(SubscriptionStatus.MEMBERSHIP)
        }

        @Test
        @DisplayName("성공: 자기 자신을 조회하는 경우 NONE 반환")
        fun returnNoneWhenSelf() {
            // when
            val status = subscriptionService.getSubscriptionStatus(subscriberId, subscriberId)

            // then
            assertThat(status).isEqualTo(SubscriptionStatus.NONE)
            verify(subscriptionRepository, never()).findByUserIdAndCreatorId(anyLong(), anyLong())
        }
    }

    @Nested
    @DisplayName("API 7: 팔로워 수 통계 조회 테스트")
    inner class GetFollowerCountTest {
        @Test
        @DisplayName("성공: 특정 창작자를 팔로우하는 활성 구독자 수를 반환한다")
        fun getFollowerCountSuccess() {
            // given
            given(subscriptionRepository.countByCreatorIdAndDeletedAtIsNull(2L)).willReturn(42L)

            // when
            val count = subscriptionService.getFollowerCount(2L)

            // then
            assertThat(count).isEqualTo(42L)
        }
    }
}
