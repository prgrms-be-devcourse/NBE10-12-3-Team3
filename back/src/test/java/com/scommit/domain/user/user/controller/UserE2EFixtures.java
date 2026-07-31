package com.scommit.domain.user.user.controller;

import com.scommit.domain.subscription.subscription.entity.Subscription;
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier;
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository;
import com.scommit.domain.user.user.entity.User;
import com.scommit.domain.user.user.entity.UserRole;
import com.scommit.domain.user.user.repository.UserRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// UserControllerE2ETest 전용 픽스처. BaseInitData는 참조하지 않는다.
// 회원가입 API로 만들 수 없는 데이터(팔로워 수 집계용 Subscription, 검색 페이징용 다건 유저)만
// 리포지토리로 직접 심는다.
@TestConfiguration
public class UserE2EFixtures {

    public static final String SEARCH_NICKNAME_PREFIX = "e2eSearchFx";
    public static final int SEARCH_USER_COUNT = 5;
    public static final int FOLLOWER_COUNT = 3;

    public record FollowerCountFixture(Long creatorId, String creatorEmail, int followerCount) {}

    public record SearchPagingFixture(String nicknamePrefix, List<Long> userIds) {}

    @Bean
    public FollowerCountFixture followerCountFixture(
            UserRepository userRepository,
            SubscriptionRepository subscriptionRepository,
            PasswordEncoder passwordEncoder
    ) {
        String creatorEmail = "e2e-fixture-creator-" + UUID.randomUUID() + "@test.com";
        User creator = userRepository.save(User.builder()
                .email(creatorEmail)
                .password(passwordEncoder.encode("fixture-password"))
                .nickname("e2eFxCreator" + shortId())
                .role(UserRole.USER)
                .build());

        for (int i = 0; i < FOLLOWER_COUNT; i++) {
            User subscriber = userRepository.save(User.builder()
                    .email("e2e-fixture-sub-" + UUID.randomUUID() + "@test.com")
                    .password(passwordEncoder.encode("fixture-password"))
                    .nickname("e2eFxSub" + shortId())
                    .role(UserRole.USER)
                    .build());

            subscriptionRepository.save(Subscription.builder()
                    .user(subscriber)
                    .creator(creator)
                    .tier(SubscriptionTier.FOLLOW)
                    .startedAt(LocalDate.now())
                    .build());
        }

        return new FollowerCountFixture(creator.getId(), creatorEmail, FOLLOWER_COUNT);
    }

    @Bean
    public SearchPagingFixture searchPagingFixture(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= SEARCH_USER_COUNT; i++) {
            User user = userRepository.save(User.builder()
                    .email("e2e-fixture-search-" + i + "-" + UUID.randomUUID() + "@test.com")
                    .password(passwordEncoder.encode("fixture-password"))
                    .nickname(SEARCH_NICKNAME_PREFIX + i)
                    .role(UserRole.USER)
                    .build());
            ids.add(user.getId());
        }
        return new SearchPagingFixture(SEARCH_NICKNAME_PREFIX, ids);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
