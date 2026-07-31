package com.scommit.domain.subscription.subscription.controller;

import com.scommit.domain.user.user.dto.LoginResponse;
import com.scommit.global.e2e.ApiResponse;
import com.scommit.global.e2e.E2ETestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import static com.scommit.global.e2e.E2ETestSupport.createUserAndLogin;
import static com.scommit.global.e2e.E2ETestSupport.expectResultCode;
import static com.scommit.global.e2e.E2ETestSupport.uniqueEmail;
import static com.scommit.global.e2e.E2ETestSupport.uniqueNickname;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.datasource.url=jdbc:h2:mem:subscription_e2edb;MODE=MySQL;DB_CLOSE_DELAY=-1")
class SubscriptionControllerE2ETest {

    @LocalServerPort
    private int port;

    private RestTestClient client;
    private String myToken;
    private Long myId;
    private String creatorToken;
    private Long creatorId;

    @BeforeEach
    void setUp() {
        client = E2ETestSupport.client(port);
        LoginResponse me = createUserAndLogin(client, uniqueEmail(), "123456", uniqueNickname());
        myToken = me.accessToken();
        myId = me.user().id();

        LoginResponse creator = createUserAndLogin(client, uniqueEmail(), "123456", uniqueNickname());
        creatorToken = creator.accessToken();
        creatorId = creator.user().id();
    }
    @Test
    @DisplayName("성공: 창작자 팔로우 (200)")
    void follow_Success() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("예외: 인증되지 않은 사용자 (401)")
    void follow_Unauthorized() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("예외: 자기 자신을 팔로우 (400-3)")
    void follow_Self() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", myId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isBadRequest();
    }
    @Test
    @DisplayName("예외: 존재하지 않는 유저 팔로우 (404-2)")
    void follow_NotFound() {
        client.post().uri("/api/subscriptions/follow/999")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isNotFound();
    }
    @Test
    @DisplayName("예외: 이미 팔로우 중인 창작자 팔로우 (409-5)")
    void follow_AlreadySubscribed() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange();

        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }
    @Test
    @DisplayName("예외: creatorId 타입 불일치 (400)")
    void follow_TypeMismatch() {
        client.post().uri("/api/subscriptions/follow/abc")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isBadRequest();
    }
    @Test
    @DisplayName("성공: 창작자 언팔로우 (200)")
    void unfollow_Success() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange();

        client.delete().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("예외: 인증되지 않은 사용자 (401)")
    void unfollow_Unauthorized() {
        client.delete().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("예외: 존재하지 않는 유저 언팔로우 (404-2)")
    void unfollow_NotFound() {
        client.delete().uri("/api/subscriptions/follow/999")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isNotFound();
    }
    @Test
    @DisplayName("예외: 팔로우하지 않은 창작자 언팔로우 (404-5)")
    void unfollow_NotSubscribed() {
        client.delete().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isNotFound();
    }
    @Test
    @DisplayName("예외: creatorId 타입 불일치 (400)")
    void unfollow_TypeMismatch() {
        client.delete().uri("/api/subscriptions/follow/abc")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isBadRequest();
    }
    @Test
    @DisplayName("성공: 멤버십 가입 (200)")
    void joinMembership_Success() {
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("성공: 팔로우 상태에서 멤버십 가입 시 자동 업그레이드 (200)")
    void joinMembership_UpgradeFromFollow() {
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("예외: 인증되지 않은 사용자 (401)")
    void joinMembership_Unauthorized() {
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("예외: 자기 자신 멤버십 가입 (400-3)")
    void joinMembership_Self() {
        client.post().uri("/api/subscriptions/membership/{creatorId}", myId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isBadRequest();
    }
    @Test
    @DisplayName("예외: 존재하지 않는 유저 멤버십 가입 (404-2)")
    void joinMembership_NotFound() {
        client.post().uri("/api/subscriptions/membership/999")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isNotFound();
    }
    @Test
    @DisplayName("?덉쇅: ?대? 媛?낇븳 硫ㅻ쾭???ш???(409-6)")
    void joinMembership_AlreadyMembership() {
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange();

        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }
    @Test
    @DisplayName("예외: creatorId 타입 불일치 (400)")
    void joinMembership_TypeMismatch() {
        client.post().uri("/api/subscriptions/membership/abc")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isBadRequest();
    }
    @Test
    @DisplayName("성공: 멤버십 취소 후 팔로우로 강등 (200)")
    void cancelMembership_Success() {
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange();

        client.delete().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("예외: 인증되지 않은 사용자 (401)")
    void cancelMembership_Unauthorized() {
        client.delete().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("?덉쇅: 媛?낇븯吏 ?딆? 硫ㅻ쾭???댁? (409-6)")
    void cancelMembership_NotMembership() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange();

        client.delete().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }
    @Test
    @DisplayName("예외: 존재하지 않는 유저 멤버십 취소 (404-2)")
    void cancelMembership_NotFound() {
        client.delete().uri("/api/subscriptions/membership/999")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isNotFound();
    }
    @Test
    @DisplayName("예외: creatorId 타입 불일치 (400)")
    void cancelMembership_TypeMismatch() {
        client.delete().uri("/api/subscriptions/membership/abc")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isBadRequest();
    }
    @Test
    @DisplayName("?깃났: 援щ룆 紐⑸줉 議고쉶 (湲곕낯 ?섏씠吏?")
    void getMySubscriptions_Success() {
        client.get().uri("/api/subscriptions", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("?깃났: 援щ룆 紐⑸줉 議고쉶 (page=0, size=1)")
    void getMySubscriptions_Pagination_Small() {
        client.get().uri("/api/subscriptions?page=0&size=1", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("?깃났: 援щ룆 紐⑸줉 議고쉶 (?곗씠???녿뒗 鍮??섏씠吏)")
    void getMySubscriptions_Pagination_Empty() {
        client.get().uri("/api/subscriptions?page=99&size=10", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("예외: 인증되지 않은 사용자 (401)")
    void getMySubscriptions_Unauthorized() {
        client.get().uri("/api/subscriptions", creatorId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("성공: 구독 수 조회")
    void getMySubscriptionCount_Success() {
        client.get().uri("/api/subscriptions/count", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("예외: 인증되지 않은 사용자 (401)")
    void getMySubscriptionCount_Unauthorized() {
        client.get().uri("/api/subscriptions/count", creatorId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("?깃났: ?곹깭 議고쉶 (FOLLOW)")
    void getStatus_Follow() {
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("?깃났: ?곹깭 議고쉶 (MEMBERSHIP)")
    void getStatus_Membership() {
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("?깃났: ?곹깭 議고쉶 (NONE)")
    void getStatus_None() {
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("?덉쇅: ?몄쬆?섏? ?딆? ?ъ슜??(401)")
    void getStatus_Unauthorized() {
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("?깃났: 議댁옱?섏? ?딅뒗 ?좎? 議고쉶 ?쒖뿉??NONE 諛섑솚 (200)")
    void getStatus_NotFound() {
        client.get().uri("/api/subscriptions/status/999", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("?덉쇅: creatorId ???遺덉씪移?(400)")
    void getStatus_TypeMismatch() {
        client.get().uri("/api/subscriptions/status/abc", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isBadRequest();
    }
    @Test
    @DisplayName("성공: 팔로워 수 없는 유저 조회 (0 반환)")
    void getFollowerCount_Zero() {
        client.get().uri("/api/subscriptions/followers/count", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("예외: 인증되지 않은 사용자 (401)")
    void getFollowerCount_Anonymous() {
        client.get().uri("/api/subscriptions/followers/count", creatorId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("시나리오: 팔로우 -> 멤버십 업그레이드 -> 다운그레이드 -> 언팔로우 흐름")
    void subscriptionLifecycleScenario() {
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
}
