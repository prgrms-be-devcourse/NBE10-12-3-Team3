package com.scommit.domain.coupon.couponpolicy.controller

// 결정사항 (docs/e2e-test-convention.md 를 그대로 따른다)
// - @SpringBootTest(RANDOM_PORT) + RestTestClient. Mock / MockMvc / @MockitoBean 일절 사용 금지.
// - DB: 이 클래스 전용 H2 in-memory(coupondb).
// - 픽스처: 관리자 계정(UserRole.ADMIN)은 회원가입 API로 만들 수 없어서 CouponE2EFixtures로 심고,
//   그 외 쿠폰 이벤트/발급 내역은 전부 API로 만든다(컨벤션 5장 우선순위 1).
// - @DirtiesContext / @Transactional 미사용. 테스트끼리는 각자 만든 이벤트로 격리한다.
// - 쿠폰 도메인은 CouponPolicyController/UserCouponController 두 컨트롤러로 나뉘어 있지만,
//   Post/PostMedia처럼 같은 도메인이라 이 파일 하나에서 함께 검증한다.

import com.scommit.domain.coupon.couponpolicy.dto.CouponPolicyCreateRequest
import com.scommit.domain.coupon.couponpolicy.dto.CouponPolicyResponse
import com.scommit.domain.coupon.couponpolicy.entity.DiscountType
import com.scommit.domain.coupon.couponpolicy.entity.ExpiryType
import com.scommit.domain.coupon.usercoupon.dto.UserCouponResponse
import com.scommit.global.e2e.ApiResponse
import com.scommit.global.e2e.E2ETestSupport
import com.scommit.global.e2e.E2ETestSupport.bearer
import com.scommit.global.e2e.E2ETestSupport.createUserAndGetAccessToken
import com.scommit.global.e2e.E2ETestSupport.expectResultCode
import com.scommit.global.e2e.E2ETestSupport.uniqueEmail
import com.scommit.global.e2e.E2ETestSupport.uniqueNickname
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody
import java.time.LocalDateTime

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=jdbc:h2:mem:coupondb;MODE=MySQL;DB_CLOSE_DELAY=-1"],
)
@ActiveProfiles("test")
@Tag("e2e")
@Import(CouponE2EFixtures::class)
class CouponControllerE2ETest {
    @LocalServerPort
    private var port: Int = 0

    private lateinit var client: RestTestClient

    @Autowired
    private lateinit var adminFixture: CouponE2EFixtures.AdminFixture

    @BeforeEach
    fun setUpClient() {
        client = E2ETestSupport.client(port)
    }

    // ---------- 공통 헬퍼 ----------

    private fun getAdminAccessToken(): String =
        E2ETestSupport.login(client, adminFixture.email, adminFixture.password).data.accessToken

    private fun createPolicyRequest(
        totalQuantity: Int = 100,
        startAt: LocalDateTime = LocalDateTime.now().minusDays(1),
        endAt: LocalDateTime = LocalDateTime.now().plusDays(7),
    ) = CouponPolicyCreateRequest(
        title = "여름 할인 이벤트",
        description = "선착순 $totalQuantity 명",
        discountType = DiscountType.PERCENT,
        discountValue = 10,
        totalQuantity = totalQuantity,
        startAt = startAt,
        endAt = endAt,
        expiryType = ExpiryType.RELATIVE,
        validDays = 7,
        fixedExpiredAt = null,
    )

    /** 쿠폰 발급/조회의 선행 데이터인 쿠폰 이벤트를 실제 API로 만든다. */
    private fun createPolicy(
        adminAccessToken: String,
        request: CouponPolicyCreateRequest = createPolicyRequest(),
    ): Long =
        checkNotNull(
            checkNotNull(
                client
                    .post()
                    .uri("/api/admin/coupon-policies")
                    .header("Authorization", bearer(adminAccessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus()
                    .isCreated()
                    .expectBody<ApiResponse<CouponPolicyResponse>>()
                    .returnResult()
                    .responseBody,
            ).data.id,
        )

    private fun issueCouponRequest(
        accessToken: String,
        policyId: Long,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/coupon-policies/$policyId/issue")
            .header("Authorization", bearer(accessToken))
            .exchange()

    @Nested
    @DisplayName("쿠폰 이벤트 생성 (POST /api/admin/coupon-policies)")
    inner class CreateCouponPolicy {
        @Test
        @DisplayName("성공: 관리자가 쿠폰 이벤트를 생성한다")
        fun createSuccess() {
            val adminAccessToken = getAdminAccessToken()

            val body =
                checkNotNull(
                    client
                        .post()
                        .uri("/api/admin/coupon-policies")
                        .header("Authorization", bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(createPolicyRequest())
                        .exchange()
                        .expectStatus()
                        .isCreated()
                        .expectBody<ApiResponse<CouponPolicyResponse>>()
                        .returnResult()
                        .responseBody,
                )

            assertThat(body.data.title).isEqualTo("여름 할인 이벤트")
            assertThat(body.data.totalQuantity).isEqualTo(100)
            assertThat(body.data.issuedQuantity).isEqualTo(0)
        }

        @Test
        @DisplayName("실패: 일반 유저는 쿠폰 이벤트를 생성할 수 없다 (403)")
        fun createFailNotAdmin() {
            val userAccessToken = createUserAndGetAccessToken(client, uniqueEmail(), "password123", uniqueNickname())

            val response =
                client
                    .post()
                    .uri("/api/admin/coupon-policies")
                    .header("Authorization", bearer(userAccessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createPolicyRequest())
                    .exchange()

            expectResultCode(response, HttpStatus.FORBIDDEN, "403-1")
        }

        @Test
        @DisplayName("실패: RELATIVE인데 validDays가 없으면 400을 반환한다")
        fun createFailInvalidExpiry() {
            val adminAccessToken = getAdminAccessToken()
            val invalidRequest = createPolicyRequest().copy(validDays = null)

            val response =
                client
                    .post()
                    .uri("/api/admin/coupon-policies")
                    .header("Authorization", bearer(adminAccessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(invalidRequest)
                    .exchange()

            expectResultCode(response, HttpStatus.BAD_REQUEST, "400-1")
        }
    }

    @Nested
    @DisplayName("진행 중인 쿠폰 이벤트 조회 (GET /api/coupon-policies/active)")
    inner class GetActiveCouponPolicies {
        @Test
        @DisplayName("성공: 인증 없이도 진행 중인 이벤트 목록을 조회할 수 있다")
        fun getActiveSuccess() {
            val adminAccessToken = getAdminAccessToken()
            val policyId = createPolicy(adminAccessToken)

            val body =
                checkNotNull(
                    client
                        .get()
                        .uri("/api/coupon-policies/active")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody<ApiResponse<List<CouponPolicyResponse>>>()
                        .returnResult()
                        .responseBody,
                )

            assertThat(body.data.map { it.id }).contains(policyId)
        }

        @Test
        @DisplayName("성공: 아직 시작하지 않은 이벤트는 목록에서 제외된다")
        fun getActiveExcludesNotStarted() {
            val adminAccessToken = getAdminAccessToken()
            val futureRequest =
                createPolicyRequest(
                    startAt = LocalDateTime.now().plusDays(1),
                    endAt = LocalDateTime.now().plusDays(10),
                )
            val futurePolicyId = createPolicy(adminAccessToken, futureRequest)

            val body =
                checkNotNull(
                    client
                        .get()
                        .uri("/api/coupon-policies/active")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody<ApiResponse<List<CouponPolicyResponse>>>()
                        .returnResult()
                        .responseBody,
                )

            assertThat(body.data.map { it.id }).doesNotContain(futurePolicyId)
        }

        @Test
        @DisplayName("성공: 이미 종료된 이벤트는 목록에서 제외된다")
        fun getActiveExcludesExpired() {
            val adminAccessToken = getAdminAccessToken()
            val expiredRequest =
                createPolicyRequest(
                    startAt = LocalDateTime.now().minusDays(10),
                    endAt = LocalDateTime.now().minusDays(1),
                )
            val expiredPolicyId = createPolicy(adminAccessToken, expiredRequest)

            val body =
                checkNotNull(
                    client
                        .get()
                        .uri("/api/coupon-policies/active")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody<ApiResponse<List<CouponPolicyResponse>>>()
                        .returnResult()
                        .responseBody,
                )

            assertThat(body.data.map { it.id }).doesNotContain(expiredPolicyId)
        }
    }

    @Nested
    @DisplayName("쿠폰 발급 (POST /api/coupon-policies/{id}/issue)")
    inner class IssueCoupon {
        @Test
        @DisplayName("성공: 로그인한 유저가 쿠폰을 발급받는다")
        fun issueSuccess() {
            val adminAccessToken = getAdminAccessToken()
            val policyId = createPolicy(adminAccessToken)
            val userAccessToken = createUserAndGetAccessToken(client, uniqueEmail(), "password123", uniqueNickname())

            val body =
                checkNotNull(
                    issueCouponRequest(userAccessToken, policyId)
                        .expectStatus()
                        .isOk()
                        .expectBody<ApiResponse<UserCouponResponse>>()
                        .returnResult()
                        .responseBody,
                )

            assertThat(body.data.couponPolicyId).isEqualTo(policyId)
            assertThat(body.data.usedAt).isNull()
        }

        @Test
        @DisplayName("실패: 존재하지 않는 쿠폰 이벤트면 404를 반환한다")
        fun issueFailPolicyNotFound() {
            val userAccessToken = createUserAndGetAccessToken(client, uniqueEmail(), "password123", uniqueNickname())

            val response = issueCouponRequest(userAccessToken, Long.MAX_VALUE)

            expectResultCode(response, HttpStatus.NOT_FOUND, "404-10")
        }

        @Test
        @DisplayName("실패: 이미 발급받은 유저가 다시 요청하면 409를 반환한다")
        fun issueFailAlreadyIssued() {
            val adminAccessToken = getAdminAccessToken()
            val policyId = createPolicy(adminAccessToken)
            val userAccessToken = createUserAndGetAccessToken(client, uniqueEmail(), "password123", uniqueNickname())
            issueCouponRequest(userAccessToken, policyId).expectStatus().isOk()

            val response = issueCouponRequest(userAccessToken, policyId)

            expectResultCode(response, HttpStatus.CONFLICT, "409-10")
        }

        @Test
        @DisplayName("실패: 발급 기간이 아닌 이벤트면 409를 반환한다")
        fun issueFailNotActive() {
            val adminAccessToken = getAdminAccessToken()
            val futureRequest =
                createPolicyRequest(
                    startAt = LocalDateTime.now().plusDays(1),
                    endAt = LocalDateTime.now().plusDays(10),
                )
            val policyId = createPolicy(adminAccessToken, futureRequest)
            val userAccessToken = createUserAndGetAccessToken(client, uniqueEmail(), "password123", uniqueNickname())

            val response = issueCouponRequest(userAccessToken, policyId)

            expectResultCode(response, HttpStatus.CONFLICT, "409-9")
        }

        @Test
        @DisplayName("실패: 수량이 소진된 이벤트면 409를 반환한다")
        fun issueFailSoldOut() {
            val adminAccessToken = getAdminAccessToken()
            val policyId = createPolicy(adminAccessToken, createPolicyRequest(totalQuantity = 1))
            val firstUserAccessToken =
                createUserAndGetAccessToken(client, uniqueEmail(), "password123", uniqueNickname())
            issueCouponRequest(firstUserAccessToken, policyId).expectStatus().isOk()
            val secondUserAccessToken =
                createUserAndGetAccessToken(client, uniqueEmail(), "password123", uniqueNickname())

            val response = issueCouponRequest(secondUserAccessToken, policyId)

            expectResultCode(response, HttpStatus.CONFLICT, "409-11")
        }
    }

    @Nested
    @DisplayName("내 쿠폰 목록 조회 (GET /api/coupons/me)")
    inner class GetMyCoupons {
        @Test
        @DisplayName("성공: 발급받은 쿠폰이 있으면 목록에 포함된다")
        fun getMyCouponsSuccess() {
            val adminAccessToken = getAdminAccessToken()
            val policyId = createPolicy(adminAccessToken)
            val userAccessToken = createUserAndGetAccessToken(client, uniqueEmail(), "password123", uniqueNickname())
            issueCouponRequest(userAccessToken, policyId).expectStatus().isOk()

            val body =
                checkNotNull(
                    client
                        .get()
                        .uri("/api/coupons/me")
                        .header("Authorization", bearer(userAccessToken))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody<ApiResponse<List<UserCouponResponse>>>()
                        .returnResult()
                        .responseBody,
                )

            assertThat(body.data.map { it.couponPolicyId }).contains(policyId)
        }

        @Test
        @DisplayName("성공: 발급받은 쿠폰이 없으면 빈 목록을 반환한다")
        fun getMyCouponsEmpty() {
            val userAccessToken = createUserAndGetAccessToken(client, uniqueEmail(), "password123", uniqueNickname())

            val body =
                checkNotNull(
                    client
                        .get()
                        .uri("/api/coupons/me")
                        .header("Authorization", bearer(userAccessToken))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody<ApiResponse<List<UserCouponResponse>>>()
                        .returnResult()
                        .responseBody,
                )

            assertThat(body.data).isEmpty()
        }
    }
}
