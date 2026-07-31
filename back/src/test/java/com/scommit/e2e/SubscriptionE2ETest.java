package com.scommit.e2e;

import com.scommit.global.security.SecurityUser;
import com.scommit.global.util.GoldenFileMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = "/e2e-fixtures/subscription_e2e_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SubscriptionE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUpSecurity() {
        // ID 1번(sub@test.com) 유저로 인증된 상태를 강제로 만듭니다. (JWT 필터 우회용)
        SecurityUser mockUser = new SecurityUser(
                1L,
                "sub@test.com",
                "E2E_Subscriber",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(mockUser, null, mockUser.getAuthorities())
        );
    }

    private void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private String performAndGetActualJson(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("1. 창작자 팔로우 (POST /api/subscriptions/follow/{creatorId})")
    class FollowTests {
        @Test
        @DisplayName("성공: 창작자 팔로우 (200)")
        void follow_Success() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/subscriptions/follow/2"))
                    .andExpect(status().isOk())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/follow_success.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 인증되지 않은 사용자 (401)")
        void follow_Unauthorized() throws Exception {
            clearAuth();
            MvcResult result = mockMvc.perform(post("/api/subscriptions/follow/2"))
                    .andExpect(status().isUnauthorized())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/follow_401.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 자기 자신을 팔로우 (400-3)")
        void follow_Self() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/subscriptions/follow/1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/follow_400_self.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 존재하지 않는 유저 팔로우 (404-2)")
        void follow_NotFound() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/subscriptions/follow/999"))
                    .andExpect(status().isNotFound())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/follow_404.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 이미 팔로우 중인 창작자 팔로우 (409-5)")
        void follow_AlreadySubscribed() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/subscriptions/follow/3"))
                    .andExpect(status().isConflict())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/follow_409.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: creatorId 타입 불일치 (400)")
        void follow_TypeMismatch() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/subscriptions/follow/abc"))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/follow_400_type.json", performAndGetActualJson(result));
        }
    }

    @Nested
    @DisplayName("2. 창작자 언팔로우 (DELETE /api/subscriptions/follow/{creatorId})")
    class UnfollowTests {
        @Test
        @DisplayName("성공: 창작자 언팔로우 (200)")
        void unfollow_Success() throws Exception {
            MvcResult result = mockMvc.perform(delete("/api/subscriptions/follow/3"))
                    .andExpect(status().isOk())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/unfollow_success.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 인증되지 않은 사용자 (401)")
        void unfollow_Unauthorized() throws Exception {
            clearAuth();
            MvcResult result = mockMvc.perform(delete("/api/subscriptions/follow/3"))
                    .andExpect(status().isUnauthorized())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/unfollow_401.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 존재하지 않는 유저 언팔로우 (404-2)")
        void unfollow_NotFound() throws Exception {
            MvcResult result = mockMvc.perform(delete("/api/subscriptions/follow/999"))
                    .andExpect(status().isNotFound())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/unfollow_404.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 팔로우하지 않은 창작자 언팔로우 (404-5)")
        void unfollow_NotSubscribed() throws Exception {
            MvcResult result = mockMvc.perform(delete("/api/subscriptions/follow/2"))
                    .andExpect(status().isNotFound())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/unfollow_404_not_subscribed.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: creatorId 타입 불일치 (400)")
        void unfollow_TypeMismatch() throws Exception {
            MvcResult result = mockMvc.perform(delete("/api/subscriptions/follow/abc"))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/unfollow_400_type.json", performAndGetActualJson(result));
        }
    }

    @Nested
    @DisplayName("3. 멤버십 가입 (POST /api/subscriptions/membership/{creatorId})")
    class JoinMembershipTests {
        @Test
        @DisplayName("성공: 멤버십 가입 (200)")
        void joinMembership_Success() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/subscriptions/membership/2"))
                    .andExpect(status().isOk())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/membership_join_success.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("성공: 팔로우 상태에서 멤버십 가입 시 자동 업그레이드 (200)")
        void joinMembership_UpgradeFromFollow() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/subscriptions/membership/3"))
                    .andExpect(status().isOk())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/membership_join_upgrade.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 인증되지 않은 사용자 (401)")
        void joinMembership_Unauthorized() throws Exception {
            clearAuth();
            MvcResult result = mockMvc.perform(post("/api/subscriptions/membership/2"))
                    .andExpect(status().isUnauthorized())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/membership_join_401.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 자기 자신 멤버십 가입 (400-3)")
        void joinMembership_Self() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/subscriptions/membership/1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/membership_join_400_self.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 존재하지 않는 유저 멤버십 가입 (404-2)")
        void joinMembership_NotFound() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/subscriptions/membership/999"))
                    .andExpect(status().isNotFound())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/membership_join_404.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 이미 가입한 멤버십 재가입 (409-6)")
        void joinMembership_AlreadyMembership() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/subscriptions/membership/4"))
                    .andExpect(status().isConflict())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/membership_join_409.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: creatorId 타입 불일치 (400)")
        void joinMembership_TypeMismatch() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/subscriptions/membership/abc"))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/membership_join_400_type.json", performAndGetActualJson(result));
        }
    }

    @Nested
    @DisplayName("4. 멤버십 해지 (DELETE /api/subscriptions/membership/{creatorId})")
    class CancelMembershipTests {
        @Test
        @DisplayName("성공: 멤버십 해지 시 팔로우로 강등 (200)")
        void cancelMembership_Success() throws Exception {
            MvcResult result = mockMvc.perform(delete("/api/subscriptions/membership/4"))
                    .andExpect(status().isOk())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/membership_cancel_success.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 인증되지 않은 사용자 (401)")
        void cancelMembership_Unauthorized() throws Exception {
            clearAuth();
            MvcResult result = mockMvc.perform(delete("/api/subscriptions/membership/4"))
                    .andExpect(status().isUnauthorized())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/membership_cancel_401.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 가입하지 않은 멤버십 해지 (409-6)")
        void cancelMembership_NotMembership() throws Exception {
            MvcResult result = mockMvc.perform(delete("/api/subscriptions/membership/3"))
                    .andExpect(status().isConflict())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/membership_cancel_409_not_membership.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 존재하지 않는 유저 멤버십 해지 (404-2)")
        void cancelMembership_NotFound() throws Exception {
            MvcResult result = mockMvc.perform(delete("/api/subscriptions/membership/999"))
                    .andExpect(status().isNotFound())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/membership_cancel_404.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: creatorId 타입 불일치 (400)")
        void cancelMembership_TypeMismatch() throws Exception {
            MvcResult result = mockMvc.perform(delete("/api/subscriptions/membership/abc"))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/membership_cancel_400_type.json", performAndGetActualJson(result));
        }
    }

    @Nested
    @DisplayName("5. 내 구독 목록 조회 (GET /api/subscriptions)")
    class GetMySubscriptionsTests {
        @Test
        @DisplayName("성공: 구독 목록 조회 (기본 페이징)")
        void getMySubscriptions_Success() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/subscriptions"))
                    .andExpect(status().isOk())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/get_my_subscriptions_success.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("성공: 구독 목록 조회 (page=0, size=1)")
        void getMySubscriptions_Pagination_Small() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/subscriptions")
                            .param("page", "0")
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/get_my_subscriptions_page_0_size_1.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("성공: 구독 목록 조회 (데이터 없는 빈 페이지)")
        void getMySubscriptions_Pagination_Empty() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/subscriptions")
                            .param("page", "99")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/get_my_subscriptions_empty_page.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 인증되지 않은 사용자 (401)")
        void getMySubscriptions_Unauthorized() throws Exception {
            clearAuth();
            MvcResult result = mockMvc.perform(get("/api/subscriptions"))
                    .andExpect(status().isUnauthorized())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/get_my_subscriptions_401.json", performAndGetActualJson(result));
        }
    }

    @Nested
    @DisplayName("6. 내 전체 구독 수 조회 (GET /api/subscriptions/count)")
    class GetMySubscriptionCountTests {
        @Test
        @DisplayName("성공: 구독 수 조회")
        void getMySubscriptionCount_Success() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/subscriptions/count"))
                    .andExpect(status().isOk())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/get_my_subscription_count_success.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 인증되지 않은 사용자 (401)")
        void getMySubscriptionCount_Unauthorized() throws Exception {
            clearAuth();
            MvcResult result = mockMvc.perform(get("/api/subscriptions/count"))
                    .andExpect(status().isUnauthorized())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/get_my_subscription_count_401.json", performAndGetActualJson(result));
        }
    }

    @Nested
    @DisplayName("7. 특정 창작자 구독 상태 조회 (GET /api/subscriptions/status/{creatorId})")
    class GetSubscriptionStatusTests {
        @Test
        @DisplayName("성공: 상태 조회 (FOLLOW)")
        void getStatus_Follow() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/subscriptions/status/3"))
                    .andExpect(status().isOk())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/get_status_follow.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("성공: 상태 조회 (MEMBERSHIP)")
        void getStatus_Membership() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/subscriptions/status/4"))
                    .andExpect(status().isOk())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/get_status_membership.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("성공: 상태 조회 (NONE)")
        void getStatus_None() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/subscriptions/status/2"))
                    .andExpect(status().isOk())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/get_status_none.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 인증되지 않은 사용자 (401)")
        void getStatus_Unauthorized() throws Exception {
            clearAuth();
            MvcResult result = mockMvc.perform(get("/api/subscriptions/status/2"))
                    .andExpect(status().isUnauthorized())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/get_status_401.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("성공: 존재하지 않는 유저 조회 시에도 NONE 반환 (200)")
        void getStatus_NotFound() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/subscriptions/status/999"))
                    .andExpect(status().isOk())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/get_status_999.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: creatorId 타입 불일치 (400)")
        void getStatus_TypeMismatch() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/subscriptions/status/abc"))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/get_status_400_type.json", performAndGetActualJson(result));
        }
    }

    @Nested
    @DisplayName("8. 내 팔로워 수 조회 (GET /api/subscriptions/followers/count)")
    class GetFollowerCountTests {
        @Test
        @DisplayName("성공: 팔로워가 없는 유저 조회 (0 반환)")
        void getFollowerCount_Zero() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/subscriptions/followers/count"))
                    .andExpect(status().isOk())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/get_follower_count_zero.json", performAndGetActualJson(result));
        }

        @Test
        @DisplayName("예외: 인증되지 않은 사용자 (401)")
        void getFollowerCount_Anonymous() throws Exception {
            clearAuth();
            MvcResult result = mockMvc.perform(get("/api/subscriptions/followers/count"))
                    .andExpect(status().isUnauthorized())
                    .andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/get_follower_count_401.json", performAndGetActualJson(result));
        }
    }

    @Nested
    @DisplayName("9. 통합 시나리오 (Lifecycle Flow)")
    class ScenarioTests {
        @Test
        @DisplayName("시나리오: 팔로우 -> 멤버십 업그레이드 -> 다운그레이드 -> 언팔로우 흐름")
        void subscriptionLifecycleScenario() throws Exception {
            // 1. 초기 상태 확인 (NONE)
            MvcResult res1 = mockMvc.perform(get("/api/subscriptions/status/2")).andExpect(status().isOk()).andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/scenario_1_initial.json", performAndGetActualJson(res1));

            // 2. 팔로우 
            mockMvc.perform(post("/api/subscriptions/follow/2")).andExpect(status().isOk());
            
            // 3. 상태 확인 (FOLLOW)
            MvcResult res2 = mockMvc.perform(get("/api/subscriptions/status/2")).andExpect(status().isOk()).andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/scenario_2_followed.json", performAndGetActualJson(res2));

            // 4. 멤버십 가입 (업그레이드)
            mockMvc.perform(post("/api/subscriptions/membership/2")).andExpect(status().isOk());

            // 5. 상태 확인 (MEMBERSHIP)
            MvcResult res3 = mockMvc.perform(get("/api/subscriptions/status/2")).andExpect(status().isOk()).andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/scenario_3_membership.json", performAndGetActualJson(res3));

            // 6. 멤버십 해지 (다운그레이드)
            mockMvc.perform(delete("/api/subscriptions/membership/2")).andExpect(status().isOk());

            // 7. 상태 확인 (FOLLOW 로 복귀)
            MvcResult res4 = mockMvc.perform(get("/api/subscriptions/status/2")).andExpect(status().isOk()).andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/scenario_4_downgraded.json", performAndGetActualJson(res4));

            // 8. 언팔로우 
            mockMvc.perform(delete("/api/subscriptions/follow/2")).andExpect(status().isOk());

            // 9. 최종 상태 확인 (NONE)
            MvcResult res5 = mockMvc.perform(get("/api/subscriptions/status/2")).andExpect(status().isOk()).andReturn();
            GoldenFileMatcher.assertEqualsWithGolden("e2e-fixtures/subscription/scenario_5_final.json", performAndGetActualJson(res5));
        }
    }
}
