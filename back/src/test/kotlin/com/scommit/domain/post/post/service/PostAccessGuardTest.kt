package com.scommit.domain.post.post.service

import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.subscription.subscription.entity.Subscription
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils

/**
 * PostAccessGuard 단위 테스트
 * - PostService.getPost() / PostMediaService의 PRIVATE·PAID 판정이 여기 하나로
 *   모여 있으므로(TRIPLES-54 재발 방지), 판정 로직 자체는 이 클래스에서만 촘촘히 검증한다.
 */
@ExtendWith(MockitoExtension::class)
class PostAccessGuardTest {
    @Mock
    private lateinit var subscriptionRepository: SubscriptionRepository

    @InjectMocks
    private lateinit var guard: PostAccessGuard

    private lateinit var owner: User
    private lateinit var otherUser: User

    @BeforeEach
    fun setUp() {
        owner =
            User("owner@example.com", null, "작성자", null, UserRole.USER)
                .also { ReflectionTestUtils.setField(it, "id", 1L) }
        otherUser =
            User("other@example.com", null, "다른유저", null, UserRole.USER)
                .also { ReflectionTestUtils.setField(it, "id", 2L) }
    }

    private fun buildPost(
        publishStatus: PublishStatus,
        accessLevel: PostAccessLevel,
    ): Post =
        Post(owner, null, "제목", "내용", publishStatus, accessLevel)
            .also { ReflectionTestUtils.setField(it, "id", 10L) }

    @Nested
    @DisplayName("isOwner")
    inner class IsOwner {
        @Test
        @DisplayName("작성자 본인이면 true를 반환한다")
        fun isOwner_Self_True() {
            val post = buildPost(PublishStatus.PUBLIC, PostAccessLevel.FREE)

            assertThat(guard.isOwner(post, owner)).isTrue()
        }

        @Test
        @DisplayName("타인이면 false를 반환한다")
        fun isOwner_Other_False() {
            val post = buildPost(PublishStatus.PUBLIC, PostAccessLevel.FREE)

            assertThat(guard.isOwner(post, otherUser)).isFalse()
        }

        @Test
        @DisplayName("비로그인이면 false를 반환한다")
        fun isOwner_Anonymous_False() {
            val post = buildPost(PublishStatus.PUBLIC, PostAccessLevel.FREE)

            assertThat(guard.isOwner(post, null)).isFalse()
        }
    }

    @Nested
    @DisplayName("blockIfPrivate")
    inner class BlockIfPrivate {
        @Test
        @DisplayName("PRIVATE 게시글을 작성자가 아닌 유저가 접근하면 ACCESS_DENIED 예외를 던진다")
        fun blockIfPrivate_NotOwner_Throws() {
            val post = buildPost(PublishStatus.PRIVATE, PostAccessLevel.FREE)

            assertThatThrownBy { guard.blockIfPrivate(post, otherUser) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }

        @Test
        @DisplayName("PRIVATE 게시글을 비로그인 유저가 접근하면 ACCESS_DENIED 예외를 던진다")
        fun blockIfPrivate_Anonymous_Throws() {
            val post = buildPost(PublishStatus.PRIVATE, PostAccessLevel.FREE)

            assertThatThrownBy { guard.blockIfPrivate(post, null) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }

        @Test
        @DisplayName("PRIVATE 게시글도 작성자 본인은 통과한다")
        fun blockIfPrivate_Owner_Passes() {
            val post = buildPost(PublishStatus.PRIVATE, PostAccessLevel.FREE)

            assertThatCode { guard.blockIfPrivate(post, owner) }.doesNotThrowAnyException()
        }

        @Test
        @DisplayName("PUBLIC 게시글은 누구나 통과한다")
        fun blockIfPrivate_Public_Passes() {
            val post = buildPost(PublishStatus.PUBLIC, PostAccessLevel.FREE)

            assertThatCode { guard.blockIfPrivate(post, null) }.doesNotThrowAnyException()
        }
    }

    @Nested
    @DisplayName("enforceFullAccess")
    inner class EnforceFullAccess {
        @Test
        @DisplayName("PAID 게시글을 멤버십 미구독 유저가 접근하면 ACCESS_DENIED 예외를 던진다")
        fun enforceFullAccess_Paid_NotMember_Throws() {
            val post = buildPost(PublishStatus.PUBLIC, PostAccessLevel.PAID)
            given(subscriptionRepository.findByUserIdAndCreatorId(2L, 1L)).willReturn(null)

            assertThatThrownBy { guard.enforceFullAccess(post, otherUser) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }

        @Test
        @DisplayName("PAID 게시글을 비로그인 유저가 접근하면 ACCESS_DENIED 예외를 던진다")
        fun enforceFullAccess_Paid_Anonymous_Throws() {
            val post = buildPost(PublishStatus.PUBLIC, PostAccessLevel.PAID)

            assertThatThrownBy { guard.enforceFullAccess(post, null) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }

        @Test
        @DisplayName("PAID 게시글이라도 멤버십 구독자는 통과한다")
        fun enforceFullAccess_Paid_Member_Passes() {
            val post = buildPost(PublishStatus.PUBLIC, PostAccessLevel.PAID)
            val subscription = Subscription(otherUser, owner, SubscriptionTier.MEMBERSHIP)
            given(subscriptionRepository.findByUserIdAndCreatorId(2L, 1L)).willReturn(subscription)

            assertThatCode { guard.enforceFullAccess(post, otherUser) }.doesNotThrowAnyException()
        }

        @Test
        @DisplayName("PAID 게시글이라도 작성자 본인은 구독 여부와 무관하게 통과한다")
        fun enforceFullAccess_Paid_Owner_Passes() {
            val post = buildPost(PublishStatus.PUBLIC, PostAccessLevel.PAID)

            assertThatCode { guard.enforceFullAccess(post, owner) }.doesNotThrowAnyException()
        }

        @Test
        @DisplayName("PRIVATE 게시글은 PAID 여부와 무관하게 작성자가 아니면 차단한다")
        fun enforceFullAccess_Private_NotOwner_Throws() {
            val post = buildPost(PublishStatus.PRIVATE, PostAccessLevel.FREE)

            assertThatThrownBy { guard.enforceFullAccess(post, otherUser) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }

        @Test
        @DisplayName("PUBLIC/FREE 게시글은 비로그인 유저도 통과한다")
        fun enforceFullAccess_PublicFree_Anonymous_Passes() {
            val post = buildPost(PublishStatus.PUBLIC, PostAccessLevel.FREE)

            assertThatCode { guard.enforceFullAccess(post, null) }.doesNotThrowAnyException()
        }
    }
}
