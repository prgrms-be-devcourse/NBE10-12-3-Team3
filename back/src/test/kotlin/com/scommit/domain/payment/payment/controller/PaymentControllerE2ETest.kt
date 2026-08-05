package com.scommit.domain.payment.payment.controller

import com.scommit.global.e2e.E2ETestSupport
import com.scommit.global.e2e.E2ETestSupport.createUserAndLogin
import com.scommit.global.e2e.E2ETestSupport.expectResultCode
import com.scommit.global.e2e.E2ETestSupport.uniqueEmail
import com.scommit.global.e2e.E2ETestSupport.uniqueNickname
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.web.client.RestClient

/**
 * 결제 컨트롤러 E2E.
 *
 * 인증·소유자 검증 등 HTTP 계약을 확인한다.
 * 승인 성공 경로는 토스로 실제 요청이 나가면 안 되므로 [TossStubConfig]로 목 서버를 물려서 검증한다.
 */
@ActiveProfiles("test")
@Import(PaymentControllerE2ETest.TossStubConfig::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:payment_e2edb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class PaymentControllerE2ETest {
    /**
     * 토스 승인 API를 목으로 대체한다.
     *
     * PaymentService가 ObjectProvider로 RestClient.Builder를 가져가므로,
     * 여기서 목 서버에 바인딩한 빌더를 빈으로 등록하면 서비스가 그 빌더로 클라이언트를 만든다.
     * 덕분에 외부 네트워크 없이 승인 성공 경로까지 E2E로 검증할 수 있다.
     */
    @TestConfiguration
    class TossStubConfig {
        @Bean
        fun tossRestClientBuilder(): RestClient.Builder {
            val builder = RestClient.builder()
            tossServer = MockRestServiceServer.bindTo(builder).build()
            return builder
        }
    }

    @LocalServerPort
    private var port: Int = 0

    private lateinit var client: RestTestClient
    private lateinit var buyerToken: String
    private var buyerId: Long = 0
    private var creatorId: Long = 0

    @BeforeEach
    fun setUp() {
        // 컨텍스트가 캐시되므로 이전 테스트가 남긴 기대치를 매번 비운다
        tossServer.reset()
        client = E2ETestSupport.client(port)

        val buyer = createUserAndLogin(client, uniqueEmail(), "123456", uniqueNickname())
        buyerToken = buyer.accessToken
        buyerId = buyer.user.id

        val creator = createUserAndLogin(client, uniqueEmail(), "123456", uniqueNickname())
        creatorId = creator.user.id
    }

    private fun ready(
        token: String,
        targetCreatorId: Long,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/payments/toss/ready")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"targetCreatorId":$targetCreatorId}""")
            .exchange()

    private fun extractOrderId(response: RestTestClient.ResponseSpec): String =
        response
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .let { String(it.responseBody!!) }
            .substringAfter("\"orderId\":\"")
            .substringBefore("\"")

    @Test
    @DisplayName("성공: 결제 준비 시 서버가 확정한 금액과 주문번호를 반환한다 (200)")
    fun readyPayment_success() {
        ready(buyerToken, creatorId)
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.resultCode")
            .isEqualTo("200-1")
            .jsonPath("$.data.amount")
            .isEqualTo(9900)
            .jsonPath("$.data.orderId")
            .exists()
            .jsonPath("$.data.orderName")
            .exists()
    }

    @Test
    @DisplayName("예외: 비로그인 사용자는 결제를 준비할 수 없다 (401)")
    fun readyPayment_unauthenticated_returns401() {
        expectResultCode(
            client
                .post()
                .uri("/api/payments/toss/ready")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"targetCreatorId":$creatorId}""")
                .exchange(),
            HttpStatus.UNAUTHORIZED,
            "401-1",
        )
    }

    @Test
    @DisplayName("예외: 자기 자신에게는 결제를 준비할 수 없다 (400)")
    fun readyPayment_selfSubscription_returns400() {
        expectResultCode(ready(buyerToken, buyerId), HttpStatus.BAD_REQUEST, "400-3")
    }

    @Test
    @DisplayName("예외: 존재하지 않는 창작자에게는 결제를 준비할 수 없다 (404)")
    fun readyPayment_creatorNotFound_returns404() {
        expectResultCode(ready(buyerToken, 999_999L), HttpStatus.NOT_FOUND, "404-2")
    }

    @Test
    @DisplayName("예외: 비로그인 사용자는 결제를 승인할 수 없다 (401)")
    fun confirmPayment_unauthenticated_returns401() {
        expectResultCode(
            client
                .post()
                .uri("/api/payments/toss/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"paymentKey":"pk","orderId":"order_notexist"}""")
                .exchange(),
            HttpStatus.UNAUTHORIZED,
            "401-1",
        )
    }

    @Test
    @DisplayName("예외: 존재하지 않는 주문번호는 승인할 수 없다 (404)")
    fun confirmPayment_orderNotFound_returns404() {
        expectResultCode(
            client
                .post()
                .uri("/api/payments/toss/confirm")
                .header("Authorization", "Bearer $buyerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"paymentKey":"pk","orderId":"order_notexist"}""")
                .exchange(),
            HttpStatus.NOT_FOUND,
            "404-11",
        )
    }

    @Test
    @DisplayName("예외: 타인의 결제 건은 승인할 수 없다 (403)")
    fun confirmPayment_otherUsersOrder_returns403() {
        // 구매자가 만든 주문을 제3자가 승인 시도한다
        val orderId = extractOrderId(ready(buyerToken, creatorId))
        val stranger = createUserAndLogin(client, uniqueEmail(), "123456", uniqueNickname())

        expectResultCode(
            client
                .post()
                .uri("/api/payments/toss/confirm")
                .header("Authorization", "Bearer ${stranger.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"paymentKey":"pk","orderId":"$orderId"}""")
                .exchange(),
            HttpStatus.FORBIDDEN,
            "403-2",
        )
    }

    @Test
    @DisplayName("성공: 결제 내역을 조회한다 (200)")
    fun getMyPaymentHistory_success() {
        ready(buyerToken, creatorId).expectStatus().isOk()

        client
            .get()
            .uri("/api/payments/history")
            .header("Authorization", "Bearer $buyerToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.resultCode")
            .isEqualTo("200-1")
            .jsonPath("$.data[0].status")
            .isEqualTo("READY")
    }

    @Test
    @DisplayName("예외: 비로그인 사용자는 결제 내역을 조회할 수 없다 (401)")
    fun getMyPaymentHistory_unauthenticated_returns401() {
        expectResultCode(
            client.get().uri("/api/payments/history").exchange(),
            HttpStatus.UNAUTHORIZED,
            "401-1",
        )
    }

    @Test
    @DisplayName("성공: 승인이 완료되면 결제가 DONE으로 기록된다 (200)")
    fun confirmPayment_success() {
        val orderId = extractOrderId(ready(buyerToken, creatorId))

        tossServer
            .expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
            .andRespond(
                withSuccess(
                    """{"paymentKey":"pk","orderId":"$orderId","status":"DONE","totalAmount":9900}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        client
            .post()
            .uri("/api/payments/toss/confirm")
            .header("Authorization", "Bearer $buyerToken")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"paymentKey":"pk","orderId":"$orderId"}""")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.resultCode")
            .isEqualTo("200-1")

        // 결제 내역에 DONE으로 남아야 한다
        client
            .get()
            .uri("/api/payments/history")
            .header("Authorization", "Bearer $buyerToken")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data[0].status")
            .isEqualTo("DONE")
    }

    companion object {
        private lateinit var tossServer: MockRestServiceServer
    }
}
