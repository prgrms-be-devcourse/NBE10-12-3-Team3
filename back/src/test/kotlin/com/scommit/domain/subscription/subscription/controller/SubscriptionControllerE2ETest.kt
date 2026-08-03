package com.scommit.domain.subscription.subscription.controller

import com.scommit.global.e2e.E2ETestSupport
import com.scommit.global.e2e.E2ETestSupport.createUserAndLogin
import com.scommit.global.e2e.E2ETestSupport.expectResultCode
import com.scommit.global.e2e.E2ETestSupport.uniqueEmail
import com.scommit.global.e2e.E2ETestSupport.uniqueNickname
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:subscription_e2edb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class SubscriptionControllerE2ETest {
    @LocalServerPort
    private var port: Int = 0

    private lateinit var client: RestTestClient
    private lateinit var myToken: String
    private var myId: Long = 0
    private lateinit var creatorToken: String
    private var creatorId: Long = 0

    @BeforeEach
    fun setUp() {
        client = E2ETestSupport.client(port)
        val me = createUserAndLogin(client, uniqueEmail(), "123456", uniqueNickname())
        myToken = me.accessToken
        myId = me.user.id

        val creator = createUserAndLogin(client, uniqueEmail(), "123456", uniqueNickname())
        creatorToken = creator.accessToken
        creatorId = creator.user.id
    }

    @Test
    @DisplayName("성공: 창작자 팔로우 (200)")
    fun follow_Success() {
        client
            .post()
            .uri("/api/subscriptions/follow/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
    }

    @Test
    @DisplayName("예외: 인증되지 않은 사용자 - 팔로우 (401)")
    fun follow_Unauthorized() {
        client
            .post()
            .uri("/api/subscriptions/follow/{creatorId}", creatorId)
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }

    @Test
    @DisplayName("예외: 자기 자신을 팔로우 (400-3)")
    fun follow_Self() {
        val response =
            client
                .post()
                .uri("/api/subscriptions/follow/{creatorId}", myId)
                .header("Authorization", "Bearer $myToken")
                .exchange()
        expectResultCode(response, HttpStatus.BAD_REQUEST, "400-3")
    }

    @Test
    @DisplayName("예외: 존재하지 않는 유저 팔로우 (404-2)")
    fun follow_NotFound() {
        val response =
            client
                .post()
                .uri("/api/subscriptions/follow/{id}", NON_EXISTENT_ID)
                .header("Authorization", "Bearer $myToken")
                .exchange()
        expectResultCode(response, HttpStatus.NOT_FOUND, "404-2")
    }

    @Test
    @DisplayName("예외: 이미 팔로우 중인 창작자 팔로우 (409-2)")
    fun follow_AlreadySubscribed() {
        client
            .post()
            .uri("/api/subscriptions/follow/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        val response =
            client
                .post()
                .uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer $myToken")
                .exchange()
        expectResultCode(response, HttpStatus.CONFLICT, "409-2")
    }

    @Test
    @DisplayName("예외: creatorId 타입 불일치 (400)")
    fun follow_TypeMismatch() {
        client
            .post()
            .uri("/api/subscriptions/follow/abc")
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isBadRequest()
    }

    @Test
    @DisplayName("성공: 창작자 언팔로우 (200)")
    fun unfollow_Success() {
        client
            .post()
            .uri("/api/subscriptions/follow/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        client
            .delete()
            .uri("/api/subscriptions/follow/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
    }

    @Test
    @DisplayName("예외: 인증되지 않은 사용자 - 언팔로우 (401)")
    fun unfollow_Unauthorized() {
        client
            .delete()
            .uri("/api/subscriptions/follow/{creatorId}", creatorId)
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }

    @Test
    @DisplayName("예외: 존재하지 않는 유저 언팔로우 (404-4)")
    fun unfollow_NotFound() {
        val response =
            client
                .delete()
                .uri("/api/subscriptions/follow/{id}", NON_EXISTENT_ID)
                .header("Authorization", "Bearer $myToken")
                .exchange()
        expectResultCode(response, HttpStatus.NOT_FOUND, "404-4")
    }

    @Test
    @DisplayName("예외: 팔로우하지 않은 창작자 언팔로우 (404-4)")
    fun unfollow_NotSubscribed() {
        val response =
            client
                .delete()
                .uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer $myToken")
                .exchange()
        expectResultCode(response, HttpStatus.NOT_FOUND, "404-4")
    }

    @Test
    @DisplayName("예외: creatorId 타입 불일치 (400)")
    fun unfollow_TypeMismatch() {
        client
            .delete()
            .uri("/api/subscriptions/follow/abc")
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isBadRequest()
    }

    @Test
    @DisplayName("예외: 멤버십 가입 중인 창작자 언팔로우 (409-4)")
    fun unfollow_MembershipActive() {
        client
            .post()
            .uri("/api/subscriptions/membership/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        val response =
            client
                .delete()
                .uri("/api/subscriptions/follow/{creatorId}", creatorId)
                .header("Authorization", "Bearer $myToken")
                .exchange()
        expectResultCode(response, HttpStatus.CONFLICT, "409-4")
    }

    @Test
    @DisplayName("성공: 멤버십 가입 (200)")
    fun joinMembership_Success() {
        client
            .post()
            .uri("/api/subscriptions/membership/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
    }

    @Test
    @DisplayName("성공: 팔로우 상태에서 멤버십 가입 시 자동 업그레이드 (200)")
    fun joinMembership_UpgradeFromFollow() {
        client
            .post()
            .uri("/api/subscriptions/follow/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        client
            .post()
            .uri("/api/subscriptions/membership/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        client
            .get()
            .uri("/api/subscriptions/status/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data.status")
            .isEqualTo("MEMBERSHIP")
    }

    @Test
    @DisplayName("예외: 인증되지 않은 사용자 - 멤버십 가입 (401)")
    fun joinMembership_Unauthorized() {
        client
            .post()
            .uri("/api/subscriptions/membership/{creatorId}", creatorId)
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }

    @Test
    @DisplayName("예외: 자기 자신 멤버십 가입 (400-3)")
    fun joinMembership_Self() {
        val response =
            client
                .post()
                .uri("/api/subscriptions/membership/{creatorId}", myId)
                .header("Authorization", "Bearer $myToken")
                .exchange()
        expectResultCode(response, HttpStatus.BAD_REQUEST, "400-3")
    }

    @Test
    @DisplayName("예외: 존재하지 않는 유저 멤버십 가입 (404-2)")
    fun joinMembership_NotFound() {
        val response =
            client
                .post()
                .uri("/api/subscriptions/membership/{id}", NON_EXISTENT_ID)
                .header("Authorization", "Bearer $myToken")
                .exchange()
        expectResultCode(response, HttpStatus.NOT_FOUND, "404-2")
    }

    @Test
    @DisplayName("예외: 이미 가입한 멤버십 가입 (409-5)")
    fun joinMembership_AlreadyMembership() {
        client
            .post()
            .uri("/api/subscriptions/membership/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        val response =
            client
                .post()
                .uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer $myToken")
                .exchange()
        expectResultCode(response, HttpStatus.CONFLICT, "409-5")
    }

    @Test
    @DisplayName("예외: creatorId 타입 불일치 (400)")
    fun joinMembership_TypeMismatch() {
        client
            .post()
            .uri("/api/subscriptions/membership/abc")
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isBadRequest()
    }

    @Test
    @DisplayName("성공: 멤버십 취소 후 팔로우로 강등 (200)")
    fun cancelMembership_Success() {
        client
            .post()
            .uri("/api/subscriptions/membership/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        client
            .delete()
            .uri("/api/subscriptions/membership/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        client
            .get()
            .uri("/api/subscriptions/status/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data.status")
            .isEqualTo("FOLLOW")
    }

    @Test
    @DisplayName("예외: 인증되지 않은 사용자 - 멤버십 취소 (401)")
    fun cancelMembership_Unauthorized() {
        client
            .delete()
            .uri("/api/subscriptions/membership/{creatorId}", creatorId)
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }

    @Test
    @DisplayName("예외: 가입하지 않은 멤버십 취소 (409-6)")
    fun cancelMembership_NotMembership() {
        client
            .post()
            .uri("/api/subscriptions/follow/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        val response =
            client
                .delete()
                .uri("/api/subscriptions/membership/{creatorId}", creatorId)
                .header("Authorization", "Bearer $myToken")
                .exchange()
        expectResultCode(response, HttpStatus.CONFLICT, "409-6")
    }

    @Test
    @DisplayName("예외: 존재하지 않는 유저 멤버십 취소 (404-4)")
    fun cancelMembership_NotFound() {
        val response =
            client
                .delete()
                .uri("/api/subscriptions/membership/{id}", NON_EXISTENT_ID)
                .header("Authorization", "Bearer $myToken")
                .exchange()
        expectResultCode(response, HttpStatus.NOT_FOUND, "404-4")
    }

    @Test
    @DisplayName("예외: creatorId 타입 불일치 (400)")
    fun cancelMembership_TypeMismatch() {
        client
            .delete()
            .uri("/api/subscriptions/membership/abc")
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isBadRequest()
    }

    @Test
    @DisplayName("성공: 구독 목록 조회 (기본 페이징)")
    fun getMySubscriptions_Success() {
        client
            .post()
            .uri("/api/subscriptions/follow/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
        client
            .get()
            .uri("/api/subscriptions")
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data.content")
            .isArray()
            .jsonPath("$.data.content.length()")
            .isEqualTo(1)
            .jsonPath("$.data.content[0].creatorId")
            .isEqualTo(creatorId)
    }

    @Test
    @DisplayName("성공: 구독 목록 조회 (page=0, size=1)")
    fun getMySubscriptions_Pagination_Small() {
        client
            .get()
            .uri("/api/subscriptions?page=0&size=1")
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
    }

    @Test
    @DisplayName("성공: 구독 목록 조회 (데이터 없는 빈 페이지)")
    fun getMySubscriptions_Pagination_Empty() {
        client
            .get()
            .uri("/api/subscriptions?page=99&size=10")
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
    }

    @Test
    @DisplayName("예외: 인증되지 않은 사용자 - 구독 목록 조회 (401)")
    fun getMySubscriptions_Unauthorized() {
        client
            .get()
            .uri("/api/subscriptions")
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }

    @Test
    @DisplayName("성공: 구독 수 조회")
    fun getMySubscriptionCount_Success() {
        client
            .post()
            .uri("/api/subscriptions/follow/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
        client
            .get()
            .uri("/api/subscriptions/count")
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data")
            .isEqualTo(1)
    }

    @Test
    @DisplayName("예외: 인증되지 않은 사용자 - 구독 수 조회 (401)")
    fun getMySubscriptionCount_Unauthorized() {
        client
            .get()
            .uri("/api/subscriptions/count")
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }

    @Test
    @DisplayName("성공: 상태 조회 (FOLLOW)")
    fun getStatus_Follow() {
        client
            .post()
            .uri("/api/subscriptions/follow/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
        client
            .get()
            .uri("/api/subscriptions/status/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data.status")
            .isEqualTo("FOLLOW")
    }

    @Test
    @DisplayName("성공: 상태 조회 (MEMBERSHIP)")
    fun getStatus_Membership() {
        client
            .post()
            .uri("/api/subscriptions/membership/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
        client
            .get()
            .uri("/api/subscriptions/status/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data.status")
            .isEqualTo("MEMBERSHIP")
    }

    @Test
    @DisplayName("성공: 상태 조회 (NONE)")
    fun getStatus_None() {
        client
            .get()
            .uri("/api/subscriptions/status/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data.status")
            .isEqualTo("NONE")
    }

    @Test
    @DisplayName("예외: 인증되지 않은 사용자 - 상태 조회 (401)")
    fun getStatus_Unauthorized() {
        client
            .get()
            .uri("/api/subscriptions/status/{creatorId}", creatorId)
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }

    @Test
    @DisplayName("성공: 존재하지 않는 유저 조회 시에도 NONE 반환 (200)")
    fun getStatus_NotFound() {
        client
            .get()
            .uri("/api/subscriptions/status/999")
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data.status")
            .isEqualTo("NONE")
    }

    @Test
    @DisplayName("예외: creatorId 타입 불일치 (400)")
    fun getStatus_TypeMismatch() {
        client
            .get()
            .uri("/api/subscriptions/status/abc")
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isBadRequest()
    }

    @Test
    @DisplayName("성공: 팔로워 수 없는 유저 조회 (0 반환)")
    fun getFollowerCount_Zero() {
        client
            .get()
            .uri("/api/subscriptions/followers/count")
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data")
            .isEqualTo(0)
    }

    @Test
    @DisplayName("성공: 팔로워 있는 유저 조회 (1 반환)")
    fun getFollowerCount_Success() {
        val follower = createUserAndLogin(client, uniqueEmail(), "123456", uniqueNickname())
        client
            .post()
            .uri("/api/subscriptions/follow/{creatorId}", myId)
            .header("Authorization", "Bearer ${follower.accessToken}")
            .exchange()
            .expectStatus()
            .isOk()

        client
            .get()
            .uri("/api/subscriptions/followers/count")
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data")
            .isEqualTo(1)
    }

    @Test
    @DisplayName("예외: 인증되지 않은 사용자 - 팔로워 수 조회 (401)")
    fun getFollowerCount_Anonymous() {
        client
            .get()
            .uri("/api/subscriptions/followers/count")
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }

    @Test
    @DisplayName("시나리오: 팔로우 -> 멤버십 업그레이드 -> 다운그레이드 -> 언팔로우 흐름")
    fun subscriptionLifecycleScenario() {
        // 1. 상태 조회 (초기 NONE)
        client
            .get()
            .uri("/api/subscriptions/status/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data.status")
            .isEqualTo("NONE")

        // 2. 팔로우
        client
            .post()
            .uri("/api/subscriptions/follow/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        // 3. 상태 조회 (FOLLOW)
        client
            .get()
            .uri("/api/subscriptions/status/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data.status")
            .isEqualTo("FOLLOW")

        // 4. 멤버십 업그레이드
        client
            .post()
            .uri("/api/subscriptions/membership/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        // 5. 상태 조회 (MEMBERSHIP)
        client
            .get()
            .uri("/api/subscriptions/status/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data.status")
            .isEqualTo("MEMBERSHIP")

        // 6. 멤버십 취소 (다운그레이드)
        client
            .delete()
            .uri("/api/subscriptions/membership/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        // 7. 상태 조회 (FOLLOW)
        client
            .get()
            .uri("/api/subscriptions/status/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data.status")
            .isEqualTo("FOLLOW")

        // 8. 언팔로우
        client
            .delete()
            .uri("/api/subscriptions/follow/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        // 9. 상태 조회 (NONE)
        client
            .get()
            .uri("/api/subscriptions/status/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data.status")
            .isEqualTo("NONE")

        // 10. 재팔로우 (NONE -> FOLLOW)
        client
            .post()
            .uri("/api/subscriptions/follow/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
        client
            .get()
            .uri("/api/subscriptions/status/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data.status")
            .isEqualTo("FOLLOW")

        // 11. 멤버십 가입 (FOLLOW -> MEMBERSHIP)
        client
            .post()
            .uri("/api/subscriptions/membership/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        // 12. 멤버십 다시 취소 (MEMBERSHIP -> FOLLOW)
        client
            .delete()
            .uri("/api/subscriptions/membership/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()

        // 13. 멤버십 재가입 (FOLLOW -> MEMBERSHIP)
        client
            .post()
            .uri("/api/subscriptions/membership/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
        client
            .get()
            .uri("/api/subscriptions/status/{creatorId}", creatorId)
            .header("Authorization", "Bearer $myToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data.status")
            .isEqualTo("MEMBERSHIP")
    }

    companion object {
        private const val NON_EXISTENT_ID = 999L
    }
}
