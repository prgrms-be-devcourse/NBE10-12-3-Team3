package com.scommit.global.e2e

import com.scommit.domain.user.user.dto.LoginRequest
import com.scommit.domain.user.user.dto.LoginResponse
import com.scommit.domain.user.user.dto.SignupRequest
import com.scommit.domain.user.user.dto.SignupResponse
import org.assertj.core.api.Assertions.assertThat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody
import java.util.UUID

/**
 * 컨트롤러를 가리지 않고 쓰이는 E2E 공통 헬퍼 — 클라이언트 생성, 회원가입/로그인, Bearer 헤더,
 * 공통 응답 어서션.
 *
 * 인증이 필요한 E2E는 결국 회원가입/로그인 API를 거쳐야 하므로, `global` 패키지에 있으면서도
 * `domain.user` 의 DTO([SignupRequest], [LoginRequest], [LoginResponse] 등)를
 * 참조한다. 이 프로젝트에서 인증은 user 도메인 소관이라 불가피하며 의도된 의존이다.
 * 이 의존을 끊기 위한 인터페이스/제네릭 추상화는 하지 않는다.
 *
 * 사용법은 `docs/e2e-test-convention.md` 참고.
 */
object E2ETestSupport {
    /**
     * `@LocalServerPort` 로 받은 포트에 붙는 실제 HTTP 클라이언트를 만든다.
     * Mock 서버 바인딩이 아니라 실제 서버로 요청이 나간다.
     */
    fun client(port: Int): RestTestClient =
        RestTestClient
            .bindToServer()
            .baseUrl("http://localhost:$port")
            .build()

    /**
     * 테스트 간 데이터 격리를 이메일/닉네임 유일성으로 해결하기 위한 값 생성기.
     * (`@DirtiesContext`/`@Transactional` 을 쓰지 않는다는 결정에 따른 것)
     */
    fun uniqueEmail(): String = "e2e-${UUID.randomUUID()}@test.com"

    fun uniqueNickname(): String =
        "e2e" +
            UUID
                .randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10)

    fun bearer(accessToken: String): String = "Bearer $accessToken"

    /**
     * 회원가입 요청을 보내고 응답을 그대로 돌려준다(상태 코드 검증 없음).
     * 검증 실패 케이스를 쓰는 쪽에서 사용한다.
     */
    fun signUpRequest(
        client: RestTestClient,
        request: SignupRequest,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/users/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()

    /**
     * 회원가입 성공을 전제로 응답 본문을 돌려준다.
     */
    fun signUp(
        client: RestTestClient,
        email: String,
        password: String,
        nickname: String,
    ): ApiResponse<SignupResponse> =
        checkNotNull(
            signUpRequest(client, SignupRequest(email, password, nickname))
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<SignupResponse>>()
                .returnResult()
                .responseBody,
        )

    /**
     * 로그인 요청을 보내고 응답을 그대로 돌려준다(상태 코드 검증 없음).
     */
    fun loginRequest(
        client: RestTestClient,
        email: String,
        password: String?,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/users/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(LoginRequest(email, password))
            .exchange()

    /**
     * 로그인 성공을 전제로 응답 본문을 돌려준다.
     */
    fun login(
        client: RestTestClient,
        email: String,
        password: String,
    ): ApiResponse<LoginResponse> =
        checkNotNull(
            loginRequest(client, email, password)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<LoginResponse>>()
                .returnResult()
                .responseBody,
        )

    /**
     * 회원가입 API로 새 계정을 만들고 바로 로그인해서 로그인 응답(토큰 + user.id)을 돌려준다.
     * 유저 id까지 필요한 테스트가 쓴다.
     */
    fun createUserAndLogin(
        client: RestTestClient,
        email: String,
        password: String,
        nickname: String,
    ): LoginResponse {
        signUp(client, email, password, nickname)
        return login(client, email, password).data
    }

    /**
     * 액세스 토큰만 필요한 테스트용 축약형.
     */
    fun createUserAndGetAccessToken(
        client: RestTestClient,
        email: String,
        password: String,
        nickname: String,
    ): String = createUserAndLogin(client, email, password, nickname).accessToken

    /**
     * 상태 코드와 `resultCode` 만 확인하는 공통 어서션.
     * `msg`/`data` 까지 봐야 하는 케이스는 이 헬퍼를 쓰지 말고 직접 풀어 쓴다.
     */
    fun expectResultCode(
        response: RestTestClient.ResponseSpec,
        status: HttpStatus,
        resultCode: String,
    ) {
        response
            .expectStatus()
            .isEqualTo(status)
            .expectBody(ApiResponse.VOID_BODY)
            .value { body ->
                checkNotNull(body)
                assertThat(body.resultCode).isEqualTo(resultCode)
            }
    }
}
