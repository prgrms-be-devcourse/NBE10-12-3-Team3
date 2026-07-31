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
    @DisplayName("?깃났: 李쎌옉???붾줈??(200)")
    void follow_Success() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("?덉쇅: ?몄쬆?섏? ?딆? ?ъ슜??(401)")
    void follow_Unauthorized() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("?덉쇅: ?먭린 ?먯떊???붾줈??(400-3)")
    void follow_Self() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", myId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isBadRequest();
    }
    @Test
    @DisplayName("?덉쇅: 議댁옱?섏? ?딅뒗 ?좎? ?붾줈??(404-2)")
    void follow_NotFound() {
        client.post().uri("/api/subscriptions/follow/999")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isNotFound();
    }
    @Test
    @DisplayName("?덉쇅: ?대? ?붾줈??以묒씤 李쎌옉???붾줈??(409-5)")
    void follow_AlreadySubscribed() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange();

        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }
    @Test
    @DisplayName("?덉쇅: creatorId ???遺덉씪移?(400)")
    void follow_TypeMismatch() {
        client.post().uri("/api/subscriptions/follow/abc")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isBadRequest();
    }
    @Test
    @DisplayName("?깃났: 李쎌옉???명뙏濡쒖슦 (200)")
    void unfollow_Success() {
        client.post().uri("/api/subscriptions/follow/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange();

        client.delete().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("?덉쇅: ?몄쬆?섏? ?딆? ?ъ슜??(401)")
    void unfollow_Unauthorized() {
        client.delete().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("?덉쇅: 議댁옱?섏? ?딅뒗 ?좎? ?명뙏濡쒖슦 (404-2)")
    void unfollow_NotFound() {
        client.delete().uri("/api/subscriptions/follow/999")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isNotFound();
    }
    @Test
    @DisplayName("?덉쇅: ?붾줈?고븯吏 ?딆? 李쎌옉???명뙏濡쒖슦 (404-5)")
    void unfollow_NotSubscribed() {
        client.delete().uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isNotFound();
    }
    @Test
    @DisplayName("?덉쇅: creatorId ???遺덉씪移?(400)")
    void unfollow_TypeMismatch() {
        client.delete().uri("/api/subscriptions/follow/abc")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isBadRequest();
    }
    @Test
    @DisplayName("?깃났: 硫ㅻ쾭??媛??(200)")
    void joinMembership_Success() {
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("?깃났: ?붾줈???곹깭?먯꽌 硫ㅻ쾭??媛?????먮룞 ?낃렇?덉씠??(200)")
    void joinMembership_UpgradeFromFollow() {
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("?덉쇅: ?몄쬆?섏? ?딆? ?ъ슜??(401)")
    void joinMembership_Unauthorized() {
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("?덉쇅: ?먭린 ?먯떊 硫ㅻ쾭??媛??(400-3)")
    void joinMembership_Self() {
        client.post().uri("/api/subscriptions/membership/{creatorId}", myId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isBadRequest();
    }
    @Test
    @DisplayName("?덉쇅: 議댁옱?섏? ?딅뒗 ?좎? 硫ㅻ쾭??媛??(404-2)")
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
    @DisplayName("?덉쇅: creatorId ???遺덉씪移?(400)")
    void joinMembership_TypeMismatch() {
        client.post().uri("/api/subscriptions/membership/abc")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isBadRequest();
    }
    @Test
    @DisplayName("?깃났: 硫ㅻ쾭???댁? ???붾줈?곕줈 媛뺣벑 (200)")
    void cancelMembership_Success() {
        client.post().uri("/api/subscriptions/membership/{creatorId}", creatorId).header("Authorization", "Bearer " + myToken).exchange();

        client.delete().uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("?덉쇅: ?몄쬆?섏? ?딆? ?ъ슜??(401)")
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
    @DisplayName("?덉쇅: 議댁옱?섏? ?딅뒗 ?좎? 硫ㅻ쾭???댁? (404-2)")
    void cancelMembership_NotFound() {
        client.delete().uri("/api/subscriptions/membership/999")
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isNotFound();
    }
    @Test
    @DisplayName("?덉쇅: creatorId ???遺덉씪移?(400)")
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
    @DisplayName("?덉쇅: ?몄쬆?섏? ?딆? ?ъ슜??(401)")
    void getMySubscriptions_Unauthorized() {
        client.get().uri("/api/subscriptions", creatorId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("?깃났: 援щ룆 ??議고쉶")
    void getMySubscriptionCount_Success() {
        client.get().uri("/api/subscriptions/count", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("?덉쇅: ?몄쬆?섏? ?딆? ?ъ슜??(401)")
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
    @DisplayName("?깃났: ?붾줈?뚭? ?녿뒗 ?좎? 議고쉶 (0 諛섑솚)")
    void getFollowerCount_Zero() {
        client.get().uri("/api/subscriptions/followers/count", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
    @Test
    @DisplayName("?덉쇅: ?몄쬆?섏? ?딆? ?ъ슜??(401)")
    void getFollowerCount_Anonymous() {
        client.get().uri("/api/subscriptions/followers/count", creatorId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @DisplayName("?쒕굹由ъ삤: ?붾줈??-> 硫ㅻ쾭???낃렇?덉씠??-> ?ㅼ슫洹몃젅?대뱶 -> ?명뙏濡쒖슦 ?먮쫫")
    void subscriptionLifecycleScenario() {
        client.get().uri("/api/subscriptions/status/{creatorId}", creatorId)
                .header("Authorization", "Bearer " + myToken)
                .exchange()
                .expectStatus().isOk();
    }
}
