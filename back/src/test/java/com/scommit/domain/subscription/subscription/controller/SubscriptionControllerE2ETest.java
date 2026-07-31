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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:subscription_e2edb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SubscriptionControllerE2ETest {

    private static final Long NON_EXISTENT_ID = 999L;

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
        var response = client.post().uri("/api/subscriptions/follow/{creatorId}", myId)
                .header("Authorization", "Bearer " + myToken)
                .exchange();
        expectResultCode(response, HttpStatus.BAD_REQUEST, "400-3");
    }
    @Test
    @DisplayName("예외: 존재하지 않는 유저 팔로우 (404-2)")
    void follow_NotFound() {
        var response = client.post().uri("/api/subscriptions/follow/{id}", NON_EXISTENT_ID)
                .header("Authorization", "Bearer " + myToken)
                .exchange();
        expectResultCode(response, HttpStatus.NOT_FOUND, "404-2");
    }
    @Test
    @DisplayName("예외: 이미 팔로우 중인 창작자 팔로우 (409-2)")
    void follow_AlreadySubscribed() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange().expectStatus().isOk();

        var response = client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange();
        expectResultCode(response, HttpStatus.CONFLICT, "409-2");
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
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange().expectStatus().isOk();

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
    @DisplayName("예외: 존재하지 않는 유저 언팔로우 (404-4)")
    void unfollow_NotFound() {
        var response = client.delete().uri("/api/subscriptions/follow/{id}", NON_EXISTENT_ID)
                .header("Authorization", "Bearer " + myToken)
                .exchange();
        expectResultCode(response, HttpStatus.NOT_FOUND, "404-4");
    }
    @Test
    @DisplayName("예외: 팔로우하지 않은 창작자 언팔로우 (404-4)")
    void unfollow_NotSubscribed() {
        var response = client.delete().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange();
        expectResultCode(response, HttpStatus.NOT_FOUND, "404-4");
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
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange().expectStatus().isOk();

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
        var response = client.post().uri("/api/subscriptions/membership/{creatorId}", myId)
                .header("Authorization", "Bearer " + myToken)
                .exchange();
        expectResultCode(response, HttpStatus.BAD_REQUEST, "400-3");
    }
    @Test
    @DisplayName("예외: 존재하지 않는 유저 멤버십 가입 (404-2)")
    void joinMembership_NotFound() {
        var response = client.post().uri("/api/subscriptions/membership/{id}", NON_EXISTENT_ID)
                .header("Authorization", "Bearer " + myToken)
                .exchange();
        expectResultCode(response, HttpStatus.NOT_FOUND, "404-2");
    }
    @Test
    @DisplayName("예외: 이미 가입한 멤버십 가입 (409-5)")
    void joinMembership_AlreadyMembership() {
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange().expectStatus().isOk();

        var response = client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange();
        expectResultCode(response, HttpStatus.CONFLICT, "409-5");
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
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange().expectStatus().isOk();

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
    @DisplayName("예외: 가입하지 않은 멤버십 취소 (409-6)")
    void cancelMembership_NotMembership() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange().expectStatus().isOk();

        var response = client.delete().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange();
        expectResultCode(response, HttpStatus.CONFLICT, "409-6");
    }
    @Test
    @DisplayName("예외: 존재하지 않는 유저 멤버십 취소 (404-4)")
    void cancelMembership_NotFound() {
        var response = client.delete().uri("/api/subscriptions/membership/{id}", NON_EXISTENT_ID)
                .header("Authorization", "Bearer " + myToken)
                .exchange();
        expectResultCode(response, HttpStatus.NOT_FOUND, "404-4");
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
    @DisplayName("성공: 구독 목록 조회 (기본 페이징)")
    void getMySubscriptions_Success() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange().expectStatus().isOk();
        client.get().uri("/api/subscriptions")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.content").isArray()
                .jsonPath("$.data.content.length()").isEqualTo(1)
                .jsonPath("$.data.content[0].creatorId").isEqualTo(creatorId);
    }
    @Test
    @DisplayName("성공: 구독 목록 조회 (page=0, size=1)")
    void getMySubscriptions_Pagination_Small() {
        client.get().uri("/api/subscriptions?page=0&size=1")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("성공: 구독 목록 조회 (데이터 없는 빈 페이지)")
    void getMySubscriptions_Pagination_Empty() {
        client.get().uri("/api/subscriptions?page=99&size=10")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("예외: 인증되지 않은 사용자 (401)")
    void getMySubscriptions_Unauthorized() {
        client.get().uri("/api/subscriptions")
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("성공: 구독 수 조회")
    void getMySubscriptionCount_Success() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange().expectStatus().isOk();
        client.get().uri("/api/subscriptions/count")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data").isEqualTo(1);
    }
    @Test
    @DisplayName("예외: 인증되지 않은 사용자 (401)")
    void getMySubscriptionCount_Unauthorized() {
        client.get().uri("/api/subscriptions/count")
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("성공: 상태 조회 (FOLLOW)")
    void getStatus_Follow() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange().expectStatus().isOk();
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("FOLLOW");
    }
    @Test
    @DisplayName("성공: 상태 조회 (MEMBERSHIP)")
    void getStatus_Membership() {
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange().expectStatus().isOk();
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("MEMBERSHIP");
    }
    @Test
    @DisplayName("성공: 상태 조회 (NONE)")
    void getStatus_None() {
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("NONE");
    }
    @Test
    @DisplayName("예외: 인증되지 않은 사용자 (401)")
    void getStatus_Unauthorized() {
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("성공: 존재하지 않는 유저 조회 시에도 NONE 반환 (200)")
    void getStatus_NotFound() {
        client.get().uri("/api/subscriptions/status/999")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("NONE");
    }
    @Test
    @DisplayName("예외: creatorId 타입 불일치 (400)")
    void getStatus_TypeMismatch() {
        client.get().uri("/api/subscriptions/status/abc")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isBadRequest();
    }
    @Test
    @DisplayName("성공: 팔로워 수 없는 유저 조회 (0 반환)")
    void getFollowerCount_Zero() {
        client.get().uri("/api/subscriptions/followers/count")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data").isEqualTo(0);
    }
    @Test
    @DisplayName("예외: 인증되지 않은 사용자 (401)")
    void getFollowerCount_Anonymous() {
        client.get().uri("/api/subscriptions/followers/count")
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("시나리오: 팔로우 -> 멤버십 업그레이드 -> 다운그레이드 -> 언팔로우 흐름")
    void subscriptionLifecycleScenario() {
        // 1. 상태 조회 (초기 NONE)
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("NONE");
        
        // 2. 팔로우
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
                
        // 3. 상태 조회 (FOLLOW)
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("FOLLOW");

        // 4. 멤버십 업그레이드
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();

        // 5. 상태 조회 (MEMBERSHIP)
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("MEMBERSHIP");

        // 6. 멤버십 취소 (다운그레이드)
        client.delete().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();

        // 7. 상태 조회 (FOLLOW)
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("FOLLOW");

        // 8. 언팔로우
        client.delete().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();

        // 9. 상태 조회 (NONE)
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("NONE");

        // 10. 재팔로우 (NONE -> FOLLOW)
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("FOLLOW");

        // 11. 멤버십 가입 (FOLLOW -> MEMBERSHIP)
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();

        // 12. 멤버십 다시 취소 (MEMBERSHIP -> FOLLOW)
        client.delete().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();

        // 13. 멤버십 재가입 (FOLLOW -> MEMBERSHIP)
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("MEMBERSHIP");
    }
}
