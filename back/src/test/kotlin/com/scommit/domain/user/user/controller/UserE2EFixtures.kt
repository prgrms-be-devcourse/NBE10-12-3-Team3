package com.scommit.domain.user.user.controller

import com.scommit.domain.subscription.subscription.entity.Subscription
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.repository.UserRepository
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate
import java.util.UUID

// UserControllerE2ETest 전용 픽스처. BaseInitData는 참조하지 않는다.
// 회원가입 API로 만들 수 없는 데이터(팔로워 수 집계용 Subscription, 검색 페이징용 다건 유저)만
// 리포지토리로 직접 심는다.
@TestConfiguration
class UserE2EFixtures {
    data class FollowerCountFixture(
        val creatorId: Long,
        val creatorEmail: String,
        val followerCount: Int,
    )

    data class SearchPagingFixture(
        val nicknamePrefix: String,
        val userIds: List<Long>,
    )

    @Bean
    fun followerCountFixture(
        userRepository: UserRepository,
        subscriptionRepository: SubscriptionRepository,
        passwordEncoder: PasswordEncoder,
    ): FollowerCountFixture {
        val creatorEmail = "e2e-fixture-creator-${UUID.randomUUID()}@test.com"
        val creator =
            userRepository.save(
                User(
                    creatorEmail,
                    passwordEncoder.encode("fixture-password"),
                    "e2eFxCreator${shortId()}",
                    null,
                    UserRole.USER,
                ),
            )

        repeat(FOLLOWER_COUNT) {
            val subscriber =
                userRepository.save(
                    User(
                        "e2e-fixture-sub-${UUID.randomUUID()}@test.com",
                        passwordEncoder.encode("fixture-password"),
                        "e2eFxSub${shortId()}",
                        null,
                        UserRole.USER,
                    ),
                )

            subscriptionRepository.save(
                Subscription(subscriber, creator, SubscriptionTier.FOLLOW, LocalDate.now(), null),
            )
        }

        return FollowerCountFixture(checkNotNull(creator.id), creatorEmail, FOLLOWER_COUNT)
    }

    @Bean
    fun searchPagingFixture(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder,
    ): SearchPagingFixture {
        val ids =
            (1..SEARCH_USER_COUNT).map { i ->
                userRepository
                    .save(
                        User(
                            "e2e-fixture-search-$i-${UUID.randomUUID()}@test.com",
                            passwordEncoder.encode("fixture-password"),
                            "$SEARCH_NICKNAME_PREFIX$i",
                            null,
                            UserRole.USER,
                        ),
                    ).id
                    .let { checkNotNull(it) }
            }
        return SearchPagingFixture(SEARCH_NICKNAME_PREFIX, ids)
    }

    companion object {
        const val SEARCH_NICKNAME_PREFIX = "e2eSearchFx"
        const val SEARCH_USER_COUNT = 5
        const val FOLLOWER_COUNT = 3

        private fun shortId(): String =
            UUID
                .randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6)
    }
}
